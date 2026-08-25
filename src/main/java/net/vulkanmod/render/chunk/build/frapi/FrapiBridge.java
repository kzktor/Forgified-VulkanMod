package net.vulkanmod.render.chunk.build.frapi;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.build.thread.BuilderResources;
import org.joml.Vector3f;

/**
 * Entry point to the Fabric Rendering API terrain path.
 *
 * <p>The Fabric Rendering API is an optional compile-time dependency: it is only on the classpath
 * when a mod that needs it (Forgified Fabric API, pulled in by Continuity and friends) is
 * installed. Every reference to it lives behind this class so the terrain renderer never triggers
 * a {@link NoClassDefFoundError} on a plain Forge install.
 */
public final class FrapiBridge {
	private static boolean available;

	private FrapiBridge() {
	}

	/**
	 * Probes for the Fabric Rendering API and claims the renderer slot. Must run before the first
	 * chunk build; a failure here only disables the FRAPI path, it never fails startup.
	 */
	public static void init() {
		try {
			Class.forName("net.fabricmc.fabric.api.renderer.v1.RendererAccess");
		} catch (ClassNotFoundException | LinkageError e) {
			Initializer.LOGGER.info("Fabric Rendering API not present, connected-texture support is off");
			return;
		}

		try {
			Impl.register();
			available = true;
			Initializer.LOGGER.info("Registered VulkanMod as the Fabric Rendering API renderer");
		} catch (Throwable t) {
			Initializer.LOGGER.error("Failed to register the Fabric Rendering API renderer, " +
					"connected-texture support is off", t);
		}
	}

	public static boolean isAvailable() {
		return available;
	}

	/** True when this model refuses the vanilla {@code getQuads} contract. */
	public static boolean needsFrapi(BakedModel model) {
		return Impl.needsFrapi(model);
	}

	public static void tessellate(BuilderResources resources, BakedModel model, BlockState blockState,
	                              BlockPos blockPos, Vector3f pos, long seed, ModelData modelData,
	                              RenderType renderType) {
		Impl.tessellate(resources, model, blockState, blockPos, pos, seed, modelData, renderType);
	}

	/**
	 * Holds every direct Fabric Rendering API reference. Only loaded once {@link #init} has
	 * confirmed the API is present.
	 */
	private static final class Impl {
		static void register() {
			net.fabricmc.fabric.api.renderer.v1.RendererAccess.INSTANCE.registerRenderer(VulkanModRenderer.INSTANCE);
		}

		static boolean needsFrapi(BakedModel model) {
			return !((net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel) model).isVanillaAdapter();
		}

		static void tessellate(BuilderResources resources, BakedModel model, BlockState blockState,
		                       BlockPos blockPos, Vector3f pos, long seed, ModelData modelData,
		                       RenderType renderType) {
			resources.terrainRenderContext()
					.tessellateBlock(model, blockState, blockPos, pos, seed, modelData, renderType);
		}
	}
}
