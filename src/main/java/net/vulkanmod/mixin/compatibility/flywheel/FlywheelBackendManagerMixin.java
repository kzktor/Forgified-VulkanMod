package net.vulkanmod.mixin.compatibility.flywheel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.BackendManagerImpl", remap = false)
public class FlywheelBackendManagerMixin {
    @Inject(method = "isBackendOn", at = @At("HEAD"), cancellable = true, remap = false)
    private static void vulkanmod$disableFlywheelBackend(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
