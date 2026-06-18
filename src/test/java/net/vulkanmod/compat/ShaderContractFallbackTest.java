package net.vulkanmod.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderContractFallbackTest {
    @Test
    void shaderFallbacksAreLoggedByShaderContract() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/mixin/render/ShaderInstanceM.java"));

        assertTrue(source.contains("GlEmulationLog.warnContractGap(\"shader_conversion\", \"fallbackShader\""));
        assertTrue(source.contains("classifyShaderFailure"));
    }

    @Test
    void shaderFallbackCodeDoesNotSpecialCaseModNames() throws Exception {
        String source = stripLineComments(Files.readString(Path.of("src/main/java/net/vulkanmod/mixin/render/ShaderInstanceM.java"))).toLowerCase();

        for (String forbidden : new String[]{"flywheel", "create", "distanthorizons", "iris", "sodium", "veil", "lodestone", "tensura"}) {
            assertFalse(containsModBranch(source, forbidden), "Shader fallback must not branch on " + forbidden);
        }
    }

    private static boolean containsModBranch(String source, String modId) {
        for (String line : source.split("\\R")) {
            String trimmed = line.strip();
            if ((trimmed.startsWith("if") || trimmed.startsWith("switch") || trimmed.startsWith("case"))
                    && (trimmed.contains("\"" + modId + "\"") || trimmed.contains("'" + modId + "'"))) {
                return true;
            }
        }
        return false;
    }

    private static String stripLineComments(String source) {
        StringBuilder stripped = new StringBuilder(source.length());
        for (String line : source.split("\\R", -1)) {
            int commentStart = line.indexOf("//");
            stripped.append(commentStart >= 0 ? line.substring(0, commentStart) : line).append('\n');
        }
        return stripped.toString();
    }
}
