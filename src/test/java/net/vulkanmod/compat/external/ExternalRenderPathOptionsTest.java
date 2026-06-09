package net.vulkanmod.compat.external;

import net.vulkanmod.compat.capabilities.ExternalRenderPathOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalRenderPathOptionsTest {
    @AfterEach
    void clearProperties() {
        System.clearProperty("vulkanmod.compat.externalLod");
        System.clearProperty("vulkanmod.compat.externalLod.draw");
        System.clearProperty("vulkanmod.compat.externalLod.debugDraw");
        System.clearProperty("vulkanmod.compat.distanthorizons");
        System.clearProperty("vulkanmod.compat.distanthorizons.draw");
        System.clearProperty("vulkanmod.compat.distanthorizons.debugDraw");
    }

    @Test
    void capabilityNamedExternalLodOptionControlsExternalRenderPath() {
        System.setProperty("vulkanmod.compat.externalLod", "on");
        System.setProperty("vulkanmod.compat.externalLod.draw", "false");
        System.setProperty("vulkanmod.compat.externalLod.debugDraw", "true");

        assertTrue(ExternalRenderPathOptions.externalLodEnabled());
        assertFalse(ExternalRenderPathOptions.externalLodDrawEnabled());
        assertTrue(ExternalRenderPathOptions.externalLodDebugDrawEnabled());
    }

    @Test
    void oldExternalLodPropertyNamesStillWorkDuringMigration() {
        System.setProperty("vulkanmod.compat.distanthorizons", "on");
        System.setProperty("vulkanmod.compat.distanthorizons.draw", "false");
        System.setProperty("vulkanmod.compat.distanthorizons.debugDraw", "true");

        assertTrue(ExternalRenderPathOptions.externalLodEnabled());
        assertFalse(ExternalRenderPathOptions.externalLodDrawEnabled());
        assertTrue(ExternalRenderPathOptions.externalLodDebugDrawEnabled());
    }
}
