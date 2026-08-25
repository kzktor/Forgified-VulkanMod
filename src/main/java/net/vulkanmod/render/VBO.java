package net.vulkanmod.render;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.vulkanmod.interfaces.ShaderMixed;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.memory.AutoIndexBuffer;
import net.vulkanmod.vulkan.memory.IndexBuffer;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.VertexBuffer;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

@OnlyIn(Dist.CLIENT)
public class VBO {
    private VertexBuffer vertexBuffer;
    private IndexBuffer indexBuffer;

    private int indexCount;
    private int vertexCount;
    private VertexFormat.Mode mode;

    private boolean autoIndexed = false;

    public VBO() {}

    public void upload(BufferBuilder.RenderedBuffer buffer) {
        BufferBuilder.DrawState parameters = buffer.drawState();

        this.indexCount = parameters.indexCount();
        this.vertexCount = parameters.vertexCount();
        this.mode = parameters.mode();

        if (this.vertexCount <= 0) {
            this.indexCount = 0;
            buffer.release();
            return;
        }

        this.configureVertexFormat(parameters, buffer.vertexBuffer());
        this.configureIndexBuffer(parameters, buffer.indexBuffer());

        buffer.release();

    }

    private void configureVertexFormat(BufferBuilder.DrawState parameters, ByteBuffer data) {
        if (!parameters.indexOnly()) {

            if (this.vertexBuffer != null)
                this.vertexBuffer.freeBuffer();

            this.vertexBuffer = new VertexBuffer(data.remaining(), MemoryTypes.GPU_MEM);
            this.vertexBuffer.copyToVertexBuffer(parameters.format().getVertexSize(), parameters.vertexCount(), data);

        }
    }

    private void configureIndexBuffer(BufferBuilder.DrawState parameters, ByteBuffer data) {
        if (parameters.sequentialIndex()) {

            AutoIndexBuffer autoIndexBuffer;
            switch (this.mode) {
                case TRIANGLE_FAN -> {
                    autoIndexBuffer = Renderer.getDrawer().getTriangleFanIndexBuffer();
                    this.indexCount = AutoIndexBuffer.DrawType.getTriangleStripIndexCount(this.vertexCount);
                }
                case TRIANGLE_STRIP, LINE_STRIP -> {
                    autoIndexBuffer = Renderer.getDrawer().getTriangleStripIndexBuffer();
                    this.indexCount = AutoIndexBuffer.DrawType.getTriangleStripIndexCount(this.vertexCount);
                }
                case QUADS -> {
                    autoIndexBuffer = Renderer.getDrawer().getQuadsIndexBuffer();
                }
                case LINES -> {
                    autoIndexBuffer = Renderer.getDrawer().getLinesIndexBuffer();
                }
                case DEBUG_LINE_STRIP -> {
                    autoIndexBuffer = Renderer.getDrawer().getDebugLineStripIndexBuffer();
                }
                case TRIANGLES, DEBUG_LINES -> {
                    autoIndexBuffer = null;
                }
                default -> throw new IllegalStateException("Unexpected draw mode: %s".formatted(this.mode));
            }

            if (this.indexBuffer != null && !this.autoIndexed)
                this.indexBuffer.freeBuffer();

            if (autoIndexBuffer != null) {
                autoIndexBuffer.checkCapacity(this.vertexCount);
                this.indexBuffer = autoIndexBuffer.getIndexBuffer();
            }

            this.autoIndexed = true;

        } else {
            if (this.indexBuffer != null)
                this.indexBuffer.freeBuffer();

            this.indexBuffer = new IndexBuffer(data.remaining(), MemoryTypes.GPU_MEM);
            this.indexBuffer.copyBuffer(data);
        }

    }

    public void drawWithShader(Matrix4f MV, Matrix4f P, ShaderInstance shader) {
        if (this.indexCount != 0) {
            RenderSystem.assertOnRenderThread();

            RenderSystem.setShader(() -> shader);

            drawWithShader(MV, P, ((ShaderMixed) shader).getPipeline());

        }
    }

    public void drawWithShader(Matrix4f MV, Matrix4f P, GraphicsPipeline pipeline) {
        if (this.indexCount != 0) {
            RenderSystem.assertOnRenderThread();

            VRenderSystem.applyMVP(MV, P);

            VRenderSystem.setPrimitiveTopologyGL(this.mode.asGLMode);

            Renderer renderer = Renderer.getInstance();
            renderer.bindGraphicsPipeline(pipeline);
            VTextureSelector.bindShaderTextures(pipeline);
            renderer.uploadAndBindUBOs(pipeline);

            if (this.indexBuffer != null)
                Renderer.getDrawer().drawIndexed(this.vertexBuffer, this.indexBuffer, this.indexCount);
            else
                Renderer.getDrawer().draw(this.vertexBuffer, this.vertexCount);

            VRenderSystem.applyMVP(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix());

        }
    }

    public void drawChunkLayer() {
        if (this.indexCount == 0)
            return;

        RenderSystem.assertOnRenderThread();

        // VulkanMod's own terrain never travels through the vanilla VertexBuffer, so every caller
        // that gets here is an external chunk-style renderer - Litematica's schematic world, for
        // one. Those follow the vanilla renderChunkLayer recipe: fill the render type's shader with
        // ModelViewMat/ProjMat/ChunkOffset, call bind()/apply(), then draw() per section. Under
        // Vulkan bind() and apply() are no-ops, so without binding the shader's pipeline here the
        // draw is submitted with whatever pipeline the previous pass left behind (nothing visible).
        ShaderInstance shader = RenderSystem.getShader();
        GraphicsPipeline pipeline = shader != null ? ((ShaderMixed) shader).getPipeline() : null;

        if (pipeline == null) {
            if (this.indexBuffer != null)
                Renderer.getDrawer().drawIndexed(this.vertexBuffer, this.indexBuffer, this.indexCount);
            else
                Renderer.getDrawer().draw(this.vertexBuffer, this.vertexCount);
            return;
        }

        drawWithShader(modelViewOf(shader), projectionOf(shader), pipeline);
    }

    /**
     * Reads the model-view the caller staged on the shader and folds ChunkOffset into it. VulkanMod's
     * bundled render type pipelines take a single MVP matrix and have no ChunkOffset uniform, whereas
     * {@code MVP * (Position + ChunkOffset)} is exactly {@code P * (MV * translate(ChunkOffset)) * Position}.
     */
    private static Matrix4f modelViewOf(ShaderInstance shader) {
        Uniform modelView = shader.MODEL_VIEW_MATRIX;
        Matrix4f matrix = modelView != null
                ? new Matrix4f().set(modelView.getFloatBuffer())
                : new Matrix4f(RenderSystem.getModelViewMatrix());

        Uniform chunkOffset = shader.CHUNK_OFFSET;
        if (chunkOffset != null) {
            FloatBuffer offset = chunkOffset.getFloatBuffer();
            matrix.translate(offset.get(0), offset.get(1), offset.get(2));
        }

        return matrix;
    }

    private static Matrix4f projectionOf(ShaderInstance shader) {
        Uniform projection = shader.PROJECTION_MATRIX;
        return projection != null
                ? new Matrix4f().set(projection.getFloatBuffer())
                : new Matrix4f(RenderSystem.getProjectionMatrix());
    }

    public void close() {
        if (this.vertexCount <= 0)
            return;

        this.vertexBuffer.freeBuffer();
        this.vertexBuffer = null;

        if (!this.autoIndexed) {
            this.indexBuffer.freeBuffer();
            this.indexBuffer = null;
        }

        this.vertexCount = 0;
        this.indexCount = 0;
    }

}

