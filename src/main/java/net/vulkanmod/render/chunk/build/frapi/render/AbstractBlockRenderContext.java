package net.vulkanmod.render.chunk.build.frapi.render;

import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ModelHelper;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.model.data.ModelData;
import net.vulkanmod.render.chunk.build.frapi.VulkanModRenderer;
import net.vulkanmod.render.chunk.build.frapi.helper.ColorHelper;
import net.vulkanmod.render.chunk.build.frapi.mesh.EncodingFormat;
import net.vulkanmod.render.chunk.build.frapi.mesh.MutableQuadViewImpl;
import net.vulkanmod.render.chunk.build.light.LightPipeline;
import net.vulkanmod.render.chunk.build.light.data.QuadLightData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public abstract class AbstractBlockRenderContext extends AbstractRenderContext {
	protected static final RenderMaterial STANDARD_MATERIAL = VulkanModRenderer.INSTANCE.materialFinder().find();
	protected static final RenderMaterial NO_AO_MATERIAL = VulkanModRenderer.INSTANCE.materialFinder().ambientOcclusion(TriState.FALSE).find();

	private final BlockColors blockColors = Minecraft.getInstance().getBlockColors();

	private final MutableQuadViewImpl editorQuad = new MutableQuadViewImpl() {
		{
			data = new int[EncodingFormat.TOTAL_STRIDE];
			clear();
		}

		@Override
		public void emitDirectly() {
			renderQuad(this);
		}
	};

	protected BlockState blockState;
	protected BlockPos blockPos;
	protected BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();

	protected BlockAndTintGetter renderRegion;

	/** Forge model context, handed to models through the FRAPI side channel. */
	protected ModelData modelData = ModelData.EMPTY;
	protected RenderType renderLayer;

	protected final Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey> occlusionCache = new Object2ByteLinkedOpenHashMap<>(2048, 0.25F) {
		protected void rehash(int i) {
		}
	};

	protected final QuadLightData quadLightData = new QuadLightData();
	protected LightPipeline smoothLightPipeline;
	protected LightPipeline flatLightPipeline;

	protected boolean useAO;
	protected boolean defaultAO;

	protected long seed;
	protected RandomSource random;
	public final Supplier<RandomSource> randomSupplier = () -> {
		random.setSeed(this.seed);
		return random;
	};

	protected boolean enableCulling = true;
	protected int cullCompletionFlags;
	protected int cullResultFlags;

	protected AbstractBlockRenderContext() {
		this.occlusionCache.defaultReturnValue((byte) 127);
	}

	protected void setupLightPipelines(LightPipeline flatLightPipeline, LightPipeline smoothLightPipeline) {
		this.flatLightPipeline = flatLightPipeline;
		this.smoothLightPipeline = smoothLightPipeline;
	}

	@Override
	public QuadEmitter getEmitter() {
		editorQuad.clear();
		return editorQuad;
	}

	@Override
	public ModelData getModelData() {
		return this.modelData;
	}

	@Override
	public RenderType getRenderLayer() {
		return this.renderLayer;
	}

	@Override
	public ItemDisplayContext itemTransformationMode() {
		throw new IllegalStateException("itemTransformationMode() can only be called on an item render context.");
	}

	@SuppressWarnings("removal")
	@Override
	public BakedModelConsumer bakedModelConsumer() {
		return null;
	}

	public void prepareForWorld(BlockAndTintGetter blockView, boolean enableCulling) {
		this.renderRegion = blockView;
		this.enableCulling = enableCulling;
	}

	public void prepareForBlock(BlockState blockState, BlockPos blockPos, boolean modelAo, ModelData modelData, @Nullable RenderType renderLayer) {
		this.blockPos = blockPos;
		this.blockState = blockState;

		this.useAO = Minecraft.useAmbientOcclusion();
		this.defaultAO = this.useAO && modelAo && blockState.getLightEmission() == 0;

		this.modelData = modelData != null ? modelData : ModelData.EMPTY;
		this.renderLayer = renderLayer != null ? renderLayer : ItemBlockRenderTypes.getChunkRenderType(blockState);

		this.cullCompletionFlags = 0;
		this.cullResultFlags = 0;
	}

	@Override
	public boolean isFaceCulled(@Nullable Direction face) {
		return !this.shouldRenderFace(face);
	}

	public boolean shouldRenderFace(@Nullable Direction face) {
		if (face == null || !enableCulling) {
			return true;
		}

		final int mask = 1 << face.get3DDataValue();

		if ((cullCompletionFlags & mask) == 0) {
			cullCompletionFlags |= mask;

			if (this.faceNotOccluded(blockState, face)) {
				cullResultFlags |= mask;
				return true;
			} else {
				return false;
			}
		} else {
			return (cullResultFlags & mask) != 0;
		}
	}

	public boolean faceNotOccluded(BlockState blockState, Direction face) {
		BlockGetter blockGetter = this.renderRegion;

		BlockPos adjPos = tempPos.setWithOffset(blockPos, face);
		BlockState adjBlockState = blockGetter.getBlockState(adjPos);

		// FumoFumo's OBJ model is not a cube mesh. Do not apply the ordinary
		// adjacent-face culling rules to its model output.
		if (BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).getNamespace().equals("fumofumo")) {
			return true;
		}

		if (blockState.skipRendering(adjBlockState, face)) {
			return false;
		}

		if (net.vulkanmod.Initializer.CONFIG.leavesCulling) {
			if (blockState.getBlock() instanceof LeavesBlock && adjBlockState.getBlock() instanceof LeavesBlock) {
				return false;
			}
		}

		if (adjBlockState.canOcclude()) {
			VoxelShape shape = blockState.getFaceOcclusionShape(blockGetter, blockPos, face);

			if (shape.isEmpty())
				return true;

			VoxelShape adjShape = adjBlockState.getFaceOcclusionShape(blockGetter, adjPos, face.getOpposite());

			if (adjShape.isEmpty())
				return true;

			if (shape == Shapes.block() && adjShape == Shapes.block()) {
				return false;
			}

			Block.BlockStatePairKey blockStatePairKey = new Block.BlockStatePairKey(blockState, adjBlockState, face);

			byte b = occlusionCache.getAndMoveToFirst(blockStatePairKey);
			if (b != 127) {
				return b != 0;
			} else {
				boolean bl = Shapes.joinIsNotEmpty(shape, adjShape, BooleanOp.ONLY_FIRST);

				if (occlusionCache.size() == 2048) {
					occlusionCache.removeLastByte();
				}

				occlusionCache.putAndMoveToFirst(blockStatePairKey, (byte) (bl ? 1 : 0));
				return bl;
			}
		}

		return true;
	}

	private void renderQuad(MutableQuadViewImpl quad) {
		if (!transform(quad)) {
			return;
		}

		if (isFaceCulled(quad.cullFace())) {
			return;
		}

		endRenderQuad(quad);
	}

	protected void endRenderQuad(MutableQuadViewImpl quad) {}

	/** handles block color, common to all renders. */
	protected void colorizeQuad(MutableQuadViewImpl quad, int colorIndex) {
		if (colorIndex != -1) {
			final int blockColor = getBlockColor(this.renderRegion, colorIndex);

			for (int i = 0; i < 4; i++) {
				quad.color(i, ColorHelper.multiplyColor(blockColor, quad.color(i)));
			}
		}
	}

	private int getBlockColor(BlockAndTintGetter region, int colorIndex) {
		return 0xFF000000 | this.blockColors.getColor(this.blockState, region, this.blockPos, colorIndex);
	}

	protected void shadeQuad(MutableQuadViewImpl quad, LightPipeline lightPipeline, boolean emissive) {
		QuadLightData data = this.quadLightData;

		lightPipeline.calculate(quad, this.blockPos, data, quad.cullFace(), quad.lightFace(), quad.hasShade());

		if (emissive) {
			for (int i = 0; i < 4; i++) {
				quad.color(i, ColorHelper.multiplyRGB(quad.color(i), data.br[i]));
				quad.lightmap(i, LightTexture.FULL_BRIGHT);
			}
		} else {
			for (int i = 0; i < 4; i++) {
				quad.color(i, ColorHelper.multiplyRGB(quad.color(i), data.br[i]));
				quad.lightmap(i, ColorHelper.maxBrightness(quad.lightmap(i), data.lm[i]));
			}
		}
	}

	/**
	 * Vanilla model fallback: pulls quads through {@link BakedModel#getQuads} and pushes them
	 * into this context. Forge context is carried by {@link #getModelData()}/{@link #getRenderLayer()}.
	 */
	public void emitBlockQuads(BakedModel model, @Nullable BlockState state, Supplier<RandomSource> randomSupplier, RenderContext context) {
		MutableQuadViewImpl quad = this.editorQuad;
		final RenderMaterial defaultMaterial = model.useAmbientOcclusion() ? STANDARD_MATERIAL : NO_AO_MATERIAL;

		final ModelData modelData = context.getModelData();
		final RenderType renderLayer = context.getRenderLayer();
		final boolean noTransform = !this.hasTransform();

		for (int i = 0; i <= ModelHelper.NULL_FACE_ID; i++) {
			final Direction cullFace = ModelHelper.faceFromIndex(i);

			if (noTransform && context.isFaceCulled(cullFace)) {
				// Skip entire quad list if possible.
				continue;
			}

			final List<BakedQuad> quads = model.getQuads(state, cullFace, randomSupplier.get(), modelData, renderLayer);
			final int count = quads.size();

			//noinspection ForLoopReplaceableByForEach
			for (int j = 0; j < count; j++) {
				final BakedQuad q = quads.get(j);
				quad.fromVanilla(q, defaultMaterial, cullFace);

				if (noTransform) {
					this.endRenderQuad(quad);
				} else {
					this.renderQuad(quad);
				}
			}
		}
	}
}
