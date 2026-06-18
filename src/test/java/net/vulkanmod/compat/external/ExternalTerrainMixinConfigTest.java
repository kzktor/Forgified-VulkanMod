package net.vulkanmod.compat.external;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalTerrainMixinConfigTest {
    @Test
    void externalTerrainMixinsAreNotRegisteredForGameplayJar() throws Exception {
        String mixinConfig = Files.readString(Path.of("src/main/resources/vulkanmod.mixins.json"));

        assertFalse(mixinConfig.contains("compatibility.distanthorizons."));
    }

    @Test
    void gl32CompatibilityRoutesExternalDrawCallsThroughUniversalContract() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/mixin/compatibility/gl/GL32M.java"));
        String contract = Files.readString(Path.of("src/main/java/net/vulkanmod/compat/opengl/GlDrawContract.java"));

        assertTrue(source.contains("GlDrawContract.drawElements"));
        assertTrue(source.contains("GlDrawContract.drawArrays"));
        assertTrue(contract.contains("ExternalTerrainRenderBridge.drawElements"));
        assertTrue(contract.contains("ExternalTerrainRenderBridge.drawArrays"));
    }

    @Test
    void gl32CompatibilityHooksCommonExternalRenderStateCalls() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/mixin/compatibility/gl/GL32M.java"));

        assertTrue(source.contains("VRenderSystem.setPolygonModeGL"));
        assertTrue(source.contains("Renderer.setViewport"));
        assertTrue(source.contains("VRenderSystem.clear(mask)"));
    }
}
