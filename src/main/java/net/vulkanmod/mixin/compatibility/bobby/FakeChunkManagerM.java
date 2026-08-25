package net.vulkanmod.mixin.compatibility.bobby;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.vulkanmod.compat.bobby.BobbyBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeds Bobby's cached chunks into {@link net.vulkanmod.render.chunk.ChunkStatusMap} so they render.
 * <p>
 * Bobby substitutes fake chunks client-side without any chunk-data or light packet, so the vanilla
 * hooks in {@code ClientChunkCacheM}/{@code ClientPacketListenerM} never see them and VulkanMod treats
 * them as not-yet-ready, i.e. invisible.
 *
 * @see BobbyBridge
 */
@Pseudo
@Mixin(targets = "de.johni0702.minecraft.bobby.FakeChunkManager", remap = false)
public class FakeChunkManagerM {

    @Inject(method = "load", at = @At("TAIL"), remap = false, require = 0)
    private void vulkanmod$onFakeChunkLoad(int x, int z, LevelChunk chunk, CallbackInfo ci) {
        BobbyBridge.onFakeChunkLoad(x, z);
    }

    @Inject(method = "unload", at = @At("TAIL"), remap = false, require = 0)
    private void vulkanmod$onFakeChunkUnload(int x, int z, boolean willBeReplaced, CallbackInfoReturnable<Boolean> cir) {
        BobbyBridge.onFakeChunkUnload(x, z);
    }

    /**
     * Suppresses Bobby's vanilla per-section invalidation. VulkanMod already routes
     * {@code LevelRenderer#setSectionDirty} into its own renderer and the status-map update above
     * schedules the visibility-graph rebuild, so the vanilla call is redundant work over the whole
     * newly loaded chunk column.
     * <p>
     * {@code remap = true} overrides the class-level {@code remap = false}, so the descriptor does get a
     * refmap entry and binds in production as well as in dev. It stays {@code require = 0} because it is
     * only an optimisation: Bobby is free to restructure {@code load}, and a miss costs the redundant
     * invalidation, never a crash. Rendering correctness lives entirely in the two {@code @Inject}s
     * above, which are name-based.
     */
    @Redirect(
            method = "load",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;setSectionDirtyWithNeighbors(III)V"
            ),
            remap = true,
            require = 0
    )
    private void vulkanmod$skipVanillaSectionInvalidation(ClientLevel level, int x, int y, int z) {
    }
}
