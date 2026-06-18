package net.vulkanmod.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversalGlNoCrashPolicyTest {
    private static final List<Path> GL_ROOTS = List.of(
            Path.of("src/main/java/net/vulkanmod/gl"),
            Path.of("src/main/java/net/vulkanmod/compat/opengl"),
            Path.of("src/main/java/net/vulkanmod/mixin/compatibility/gl"));

    @Test
    void universalGlCodeDoesNotIntroduceNewModNamedBranches() throws Exception {
        StringBuilder violations = new StringBuilder();
        for (Path root : GL_ROOTS) {
            if (!Files.exists(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
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
                        if (containsModTargetingBranch(source, forbidden)) {
                            violations.append(file).append(" contains mod-targeting branch for ").append(forbidden).append('\n');
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "Universal GL code must not branch by mod name:\n" + violations);
    }

    @Test
    void universalGlCodeUsesContractGapsInsteadOfUnsupportedOperationException() throws Exception {
        StringBuilder violations = new StringBuilder();
        for (Path root : GL_ROOTS) {
            if (!Files.exists(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String source = stripLineComments(Files.readString(file));
                    if (source.contains("new UnsupportedOperationException")) {
                        violations.append(file).append(" throws UnsupportedOperationException\n");
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "GL compatibility must degrade by contract gap, not UnsupportedOperationException:\n" + violations);
    }

    private static boolean containsModTargetingBranch(String source, String modId) {
        String lower = stripLineComments(source).toLowerCase(Locale.ROOT);
        for (String branch : List.of("if", "switch", "case")) {
            if (lower.contains(branch) && containsModToken(lower, modId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsModToken(String source, String modId) {
        return source.contains("\"" + modId + "\"")
                || source.contains("'" + modId + "'")
                || source.contains("modid=" + modId)
                || source.contains("mod_id=" + modId)
                || source.contains("mod id " + modId);
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
