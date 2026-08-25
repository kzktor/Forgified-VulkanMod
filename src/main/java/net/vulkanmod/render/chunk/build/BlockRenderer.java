package net.vulkanmod.render.chunk.build;

import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.vulkanmod.render.chunk.build.frapi.FrapiBridge;
import net.vulkanmod.render.chunk.build.light.LightPipeline;
import net.vulkanmod.render.chunk.build.light.data.QuadLightData;
import net.vulkanmod.render.chunk.build.thread.BuilderResources;
import net.vulkanmod.render.model.quad.QuadUtils;
import net.vulkanmod.render.model.quad.QuadView;
import net.vulkanmod.render.vertex.TerrainBufferBuilder;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.render.vertex.VertexUtil;
import net.vulkanmod.vulkan.util.ColorUtil;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.ChunkRenderTypeSet;
import org.joml.Vector3f;

import java.util.List;

public class BlockRenderer {

    static final Direction[] DIRECTIONS = Direction.values();
    private static BlockColors blockColors;

    RandomSource randomSource = RandomSource.createNewThreadLocalInstance();

    Vector3f pos;
    BlockPos blockPos;
    BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

    BuilderResources resources;

    BlockState blockState;
    ModelData modelData = ModelData.EMPTY;
    RenderType renderType;

    public void setResources(BuilderResources resources) {
        this.resources = resources;
    }

    final Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey> occlusionCache = new Object2ByteLinkedOpenHashMap<>(2048, 0.25F) {
        protected void rehash(int i) {
        }
    };

    public BlockRenderer() {
        occlusionCache.defaultReturnValue((byte) 127);
    }

    public static void setBlockColors(BlockColors blockColors) {
        BlockRenderer.blockColors = blockColors;
    }

    public void renderBatched(BlockState blockState, BlockPos blockPos, Vector3f pos, TerrainBufferBuilder bufferBuilder) {
        this.pos = pos;
        this.blockPos = blockPos;
        this.blockState = blockState;

        long seed = blockState.getSeed(blockPos);

        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(blockState);
        this.modelData = model.getModelData(resources.region, blockPos, blockState, ModelData.EMPTY);
        Vec3 offset = blockState.getOffset(resources.region, blockPos);
        pos.add((float) offset.x, (float) offset.y, (float) offset.z);

        randomSource.setSeed(seed);
        ChunkRenderTypeSet renderTypes = model.getRenderTypes(blockState, randomSource, modelData);

        // Models that opt out of the vanilla getQuads contract (Continuity's connected textures,
        // for one) only produce their real geometry through the Fabric Rendering API. Route those
        // through the FRAPI context instead; it emits into the same buffers.
        if (FrapiBridge.isAvailable() && FrapiBridge.needsFrapi(model)) {
            RenderType frapiRenderType = renderTypes.asList().isEmpty()
                    ? ItemBlockRenderTypes.getChunkRenderType(blockState)
                    : renderTypes.asList().get(0);

            FrapiBridge.tessellate(resources, model, blockState, blockPos, pos, seed, modelData, frapiRenderType);
            return;
        }

        // Forge composite and OBJ models can emit quads into a render type that
        // differs from the block state's default layer. Match the reference
        // renderer by asking the model for its declared layers first.
        boolean rendered = false;
        for (RenderType modelRenderType : renderTypes) {
            TerrainRenderType terrainRenderType = TerrainRenderType.get(modelRenderType);
            if (terrainRenderType == null) {
                continue;
            }

            this.renderType = modelRenderType;
            TerrainBufferBuilder modelBufferBuilder = resources.builderPack.builder(compactRenderType(terrainRenderType));
            modelBufferBuilder.setBlockAttributes(blockState);
            tessellateBlock(model, modelBufferBuilder, seed, modelRenderType);
            rendered = true;
        }

        // A few older Forge model implementations return no render types.
        // Preserve the old behavior for those models.
        if (!rendered) {
            RenderType defaultRenderType = ItemBlockRenderTypes.getChunkRenderType(blockState);
            this.renderType = defaultRenderType;
            tessellateBlock(model, bufferBuilder, seed, defaultRenderType);
        }
    }

    public void tessellateBlock(BakedModel bakedModel, TerrainBufferBuilder bufferBuilder, long seed, RenderType modelRenderType) {
        boolean useAO = Minecraft.useAmbientOcclusion() && blockState.getLightEmission() == 0 && bakedModel.useAmbientOcclusion();
        LightPipeline lightPipeline = useAO ? resources.smoothLightPipeline : resources.flatLightPipeline;

        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < DIRECTIONS.length; ++i) {
            Direction direction = DIRECTIONS[i];

            randomSource.setSeed(seed);
            List<BakedQuad> quads = bakedModel.getQuads(blockState, direction, randomSource, modelData, modelRenderType);

            if (!quads.isEmpty()) {
                mutableBlockPos.setWithOffset(blockPos, direction);
                if (shouldRenderFace(blockState, direction, mutableBlockPos)) {
                    renderModelFace(bufferBuilder, quads, lightPipeline, direction);
                }
            }
        }

        randomSource.setSeed(seed);
        List<BakedQuad> quads = bakedModel.getQuads(blockState, null, randomSource, modelData, modelRenderType);
        if (!quads.isEmpty()) {
            renderModelFace(bufferBuilder, quads, lightPipeline, null);
        }
    }

    public static TerrainRenderType compactRenderType(TerrainRenderType renderType) {
        if (net.vulkanmod.Initializer.CONFIG.uniqueOpaqueLayer) {
            return switch (renderType) {
                case SOLID, CUTOUT, CUTOUT_MIPPED -> TerrainRenderType.CUTOUT_MIPPED;
                case TRANSLUCENT, TRIPWIRE -> TerrainRenderType.TRANSLUCENT;
            };
        }

        return switch (renderType) {
            case SOLID, CUTOUT_MIPPED -> TerrainRenderType.CUTOUT_MIPPED;
            case CUTOUT -> TerrainRenderType.CUTOUT;
            case TRANSLUCENT, TRIPWIRE -> TerrainRenderType.TRANSLUCENT;
        };
    }

    private void renderModelFace(TerrainBufferBuilder bufferBuilder, List<BakedQuad> quads, LightPipeline lightPipeline, Direction cullFace) {
        QuadLightData quadLightData = resources.quadLightData;

        for (int i = 0; i < quads.size(); ++i) {
            BakedQuad bakedQuad = quads.get(i);
            QuadView quadView = (QuadView) bakedQuad;
            lightPipeline.calculate(quadView, blockPos, quadLightData, cullFace, bakedQuad.getDirection(), bakedQuad.isShade());
            putQuadData(bufferBuilder, quadView, quadLightData);
        }
    }

    private void putQuadData(TerrainBufferBuilder bufferBuilder, QuadView quadView, QuadLightData quadLightData) {
        float r, g, b;
        if (quadView.isTinted()) {
            int color = blockColors.getColor(blockState, resources.region, blockPos, quadView.getColorIndex());
            r = ColorUtil.ARGB.unpackR(color);
            g = ColorUtil.ARGB.unpackG(color);
            b = ColorUtil.ARGB.unpackB(color);
        } else {
            r = 1.0F;
            g = 1.0F;
            b = 1.0F;
        }

        putQuadData(bufferBuilder, pos, quadView, quadLightData, r, g, b);
    }

    public static void putQuadData(TerrainBufferBuilder bufferBuilder, Vector3f pos, QuadView quad, QuadLightData quadLightData, float red, float green, float blue) {
        Vec3i normal = quad.getFacingDirection().getNormal();
        int packedNormal = VertexUtil.packNormal(normal.getX(), normal.getY(), normal.getZ());

        float[] brightnessArr = quadLightData.br;
        int[] lights = quadLightData.lm;

        // Rotate triangles if needed to fix AO anisotropy
        int idx = QuadUtils.getIterationStartIdx(brightnessArr, lights);

        bufferBuilder.ensureCapacity();

        for (byte i = 0; i < 4; ++i) {
            final float x = pos.x() + quad.getX(idx);
            final float y = pos.y() + quad.getY(idx);
            final float z = pos.z() + quad.getZ(idx);

            final float r, g, b;
            final float quadR, quadG, quadB;

            final int quadColor = quad.getColor(idx);
            quadR = ColorUtil.RGBA.unpackR(quadColor);
            quadG = ColorUtil.RGBA.unpackG(quadColor);
            quadB = ColorUtil.RGBA.unpackB(quadColor);

            final float brightness = brightnessArr[idx];
            r = quadR * brightness * red;
            g = quadG * brightness * green;
            b = quadB * brightness * blue;

            final int color = ColorUtil.RGBA.pack(r, g, b, 1.0f);
            final int light = lights[idx];
            final float u = quad.getU(idx);
            final float v = quad.getV(idx);

            bufferBuilder.vertex(x, y, z, color, u, v, light, packedNormal);

            idx = (idx + 1) & 0b11;
        }

    }

    public boolean shouldRenderFace(BlockState blockState, Direction direction, BlockPos adjPos) {
        BlockGetter blockGetter = resources.region;
        BlockState adjBlockState = blockGetter.getBlockState(adjPos);

        // FumoFumo's OBJ model is not a cube mesh. Do not apply the ordinary
        // adjacent-face culling rules to its model output.
        if (BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).getNamespace().equals("fumofumo")) {
            return true;
        }

        if (blockState.skipRendering(adjBlockState, direction)) {
            return false;
        }

        if (net.vulkanmod.Initializer.CONFIG.leavesCulling) {
            if (blockState.getBlock() instanceof LeavesBlock && adjBlockState.getBlock() instanceof LeavesBlock) {
                return false;
            }
        }

        if (adjBlockState.canOcclude()) {
            VoxelShape shape = blockState.getFaceOcclusionShape(blockGetter, blockPos, direction);

            if (shape.isEmpty())
                return true;

            VoxelShape adjShape = adjBlockState.getFaceOcclusionShape(blockGetter, adjPos, direction.getOpposite());

            if (adjShape.isEmpty())
                return true;

            if (shape == Shapes.block() && adjShape == Shapes.block()) {
                return false;
            }

            Block.BlockStatePairKey blockStatePairKey = new Block.BlockStatePairKey(blockState, adjBlockState, direction);

            byte b = occlusionCache.getAndMoveToFirst(blockStatePairKey);
            if (b != 127) {
                return b != 0;
            } else {
                boolean bl = Shapes.joinIsNotEmpty(shape, adjShape, BooleanOp.ONLY_FIRST);

                if (occlusionCache.size() == 2048) {
                    occlusionCache.removeLastByte();
                }

                occlusionCache.putAndMoveToFirst(blockStatePairKey, (byte) (bl ? 1 : 0));
                return bl;
            }
        }

        return true;
    }
}
