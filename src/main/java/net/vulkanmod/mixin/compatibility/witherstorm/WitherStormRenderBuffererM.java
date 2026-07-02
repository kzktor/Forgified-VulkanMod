package net.vulkanmod.mixin.compatibility.witherstorm;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "nonamecrackers2.witherstormmod.client.instancing.RenderBufferer", remap = false)
public class WitherStormRenderBuffererM {
    @Inject(method = "shouldUse()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void vulkanmod$disableBufferedRendering(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
