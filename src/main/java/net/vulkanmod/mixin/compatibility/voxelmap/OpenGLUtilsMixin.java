package net.vulkanmod.mixin.compatibility.voxelmap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes VoxelMap's minimap rendering upside-down under VulkanMod.
 * <p>
 * VoxelMap draws its minimap into an off-screen FBO and then samples that FBO's colour texture with the
 * OpenGL convention, where the first texture row (v=0) is the <em>bottom</em> of the rendered scene.
 * VulkanMod renders to FBOs the Vulkan way, so the resulting texture's first row (v=0) is the
 * <em>top</em> of the scene. The game's own {@code RenderTarget} compensates for this difference in its
 * blit shader ({@code INVERTED_UV}), but VoxelMap samples the FBO with a plain {@code position_tex}
 * draw and no such compensation, so the map comes out vertically flipped (north-south inverted when
 * rotation is disabled).
 * <p>
 * {@link net.vulkanmod.vulkan.util.DrawUtil} already handles the main render target, so this mixin only
 * patches the one place VoxelMap reads its own minimap FBO: {@code OpenGL.Utils#setMapWithScale}, which
 * is called solely from {@code Map#renderMap} to blit the FBO texture onto the screen. The other
 * {@code setMap} overloads are left untouched, as they draw regular (non-FBO) sprites and arrows.
 */
@Pseudo
@Mixin(targets = "com.mamiyaotaru.voxelmap.util.OpenGL$Utils", remap = false)
public class OpenGLUtilsMixin {

    @Shadow
    private static void ldrawthree(double x, double y, double z, float u, float v) {
    }

    /**
     * Replaces the four vertices written by {@code setMapWithScale} with V-flipped equivalents. The
     * screen positions and U coordinates are unchanged; only the texture V coordinate is inverted
     * (v -> 1 - v), which is exactly the vertical flip VulkanMod's FBO convention needs.
     */
    @Inject(method = "setMapWithScale", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void vulkanmod$flipFboTextureV(int x, int y, float scale, CallbackInfo ci) {
        float s = ((int) (128f * scale)) / 4.0f;

        ldrawthree(x - s, y + s, 1.0, 0.0f, 0.0f);
        ldrawthree(x + s, y + s, 1.0, 1.0f, 0.0f);
        ldrawthree(x + s, y - s, 1.0, 1.0f, 1.0f);
        ldrawthree(x - s, y - s, 1.0, 0.0f, 1.0f);

        ci.cancel();
    }
}
