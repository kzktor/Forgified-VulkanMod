package net.vulkanmod.gl;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-time diagnostics for tolerated GL emulation gaps. External mods issue GL
 * calls the Vulkan backend has no analog for; those must degrade (no-op or a
 * spec-shaped default) instead of crashing, with a single log line per call
 * site so repeated per-frame calls cannot flood the log.
 */
public final class GlEmulationLog {
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static final Set<String> CONTRACT_FAMILIES = Set.of(
            "provider",
            "state_query",
            "object_lifetime",
            "texture_image",
            "framebuffer_readback",
            "shader_conversion",
            "draw_path",
            "runtime_smoke",
            "performance");

    // Holder defers the log4j dependency so unit tests can exercise the
    // emulation classes on a classpath without a logging backend.
    private static final class Log {
        static final org.apache.logging.log4j.Logger IMPL =
                org.apache.logging.log4j.LogManager.getLogger("VulkanMod-GlEmulation");
    }

    private GlEmulationLog() {
    }

    public static void warnContractGap(String family, String operation, String message, Object... args) {
        warnOnce(contractGapKey(family, operation), message, args);
    }

    private static String contractGapKey(String family, String operation) {
        if (family == null || !CONTRACT_FAMILIES.contains(family)) {
            throw new IllegalArgumentException("Unknown GL contract family: " + family);
        }
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("GL contract operation must not be blank");
        }
        return family + "." + operation;
    }

    static void warnOnce(String key, String message, Object... args) {
        if (!REPORTED.add(key)) {
            return;
        }

        try {
            Log.IMPL.warn(message, args);
        } catch (Throwable ignored) {
        }
    }
}
