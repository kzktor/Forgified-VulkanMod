package net.vulkanmod.compat.bobby;

import net.vulkanmod.Initializer;
import net.vulkanmod.compat.CompatDetector;
import net.vulkanmod.render.chunk.ChunkStatusMap;

import java.lang.reflect.Method;

/**
 * Bridges Bobby's cached ("fake") chunks into VulkanMod's chunk visibility tracking.
 * <p>
 * VulkanMod does not render a chunk until {@link ChunkStatusMap} reports it as ready, and that map is
 * only fed by the vanilla network path ({@code ClientChunkCache#replaceWithPacketData} and
 * {@code ClientPacketListener#applyLightData}). Bobby's fake chunks bypass that path entirely: they are
 * substituted client-side, so no chunk-data or light packet ever arrives for them. Without this bridge
 * the chunks exist in the client world but stay invisible.
 * <p>
 * Bobby is a Fabric mod, so it is only present on this Forge fork by way of Sinytra Connector. Every
 * hook here is reflective and fails soft: when Bobby is absent, nothing is touched.
 */
public final class BobbyBridge {
    private static final String MOD_ID = "bobby";
    private static final String BOBBY_CLASS = "de.johni0702.minecraft.bobby.Bobby";
    private static final String CONFIG_CLASS = "de.johni0702.minecraft.bobby.BobbyConfig";

    private static boolean initialized;
    private static volatile boolean available;

    private static Object instance;
    /** {@code null} on the Forge port, where the config is a static holder rather than an instance. */
    private static Method getConfig;
    private static Method getMaxRenderDistance;

    private BobbyBridge() {
    }

    private static synchronized void init() {
        if (initialized)
            return;
        initialized = true;

        if (!CompatDetector.isModLoaded(MOD_ID))
            return;

        try {
            Class<?> bobbyClass = Class.forName(BOBBY_CLASS);
            instance = bobbyClass.getMethod("getInstance").invoke(null);
            if (instance == null)
                return;

            // Upstream Bobby exposes the config per instance; the Forge port (Bobby Reforged) replaced it
            // with static accessors on BobbyConfig and dropped Bobby#getConfig entirely.
            try {
                getConfig = bobbyClass.getMethod("getConfig");
                Object config = getConfig.invoke(instance);
                if (config == null)
                    return;

                getMaxRenderDistance = config.getClass().getMethod("getMaxRenderDistance");
            } catch (NoSuchMethodException e) {
                getConfig = null;
                getMaxRenderDistance = Class.forName(CONFIG_CLASS).getMethod("getMaxRenderDistance");
            }

            available = true;
            Initializer.LOGGER.info("Bobby fake-chunk bridge enabled for bobby {}",
                    CompatDetector.getModVersion(MOD_ID));
        } catch (Throwable t) {
            Initializer.LOGGER.warn("Could not hook Bobby: {}", t.toString());
        }
    }

    public static boolean isAvailable() {
        if (!initialized)
            init();
        return available;
    }

    /**
     * Bobby's configured maximum view distance, or {@code 0} when Bobby is absent or the value cannot
     * be read. Bobby extends the render-distance slider well past vanilla's 32 because cached chunks
     * cost no server bandwidth, so VulkanMod's own slider has to follow it.
     */
    public static int getMaxRenderDistance() {
        if (!isAvailable())
            return 0;

        try {
            if (getConfig == null)
                return (int) getMaxRenderDistance.invoke(null);

            Object config = getConfig.invoke(instance);
            if (config == null)
                return 0;

            return (int) getMaxRenderDistance.invoke(config);
        } catch (Throwable t) {
            disable(t);
            return 0;
        }
    }

    /**
     * Marks a fake chunk as fully ready for rendering. Bobby supplies both block data and light in one
     * step, so {@link ChunkStatusMap#CHUNK_READY} is set at once rather than in the two stages the
     * vanilla network path uses.
     */
    public static void onFakeChunkLoad(int x, int z) {
        ChunkStatusMap statusMap = ChunkStatusMap.INSTANCE;
        if (statusMap != null)
            statusMap.setChunkStatus(x, z, ChunkStatusMap.CHUNK_READY);
    }

    /**
     * Clears the ready flag when a fake chunk is dropped, either because the real chunk arrived or
     * because it fell out of range.
     */
    public static void onFakeChunkUnload(int x, int z) {
        ChunkStatusMap statusMap = ChunkStatusMap.INSTANCE;
        if (statusMap != null)
            statusMap.resetChunkStatus(x, z, ChunkStatusMap.CHUNK_READY);
    }

    private static void disable(Throwable t) {
        available = false;
        Initializer.LOGGER.error("Disabling Bobby fake-chunk bridge after failure", t);
    }
}
