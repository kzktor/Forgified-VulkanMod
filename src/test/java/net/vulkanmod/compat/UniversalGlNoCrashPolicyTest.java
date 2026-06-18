package net.vulkanmod.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversalGlNoCrashPolicyTest {
    private static final Pattern BRANCH_PATTERN = Pattern.compile("^\\s*(?:}\\s*)*(?:else\\s+)?(if|switch|case)\\b");
    private static final Pattern UNSUPPORTED_OPERATION_CREATION =
            Pattern.compile("\\bnew\\s+UnsupportedOperationException\\b");

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
                    String source = Files.readString(file);
                    if (containsUnsupportedOperationExceptionCreation(source)) {
                        violations.append(file).append(" throws UnsupportedOperationException\n");
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "GL compatibility must degrade by contract gap, not UnsupportedOperationException:\n" + violations);
    }

    @Test
    void modBranchMatcherRequiresBranchAndModTokenOnSameLine() {
        String source = """
                if (textureReady) {
                    bindTexture();
                }
                String modId = "flywheel";
                """;

        assertFalse(containsModTargetingBranch(source, "flywheel"));
    }

    @Test
    void modBranchMatcherDoesNotTreatIfInsideIdentifierAsBranch() {
        String source = """
                String diffuseTexture = "flywheel";
                """;

        assertFalse(containsModTargetingBranch(source, "flywheel"));
    }

    @Test
    void modBranchMatcherDetectsBranchLineWithModToken() {
        assertTrue(containsModTargetingBranch("if (modId.equals(\"flywheel\")) return;", "flywheel"));
    }

    @Test
    void modBranchMatcherDetectsMultilineBranchWithModToken() {
        String source = """
                if (ModList.get().isLoaded(
                        "iris")) {
                    enableShaderCompat();
                }
                """;

        assertTrue(containsModTargetingBranch(source, "iris"));
    }

    @Test
    void modBranchMatcherDoesNotTreatGlCreateNamesAsCreateModChecks() {
        assertFalse(containsModTargetingBranch("if (glCreateProgram != 0) return;", "create"));
    }

    @Test
    void modBranchMatcherIgnoresBlockCommentsAndTextBlocks() {
        String source = """
                /*
                if (ModList.get().isLoaded("iris")) {
                }
                */
                String script = \"""
                        if (ModList.get().isLoaded("iris")) {
                        }
                        \""";
                """;

        assertFalse(containsModTargetingBranch(source, "iris"));
    }

    @Test
    void unsupportedOperationScannerIgnoresCommentsAndLiterals() {
        String source = """
                // throw new UnsupportedOperationException();
                /* throw new UnsupportedOperationException(); */
                String message = "new UnsupportedOperationException";
                char marker = 'U';
                String block = \"""
                        new UnsupportedOperationException
                        \""";
                """;

        assertFalse(containsUnsupportedOperationExceptionCreation(source));
    }

    @Test
    void unsupportedOperationScannerDetectsExecutableCreation() {
        assertTrue(containsUnsupportedOperationExceptionCreation("throw new UnsupportedOperationException();"));
    }

    @Test
    void unsupportedOperationScannerDetectsExecutableCreationWithJavaWhitespace() {
        String source = """
                throw new
                        UnsupportedOperationException();
                """;

        assertTrue(containsUnsupportedOperationExceptionCreation(source));
    }

    @Test
    void unsupportedOperationScannerTreatsBlockCommentAsTokenSeparator() {
        assertTrue(containsUnsupportedOperationExceptionCreation("throw new/* gap */UnsupportedOperationException();"));
    }

    private static boolean containsModTargetingBranch(String source, String modId) {
        String lower = stripCommentsAndTextBlocksPreservingLiterals(source).toLowerCase(Locale.ROOT);
        String[] lines = lower.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            var branch = BRANCH_PATTERN.matcher(lines[i]);
            if (branch.find() && containsModToken(collectBranchStatement(lines, i, branch.group(1)), modId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsUnsupportedOperationExceptionCreation(String source) {
        return UNSUPPORTED_OPERATION_CREATION.matcher(stripNonExecutableText(source)).find();
    }

    private static String collectBranchStatement(String[] lines, int start, String branchKeyword) {
        StringBuilder statement = new StringBuilder();
        int parenDepth = 0;
        boolean sawOpenParen = false;
        int end = Math.min(lines.length, start + 12);

        for (int i = start; i < end; i++) {
            String line = lines[i];
            statement.append(line).append('\n');

            if ("case".equals(branchKeyword)) {
                if (line.contains(":") || line.contains("->")) {
                    return statement.toString();
                }
                continue;
            }

            for (int j = 0; j < line.length(); j++) {
                char c = line.charAt(j);
                if (c == '(') {
                    sawOpenParen = true;
                    parenDepth++;
                } else if (c == ')' && sawOpenParen) {
                    parenDepth--;
                }
            }

            if (sawOpenParen && parenDepth <= 0) {
                return statement.toString();
            }
            if (!sawOpenParen && (line.contains("{") || line.contains(";"))) {
                return statement.toString();
            }
        }
        return statement.toString();
    }

    private static boolean containsModToken(String source, String modId) {
        return source.contains("\"" + modId + "\"")
                || source.contains("'" + modId + "'")
                || source.contains("modid=" + modId)
                || source.contains("mod_id=" + modId)
                || source.contains("mod id " + modId);
    }

    private static String stripCommentsAndTextBlocksPreservingLiterals(String source) {
        StringBuilder stripped = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (c == '/' && next == '/') {
                i = appendUntilLineEnd(source, stripped, i + 2);
            } else if (c == '/' && next == '*') {
                i = appendUntilBlockCommentEnd(source, stripped, i + 2);
            } else if (c == '"' && i + 2 < source.length() && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                i = appendUntilTextBlockEnd(source, stripped, i + 3);
            } else if (c == '"' || c == '\'') {
                i = appendLiteral(source, stripped, i, c);
            } else {
                stripped.append(c);
            }
        }
        return stripped.toString();
    }

    private static String stripNonExecutableText(String source) {
        StringBuilder stripped = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (c == '/' && next == '/') {
                i = appendUntilLineEnd(source, stripped, i + 2);
            } else if (c == '/' && next == '*') {
                i = appendUntilBlockCommentEnd(source, stripped, i + 2);
            } else if (c == '"' && i + 2 < source.length() && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                i = appendUntilTextBlockEnd(source, stripped, i + 3);
            } else if (c == '"') {
                i = appendUntilLiteralEnd(source, stripped, i + 1, '"');
            } else if (c == '\'') {
                i = appendUntilLiteralEnd(source, stripped, i + 1, '\'');
            } else {
                stripped.append(c);
            }
        }
        return stripped.toString();
    }

    private static int appendUntilLineEnd(String source, StringBuilder stripped, int index) {
        for (int i = index; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\r' || c == '\n') {
                stripped.append(c);
                return i;
            }
        }
        return source.length();
    }

    private static int appendUntilBlockCommentEnd(String source, StringBuilder stripped, int index) {
        stripped.append(' ');
        for (int i = index; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\r' || c == '\n') {
                stripped.append(c);
            } else if (c == '*' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                return i + 1;
            }
        }
        return source.length();
    }

    private static int appendUntilTextBlockEnd(String source, StringBuilder stripped, int index) {
        for (int i = index; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\r' || c == '\n') {
                stripped.append(c);
            } else if (c == '"' && i + 2 < source.length() && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                return i + 2;
            }
        }
        return source.length();
    }

    private static int appendLiteral(String source, StringBuilder stripped, int index, char delimiter) {
        stripped.append(delimiter);
        boolean escaped = false;
        for (int i = index + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            stripped.append(c);
            if (c == '\r' || c == '\n') {
                return i;
            }
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == delimiter) {
                return i;
            }
        }
        return source.length();
    }

    private static int appendUntilLiteralEnd(String source, StringBuilder stripped, int index, char delimiter) {
        boolean escaped = false;
        for (int i = index; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\r' || c == '\n') {
                stripped.append(c);
                return i;
            }
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == delimiter) {
                return i;
            }
        }
        return source.length();
    }
}
