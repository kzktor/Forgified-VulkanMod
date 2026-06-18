package net.vulkanmod.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawContractRoutingTest {
    @Test
    void glDrawCallsRouteThroughGenericDrawContractName() throws Exception {
        for (Path path : List.of(
                Path.of("src/main/java/net/vulkanmod/compat/opengl/GlFunctionRegistry.java"),
                Path.of("src/main/java/net/vulkanmod/mixin/compatibility/gl/GL11M.java"),
                Path.of("src/main/java/net/vulkanmod/mixin/compatibility/gl/GL12M.java"),
                Path.of("src/main/java/net/vulkanmod/mixin/compatibility/gl/GL14M.java"),
                Path.of("src/main/java/net/vulkanmod/mixin/compatibility/gl/GL32M.java"))) {
            String source = Files.readString(path);
            assertTrue(source.contains("GlDrawContract"), path + " must route GL draw calls through GlDrawContract");
            assertFalse(source.contains("ExternalTerrainRenderBridge"), path + " must not name old compatibility bridges from universal GL call sites");
        }
    }

    @Test
    void drawContractIsUniversalAndContractKeyed() throws Exception {
        Path path = Path.of("src/main/java/net/vulkanmod/compat/opengl/GlDrawContract.java");
        assertTrue(Files.exists(path), "GlDrawContract must exist");
        String source = Files.readString(path);
        String lower = source.toLowerCase(Locale.ROOT);

        assertTrue(source.contains("GlEmulationLog.warnContractGap(\"draw_path\""));
        for (String forbidden : List.of("flywheel", "create", "distanthorizons", "iris", "sodium", "embeddium", "veil", "lodestone", "tensura")) {
            assertFalse(lower.contains(forbidden), "Universal draw contract must not name mod target: " + forbidden);
        }
    }
}
