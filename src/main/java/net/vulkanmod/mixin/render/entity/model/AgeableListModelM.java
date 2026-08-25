package net.vulkanmod.mixin.render.entity.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.AgeableListModel;
import net.vulkanmod.compat.render.GuiEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 1.20.1 backport of the 1.21.x AgeableListModelM. Forge 1.20.1 runs SRG, so the target is the literal
// SRG name m_7695_ (= renderToBuffer) with remap=false; the 1.20.1 signature is the float r/g/b/a form
// (1.21.x packed the colour into a single int). Inert unless a GUI entity preview is active.
@Mixin(AgeableListModel.class)
public class AgeableListModelM {
    @Inject(method = "m_7695_", at = @At("HEAD"), remap = false)
    private void vulkanMod$deferDirectGuiPlayerModelStateBoundary(PoseStack poseStack, VertexConsumer buffer,
                                                                  int packedLight, int packedOverlay,
                                                                  float red, float green, float blue, float alpha,
                                                                  CallbackInfo ci) {
        if (!GuiEntityRenderState.isGuiEntityPreview(packedLight)) {
            return;
        }

        GuiEntityRenderState.prepareDeferredDraw();
    }
}
