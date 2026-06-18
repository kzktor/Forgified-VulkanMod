package net.vulkanmod.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversalGlContractLedgerTest {
    private static final Path LEDGER = Path.of("src/main/resources/assets/vulkanmod/compat/gl_contracts.properties");

    @Test
    void ledgerExistsAndDefinesRequiredUniversalContractFamilies() throws Exception {
        assertTrue(Files.exists(LEDGER), "GL contract ledger must exist");
        String ledger = Files.readString(LEDGER);

        for (String family : List.of(
                "provider",
                "state_query",
                "object_lifetime",
                "texture_image",
                "framebuffer_readback",
                "shader_conversion",
                "draw_path",
                "runtime_smoke",
                "performance")) {
            assertTrue(ledger.contains("family." + family + "="), "Missing GL contract family: " + family);
        }
    }

    @Test
    void ledgerDoesNotNameThirdPartyModsAsFixTargets() throws Exception {
        String ledger = Files.readString(LEDGER).toLowerCase();

        for (String forbidden : List.of(
                "create",
                "flywheel",
                "distanthorizons",
                "iris",
                "sodium",
                "embeddium",
                "veil",
                "lodestone",
                "tensura")) {
            assertFalse(ledger.contains(forbidden), "Ledger must describe GL contracts, not mod targets: " + forbidden);
        }
    }
}
