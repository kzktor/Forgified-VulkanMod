package net.vulkanmod.compat.dynamiclights;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.vulkanmod.Initializer;
import net.vulkanmod.compat.CompatDetector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Bridges terrain lighting to an installed dynamic lights mod.
 * <p>
 * RyoamicLights (and its siblings) inject dynamic light into terrain by mixing into Sodium's
 * {@code me.jellysquid.mods.sodium.client.model.light} pipeline. VulkanMod carries its own fork of
 * that pipeline, which those mixins never touch, so blocks stayed at their vanilla light level while
 * entities lit up correctly. This bridge re-applies the injection on our side.
 * <p>
 * The injection happens at lightmap level ({@code LightDataAccess#getLightmap}), matching RyoamicLights'
 * own Sodium mixin, so the fractional block-light falloff is preserved instead of being truncated to
 * whole light levels. The block position is recorded per {@code ArrayLightDataCache#get} call into a
 * {@code ThreadLocal} because the chunk builders run on several threads concurrently.
 */
public final class DynamicLightsBridge {
    private static final String[][] CANDIDATES = {
            {"ryoamiclights", "org.thinkingstudio.ryoamiclights.RyoamicLights"},
            {"sodiumdynamiclights", "org.thinkingstudio.sodiumdynamiclights.SodiumDynamicLights"},
            {"lambdynlights", "dev.lambdaurora.lambdynlights.LambDynLights"},
    };

    private static final ThreadLocal<BlockPos.MutableBlockPos> POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    private static boolean initialized;
    private static volatile boolean available;
    private static volatile boolean active;

    private static Object instance;
    private static Object config;
    private static Method getLightmapWithDynamicLight;
    private static Method getLightSourcesCount;
    private static Method getDynamicLightsMode;
    private static Method isEnabled;
    private static Method updateAllMethod;

    private static boolean loggedFirstState;
    private static boolean loggedUpdateFailure;
    private static int samples;

    private DynamicLightsBridge() {
    }

    private static synchronized void init() {
        if (initialized)
            return;
        initialized = true;

        for (String[] candidate : CANDIDATES) {
            if (!CompatDetector.isModLoaded(candidate[0]))
                continue;

            try {
                Class<?> mainClass = Class.forName(candidate[1]);
                instance = mainClass.getMethod("get").invoke(null);
                if (instance == null)
                    continue;

                getLightmapWithDynamicLight = findMethod(mainClass, "getLightmapWithDynamicLight", BlockPos.class, int.class);
                getLightSourcesCount = mainClass.getMethod("getLightSourcesCount");

                // Optional: drives the auto-update. Missing on some forks, but the injection still works.
                try {
                    updateAllMethod = findMethod(mainClass, "updateAll", LevelRenderer.class);
                } catch (Throwable t) {
                    Initializer.LOGGER.warn("Dynamic lights auto-update unavailable for {}: {}", candidate[0], t.toString());
                }

                Field configField = mainClass.getField("config");
                config = configField.get(instance);
                getDynamicLightsMode = config.getClass().getMethod("getDynamicLightsMode");

                available = true;
                Initializer.LOGGER.info("Dynamic lights terrain bridge enabled for {} {}",
                        candidate[0], CompatDetector.getModVersion(candidate[0]));
                return;
            } catch (Throwable t) {
                Initializer.LOGGER.warn("Could not hook dynamic lights mod {}: {}", candidate[0], t);
            }
        }
    }

    /**
     * Resolves a method by name and arity, tolerating remapped Minecraft parameter types.
     */
    private static Method findMethod(Class<?> owner, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        try {
            return owner.getMethod(name, paramTypes);
        } catch (NoSuchMethodException ignored) {
            for (Method method : owner.getMethods()) {
                if (!method.getName().equals(name) || method.getParameterTypes().length != paramTypes.length)
                    continue;

                Class<?>[] params = method.getParameterTypes();
                boolean match = true;
                for (int i = 0; i < params.length; i++) {
                    if (!params[i].isAssignableFrom(paramTypes[i])) {
                        match = false;
                        break;
                    }
                }
                if (match)
                    return method;
            }
            throw new NoSuchMethodException(owner.getName() + '.' + name);
        }
    }

    /**
     * Snapshots whether dynamic light needs to be sampled. Called once per section build so the
     * per-block path stays a single volatile read.
     */
    public static void refresh() {
        if (!initialized)
            init();

        if (!available) {
            active = false;
            return;
        }

        try {
            Object mode = getDynamicLightsMode.invoke(config);
            if (isEnabled == null)
                isEnabled = mode.getClass().getMethod("isEnabled");

            // Gate on the mode only. getDynamicLightLevel already returns 0 when there are no
            // sources, and the source count is maintained on the client thread, so reading it here
            // from the builder thread could transiently report 0 and disable lighting outright.
            active = (boolean) isEnabled.invoke(mode);

            if (!loggedFirstState) {
                loggedFirstState = true;
                Initializer.LOGGER.info("Dynamic lights terrain bridge state: active={}, lightSources={}",
                        active, getLightSourcesCount.invoke(instance));
            }
        } catch (Throwable t) {
            disable(t);
        }
    }

    /**
     * Drives the dynamic-lights mod's per-frame update, which detects light-source movement and
     * schedules the affected chunks for rebuild. Forge normally reaches this through
     * {@code RenderLevelStageEvent}, dispatched at the end of the vanilla {@code renderSectionLayer};
     * VulkanMod cancels that method, so the event never fires and light sources never re-schedule.
     * Call this from VulkanMod's render loop instead.
     *
     * @param vanillaLevelRenderer the vanilla {@code LevelRenderer}, whose {@code setSectionDirty} is
     *                             redirected into VulkanMod's own chunk renderer
     */
    public static void updateAllDynamicLights(Object vanillaLevelRenderer) {
        if (!initialized)
            init();
        if (!available || updateAllMethod == null)
            return;

        try {
            updateAllMethod.invoke(instance, vanillaLevelRenderer);
        } catch (Throwable t) {
            if (!loggedUpdateFailure) {
                loggedUpdateFailure = true;
                Initializer.LOGGER.warn("Dynamic lights auto-update failed (auto-rebuild disabled)", t);
            }
        }
    }

    /**
     * Records the block position being queried by {@code ArrayLightDataCache#get}. Thread-local because
     * the chunk builders run on several threads concurrently.
     */
    public static void recordPos(int x, int y, int z) {
        if (active)
            POS.get().set(x, y, z);
    }

    /**
     * Raises a block's lightmap to the dynamic light level at its position, mirroring RyoamicLights'
     * {@code getLightmapWithDynamicLight}. Fully-opaque blocks are skipped to match their behavior:
     * light stops at the surface. The brighter of the two wins, and vanilla light is never reduced.
     */
    public static int applyToLightmap(int word, int lightmap) {
        if (!active)
            return lightmap;

        // unpackFO: fully-opaque/solid-render blocks never receive dynamic light.
        if ((word >>> 30 & 1) != 0)
            return lightmap;

        try {
            int result = (int) getLightmapWithDynamicLight.invoke(instance, POS.get(), lightmap);

            if (samples < 8 && result != lightmap) {
                samples++;
                Initializer.LOGGER.info("[DynamicLights] {} 0x{} -> 0x{}", POS.get().toShortString(),
                        Integer.toHexString(lightmap), Integer.toHexString(result));
            }

            return result;
        } catch (Throwable t) {
            disable(t);
            return lightmap;
        }
    }

    private static void disable(Throwable t) {
        available = false;
        active = false;
        Initializer.LOGGER.error("Disabling dynamic lights terrain bridge after failure", t);
    }
}
