package net.vulkanmod.render.chunk.build.frapi.render;

import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.vulkanmod.render.chunk.build.BlockRenderer;
import net.vulkanmod.render.chunk.build.frapi.helper.ColorHelper;
import net.vulkanmod.render.chunk.build.frapi.mesh.MutableQuadViewImpl;
import net.vulkanmod.render.chunk.build.light.LightPipeline;
import net.vulkanmod.render.chunk.build.light.data.QuadLightData;
import net.vulkanmod.render.chunk.build.thread.BuilderResources;
import net.vulkanmod.render.vertex.TerrainBufferBuilder;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.util.ColorUtil;
import org.joml.Vector3f;

/**
 * Terrain FRAPI context. Quads arrive through {@link FabricBakedModel#emitBlockQuads} and are
 * written out through the existing {@link BlockRenderer#putQuadData} path, so the output format
 * stays identical to the vanilla-model path.
 */
public class TerrainRenderContext extends AbstractBlockRenderContext {
	private final BuilderResources resources;

	private Vector3f origin;

	public TerrainRenderContext(BuilderResources resources) {
		this.resources = resources;
		this.random = RandomSource.createNewThreadLocalInstance();
		this.setupLightPipelines(resources.flatLightPipeline, resources.smoothLightPipeline);
	}

	/** Called once per chunk build, after {@link BuilderResources#update}. */
	public void prepareForRegion() {
		this.prepareForWorld(this.resources.region, true);
	}

	/** Drops references to the finished region so it can be collected. */
	public void release() {
		this.blockState = null;
		this.blockPos = null;
		this.origin = null;
		this.renderRegion = null;
	}

	/**
	 * Renders one block through the FRAPI pipeline. {@code pos} must already include the
	 * block state's model offset, matching {@link BlockRenderer#renderBatched}.
	 */
	public void tessellateBlock(BakedModel model, BlockState blockState, BlockPos blockPos, Vector3f pos,
	                            long seed, ModelData modelData, RenderType renderLayer) {
		try {
			this.origin = pos;
			this.seed = seed;

			this.prepareForBlock(blockState, blockPos, model.useAmbientOcclusion(), modelData, renderLayer);

			((FabricBakedModel) model).emitBlockQuads(this.renderRegion, blockState, blockPos, this.randomSupplier, this);
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.forThrowable(throwable, "Tessellating block in world - VulkanMod Renderer");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Block being tessellated");
			CrashReportCategory.populateBlockDetails(crashReportCategory, this.renderRegion, blockPos, blockState);
			throw new ReportedException(crashReport);
		}
	}

	@Override
	protected void endRenderQuad(MutableQuadViewImpl quad) {
		final RenderMaterial mat = quad.material();
		final int colorIndex = mat.disableColorIndex() ? -1 : quad.colorIndex();
		final TriState aoMode = mat.ambientOcclusion();
		final boolean ao = this.useAO && (aoMode == TriState.TRUE || (aoMode == TriState.DEFAULT && this.defaultAO));
		final boolean emissive = mat.emissive();

		LightPipeline lightPipeline = ao ? this.smoothLightPipeline : this.flatLightPipeline;

		colorizeQuad(quad, colorIndex);
		lightQuad(quad, lightPipeline, emissive);

		putQuad(quad, this.bufferFor(mat.blendMode()));
	}

	/**
	 * Fills {@link #quadLightData} for {@link BlockRenderer#putQuadData}, which applies the
	 * brightness itself. Unlike {@link #shadeQuad}, vertex colors are left untouched here so the
	 * brightness is not applied twice.
	 */
	private void lightQuad(MutableQuadViewImpl quad, LightPipeline lightPipeline, boolean emissive) {
		QuadLightData data = this.quadLightData;

		lightPipeline.calculate(quad, this.blockPos, data, quad.cullFace(), quad.lightFace(), quad.hasShade());

		if (emissive) {
			for (int i = 0; i < 4; i++) {
				data.lm[i] = LightTexture.FULL_BRIGHT;
			}
		} else {
			for (int i = 0; i < 4; i++) {
				data.lm[i] = ColorHelper.maxBrightness(quad.lightmap(i), data.lm[i]);
			}
		}
	}

	/**
	 * A single model may emit quads into several blend modes. Resolve the target buffer per quad
	 * rather than per model, since {@code emitBlockQuads} is a single pass over the whole model.
	 */
	private TerrainBufferBuilder bufferFor(BlendMode blendMode) {
		RenderType renderType = blendMode == BlendMode.DEFAULT ? this.renderLayer : blendMode.blockRenderLayer;

		TerrainRenderType terrainRenderType = TerrainRenderType.get(renderType);
		if (terrainRenderType == null) {
			terrainRenderType = TerrainRenderType.CUTOUT_MIPPED;
		}

		return this.resources.builderPack.builder(BlockRenderer.compactRenderType(terrainRenderType));
	}

	private void putQuad(MutableQuadViewImpl quad, TerrainBufferBuilder bufferBuilder) {
		// The emitter stores colors as ARGB, putQuadData unpacks them through ColorUtil.RGBA.
		for (int i = 0; i < 4; i++) {
			quad.color(i, ColorUtil.RGBA.fromArgb32(quad.color(i)));
		}

		bufferBuilder.setBlockAttributes(this.blockState);
		BlockRenderer.putQuadData(bufferBuilder, this.origin, quad, this.quadLightData, 1.0f, 1.0f, 1.0f);
	}
}
