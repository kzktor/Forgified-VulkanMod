package net.vulkanmod.mixin.render.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.vulkanmod.compat.render.GuiEntityRenderState;
import net.vulkanmod.interfaces.ExtendedVertexBuilder;
import net.vulkanmod.interfaces.ModelPartCubeMixed;
import net.vulkanmod.render.model.CubeModel;
import net.vulkanmod.render.vertex.VertexUtil;
import net.vulkanmod.vulkan.util.ColorUtil;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// 1.20.1 port of the 1.21.x CubeModel fast model path: transforms 8 vertices per cube instead of
// 24, and writes vertices through the packed ExtendedVertexBuilder fast path when available.
@Mixin(value = ModelPart.class, priority = 900)
public class ModelPartM {

    @Shadow(remap = false) @Final private List<ModelPart.Cube> f_104212_;

    @Unique
    private final Vector3f vulkanMod$normal = new Vector3f();

    // compile
    @Inject(method = "m_104290_", at = @At("HEAD"), cancellable = true, remap = false)
    private void compile(PoseStack.Pose pose, VertexConsumer vertexConsumer, int light, int overlay, float r, float g, float b, float a, CallbackInfo ci) {
        // Keep vanilla ModelPart compilation on Forge 1.20.1. The fast CubeModel path
        // changes the vertex ordering and transform lifetime used by block-entity models;
        // that produces solid-color beds/chests and visible head jitter. BufferBuilder's
        // native vertex path is handled by the Vulkan vertex upload mixin below.
    }
}
