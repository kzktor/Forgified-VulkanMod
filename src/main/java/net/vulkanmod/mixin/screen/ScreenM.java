package net.vulkanmod.mixin.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.vulkanmod.compat.render.RenderStateSnapshot;
import net.vulkanmod.vulkan.Renderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenM {
    @Unique
    private RenderStateSnapshot vulkanMod$screenStateSnapshot;
    @Unique
    private RenderStateSnapshot vulkanMod$blurStateSnapshot;

    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void vulkanMod$beginScreenRenderStateBoundary(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        this.vulkanMod$screenStateSnapshot = new RenderStateSnapshot();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        // Give every screen a clean depth buffer before GUI content draws. The world (or a previous
        // frame) leaves its depth in the framebuffer; GUI 3D previews (vanilla inventory, Essential's
        // wardrobe, etc.) enable depth testing and would otherwise z-fight against that stale depth,
        // producing a flickering "blob shadow" under the model. VulkanMod's other screen depth clear
        // lives in renderBlurredBackground, which is skipped in-world and for custom GUI frameworks
        // (Essential/Elementa) that never call it, so clear here where it always runs.
        Renderer.clearAttachments(GL11.GL_DEPTH_BUFFER_BIT);
    }

    @Inject(method = "renderWithTooltip", at = @At("RETURN"))
    private void vulkanMod$endScreenRenderStateBoundary(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        RenderStateSnapshot snapshot = this.vulkanMod$screenStateSnapshot;
        this.vulkanMod$screenStateSnapshot = null;

        if (snapshot != null) {
            snapshot.restore();
        }
    }

    // Screen.renderBlurredBackground does not exist on 1.20.1 (added in 1.20.2 with the menu blur). require=0
    // lets these injects skip gracefully on 1.20.1 while still applying on versions that have the method.
    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void vulkanMod$beginBlurBackgroundState(float f, CallbackInfo ci) {
        if (Minecraft.getInstance().level != null) {
            Renderer.resetScissor();
            ci.cancel();
            return;
        }

        this.vulkanMod$blurStateSnapshot = new RenderStateSnapshot();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        Renderer.resetScissor();
    }

    @Inject(method = "renderBlurredBackground", at = @At("RETURN"), require = 0)
    private void vulkanMod$endBlurBackgroundState(float f, CallbackInfo ci) {
        Renderer.resetScissor();

        Renderer.clearAttachments(256);

        RenderStateSnapshot snapshot = this.vulkanMod$blurStateSnapshot;
        this.vulkanMod$blurStateSnapshot = null;

        if (snapshot != null) {
            snapshot.restore();
        }
    }
}

