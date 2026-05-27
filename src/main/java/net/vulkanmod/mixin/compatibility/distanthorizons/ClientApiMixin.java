package net.vulkanmod.mixin.compatibility.distanthorizons;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ClientApi", remap = false)
public class ClientApiMixin {

    @Inject(method = "renderLods", at = @At("HEAD"), cancellable = true)
    private static void bypassRenderLods(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderLodLayer", at = @At("HEAD"), cancellable = true)
    private static void bypassRenderLodLayer(boolean renderFullRes, CallbackInfo ci) {
        ci.cancel();
    }
}
