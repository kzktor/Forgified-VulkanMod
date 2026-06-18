package net.vulkanmod.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeSmokeScriptTest {
    @Test
    void runtimeSmokeScriptReportsGlContractFamilies() throws Exception {
        Path script = Path.of("scripts/gl-runtime-smoke-check.ps1");
        assertTrue(Files.exists(script), "runtime smoke classifier script must exist");
        String source = Files.readString(script);

        assertTrue(source.contains("provider"));
        assertTrue(source.contains("state_query"));
        assertTrue(source.contains("texture_image"));
        assertTrue(source.contains("framebuffer_readback"));
        assertTrue(source.contains("shader_conversion"));
        assertTrue(source.contains("draw_path"));
        assertTrue(source.contains("GL contract summary"));
    }
}
