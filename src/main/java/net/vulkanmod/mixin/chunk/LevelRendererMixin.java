package net.vulkanmod.mixin.chunk;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.vulkanmod.compat.dynamiclights.DynamicLightsBridge;
import net.vulkanmod.compat.litematica.LitematicaBridge;
import net.vulkanmod.render.chunk.WorldRenderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.SortedSet;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow(remap = false)
    @Final
    private RenderBuffers f_109464_;

    @Shadow(remap = false)
    @Final
    private Long2ObjectMap<SortedSet<BlockDestructionProgress>> f_109409_;
    @Shadow(remap = false)
    private @Nullable ClientLevel f_109465_;

    @Shadow(remap = false)
    public abstract void m_173014_();

    @Shadow(remap = false)
    private int f_109438_;
    @Shadow(remap = false)
    @Final
    private Minecraft f_109461_;
    @Shadow(remap = false)
    @Final
    private Set<BlockEntity> f_109468_;
    @Shadow(remap = false)
    private boolean f_109474_;
    @Shadow(remap = false)
    @Final
    private EntityRenderDispatcher f_109463_;

    @Shadow(remap = false)
    protected abstract boolean m_109817_();

    @Shadow(remap = false)
    public abstract void m_109826_();

    @Shadow(remap = false)
    public abstract void m_109599_(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f);

    private WorldRenderer worldRenderer;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(Minecraft minecraft, EntityRenderDispatcher entityRenderDispatcher, BlockEntityRenderDispatcher blockEntityRenderDispatcher, RenderBuffers renderBuffers, CallbackInfo ci) {
        this.worldRenderer = WorldRenderer.init(this.f_109464_);
    }

    @Inject(method = "m_109701_", at = @At("RETURN"), remap = false)
    private void setLevel(ClientLevel clientLevel, CallbackInfo ci) {
        this.worldRenderer.setLevel(clientLevel);
    }

    @Inject(method = "m_109599_", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;m_109588_(Lcom/mojang/blaze3d/vertex/PoseStack;)V", ordinal = 1, shift = At.Shift.BEFORE, remap = false), remap = false)
    private void renderBlockEntities(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci) {
        Vec3 pos = camera.getPosition();
        this.worldRenderer.renderBlockEntities(poseStack, pos.x(), pos.y(), pos.z(), this.f_109409_, f);
    }

    // The methods below replace vanilla behavior with inject-and-cancel instead of @Overwrite so
    // other mods' @Inject/@Redirect handlers into these methods still apply (an @Overwrite removes
    // the target instructions and hard-crashes any mod that redirects into them, e.g. Lodestone).

    // setupRender
    @Inject(method = "m_194338_", at = @At("HEAD"), cancellable = true, remap = false)
    private void setupRender(Camera camera, Frustum frustum, boolean isCapturedFrustum, boolean spectator, CallbackInfo ci) {
        this.worldRenderer.setupRenderer(camera, frustum, isCapturedFrustum, spectator);
        // Litematica culls and rebuilds its schematic chunks from a TAIL inject here, which the
        // cancel below skips. Drive it at the same point in the frame instead.
        LitematicaBridge.afterSetupRender(frustum);
        ci.cancel();
    }

    // compileSections: WorldRenderer uploads sections itself, vanilla dispatcher must not run.
    @Inject(method = "m_194370_", at = @At("HEAD"), cancellable = true, remap = false)
    private void skipCompileSections(Camera camera, CallbackInfo ci) {
        ci.cancel();
    }

    // isSectionCompiled
    @Inject(method = "m_202430_", at = @At("HEAD"), cancellable = true, remap = false)
    private void isSectionCompiled(BlockPos blockPos, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(this.worldRenderer.isSectionCompiled(blockPos));
    }

    // renderChunkLayer
    @Inject(method = "m_172993_", at = @At("HEAD"), cancellable = true, remap = false)
    private void renderSectionLayer(RenderType renderType, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f projectionMatrix, CallbackInfo ci) {
        this.worldRenderer.renderSectionLayer(renderType, poseStack, camX, camY, camZ, projectionMatrix);
        // Forge dispatches RenderLevelStageEvent at the end of the vanilla renderSectionLayer body,
        // which this HEAD+cancel skips. Drive the dynamic-lights update here so moving light sources
        // keep scheduling chunk rebuilds.
        DynamicLightsBridge.updateAllDynamicLights(this);
        // Litematica draws its schematic layer by layer from a TAIL inject here, likewise skipped.
        LitematicaBridge.afterRenderChunkLayer(renderType, poseStack, projectionMatrix);
        ci.cancel();
    }

    // allChanged: skips the vanilla viewArea/chunkRenderDispatcher rebuild (those are replaced by
    // WorldRenderer) and notifies the WorldRenderer afterwards, matching the old @Overwrite +
    // RETURN-inject pair this replaces.
    @Inject(method = "m_109818_", at = @At("HEAD"), cancellable = true, remap = false)
    private void allChanged(CallbackInfo ci) {
        if (this.f_109465_ != null) {
            this.m_173014_();
            this.f_109465_.clearTintCaches();

            this.f_109474_ = true;
            ItemBlockRenderTypes.setFancy(Minecraft.useFancyGraphics());
            this.f_109438_ = this.f_109461_.options.getEffectiveRenderDistance();
            synchronized (this.f_109468_) {
                this.f_109468_.clear();
            }
        }

        this.worldRenderer.allChanged();
        // Litematica reloads its schematic renderers from a RETURN inject here, likewise skipped.
        LitematicaBridge.afterAllChanged(this.f_109465_);
        ci.cancel();
    }

    // setSectionDirty
    @Inject(method = "m_109501_", at = @At("HEAD"), cancellable = true, remap = false)
    private void setSectionDirty(int x, int y, int z, boolean flag, CallbackInfo ci) {
        this.worldRenderer.setSectionDirty(x, y, z, flag);
        ci.cancel();
    }

    // getSectionStatistics
    @Inject(method = "m_109820_", at = @At("HEAD"), cancellable = true, remap = false)
    private void getSectionStatistics(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(this.worldRenderer.getChunkStatistics());
    }

    // hasRenderedAllSections
    @Inject(method = "m_109825_", at = @At("HEAD"), cancellable = true, remap = false)
    private void hasRenderedAllSections(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(this.worldRenderer.graphNeedsUpdate());
    }

    // countRenderedSections
    @Inject(method = "m_109821_", at = @At("HEAD"), cancellable = true, remap = false)
    private void countRenderedSections(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(this.worldRenderer.getVisibleSectionsCount());
    }

    @Redirect(method = "m_173012_", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;m_172790_()F", remap = false), remap = false)
    private float getRenderDistanceZFar(GameRenderer instance) {
        return instance.getRenderDistance() * 4F;
    }

//    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;entitiesForRendering()Ljava/lang/Iterable;"))
//    private Iterable<Entity> replaceIterator(ClientLevel instance) {
//
//        return () -> new Iterator<Entity>() {
//            @Override
//            public boolean hasNext() {
//                return false;
//            }
//
//            @Override
//            public Entity next() {
//                return null;
//            }
//        };
//    }
//
//    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
//            target = "Lnet/minecraft/client/multiplayer/ClientLevel;entitiesForRendering()Ljava/lang/Iterable;",
//            shift = At.Shift.AFTER),
//            locals = LocalCapture.CAPTURE_FAILHARD
//    )
//    private void renderEntities(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci) {
//        for(Entity entity : this.level.entitiesForRendering()) {
//            if (this.entityRenderDispatcher.shouldRender(entity, frustum, d0, d1, d2) || entity.hasIndirectPassenger(this.minecraft.player)) {
//                BlockPos blockpos = entity.blockPosition();
//                if ((this.level.isOutsideBuildHeight(blockpos.getY()) || this.isChunkCompiled(blockpos)) && (entity != p_109604_.getEntity() || p_109604_.isDetached() || p_109604_.getEntity() instanceof LivingEntity && ((LivingEntity)p_109604_.getEntity()).isSleeping()) && (!(entity instanceof LocalPlayer) || p_109604_.getEntity() == entity)) {
//                    ++this.renderedEntities;
//                    if (entity.tickCount == 0) {
//                        entity.xOld = entity.getX();
//                        entity.yOld = entity.getY();
//                        entity.zOld = entity.getZ();
//                    }
//
//                    MultiBufferSource multibuffersource;
//                    if (this.shouldShowEntityOutlines() && this.minecraft.shouldEntityAppearGlowing(entity)) {
//                        flag3 = true;
//                        OutlineBufferSource outlinebuffersource = this.renderBuffers.outlineBufferSource();
//                        multibuffersource = outlinebuffersource;
//                        int i = entity.getTeamColor();
//                        int j = 255;
//                        int k = i >> 16 & 255;
//                        int l = i >> 8 & 255;
//                        int i1 = i & 255;
//                        outlinebuffersource.setColor(k, l, i1, 255);
//                    } else {
//                        multibuffersource = bu;
//                    }
//
//                    this.renderEntity(entity, d0, d1, d2, p_109601_, p_109600_, multibuffersource);
//                }
//            }
//        }
//    }

}
