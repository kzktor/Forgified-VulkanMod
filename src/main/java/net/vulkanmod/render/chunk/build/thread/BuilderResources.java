package net.vulkanmod.render.chunk.build.thread;

import net.vulkanmod.Initializer;
import net.vulkanmod.compat.dynamiclights.DynamicLightsBridge;
import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.build.BlockRenderer;
import net.vulkanmod.render.chunk.build.LiquidRenderer;
import net.vulkanmod.render.chunk.build.RenderRegion;
import net.vulkanmod.render.chunk.build.TintCache;
import net.vulkanmod.render.chunk.build.light.LightMode;
import net.vulkanmod.render.chunk.build.light.LightPipeline;
import net.vulkanmod.render.chunk.build.light.data.ArrayLightDataCache;
import net.vulkanmod.render.chunk.build.light.data.QuadLightData;
import net.vulkanmod.render.chunk.build.light.flat.FlatLightPipeline;
import net.vulkanmod.render.chunk.build.light.smooth.NewSmoothLightPipeline;
import net.vulkanmod.render.chunk.build.light.smooth.SmoothLightPipeline;
import net.vulkanmod.render.chunk.build.frapi.render.TerrainRenderContext;

public class BuilderResources {
    public final ThreadBuilderPack builderPack = new ThreadBuilderPack();
    public final BlockRenderer blockRenderer = new BlockRenderer();
    public final LiquidRenderer liquidRenderer = new LiquidRenderer();

    public final TintCache tintCache = new TintCache();

    public RenderRegion region;

    public final ArrayLightDataCache lightDataCache = new ArrayLightDataCache();
    public final QuadLightData quadLightData = new QuadLightData();

    public final LightPipeline smoothLightPipeline;
    public final LightPipeline flatLightPipeline;

    /**
     * FRAPI terrain context, used for models that opt out of the vanilla {@code getQuads} path
     * (Continuity's connected textures, for one). Created lazily: it touches the Fabric Rendering
     * API, which is only on the classpath when a mod that needs it is installed.
     */
    private TerrainRenderContext terrainRenderContext;

    private int totalBuildTime = 0, buildCount = 0;

    public BuilderResources() {
        this.flatLightPipeline = new FlatLightPipeline(lightDataCache);

        if(Initializer.CONFIG.ambientOcclusion == LightMode.SUB_BLOCK)
            this.smoothLightPipeline = new NewSmoothLightPipeline(lightDataCache);
        else
            this.smoothLightPipeline = new SmoothLightPipeline(lightDataCache);
    }

    public void update(RenderRegion region, RenderSection renderSection) {
        this.region = region;

        lightDataCache.reset(region, renderSection.xOffset(), renderSection.yOffset(), renderSection.zOffset());

        DynamicLightsBridge.refresh();

        blockRenderer.setResources(this);
        liquidRenderer.setResources(this);

        if (this.terrainRenderContext != null) {
            this.terrainRenderContext.prepareForRegion();
        }
    }

    public TerrainRenderContext terrainRenderContext() {
        if (this.terrainRenderContext == null) {
            this.terrainRenderContext = new TerrainRenderContext(this);
            this.terrainRenderContext.prepareForRegion();
        }

        return this.terrainRenderContext;
    }

    public void clear() {
        builderPack.clearAll();
        this.clearRegion();
    }

    public void clearRegion() {
        this.region = null;
        lightDataCache.clearWorld();

        if (this.terrainRenderContext != null) {
            this.terrainRenderContext.release();
        }
    }

    public void close() {
        builderPack.closeAll();
        this.clearRegion();
    }

    public void updateBuildStats(int buildTime) {
        this.buildCount++;
        this.totalBuildTime += buildTime;
    }

    public int getTotalBuildTime() {
        return totalBuildTime;
    }

    public int getBuildCount() {
        return buildCount;
    }

    public void resetCounters() {
        totalBuildTime = 0;
        buildCount = 0;
    }
}

