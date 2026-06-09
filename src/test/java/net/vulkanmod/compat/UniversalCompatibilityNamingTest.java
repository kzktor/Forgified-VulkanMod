package net.vulkanmod.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversalCompatibilityNamingTest {
    private static final Path PRODUCTION_JAVA = Path.of("src/main/java");

    private static final List<Pattern> BANNED_PATH_TOKENS = List.of(
            Pattern.compile("(^|[/\\\\])distanthorizons([/\\\\]|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|[/\\\\])createcompat([/\\\\.]|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|[/\\\\])flywheelcompat([/\\\\.]|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|[/\\\\])pondercompat([/\\\\.]|$)", Pattern.CASE_INSENSITIVE)
    );

    private static final List<String> BANNED_IDENTIFIER_TOKENS = List.of(
            "DistantHorizons",
            "Distant Horizons",
            "DistantHorizonsCompat",
            "DistantHorizonsRenderBridge",
            "CreateCompat",
            "FlywheelCompat",
            "PonderCompat",
            "GlDh"
    );

    @Test
    void productionJavaUsesCapabilityNamesInsteadOfModNames() throws Exception {
        try (Stream<Path> paths = Files.walk(PRODUCTION_JAVA)) {
            List<String> violations = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(UniversalCompatibilityNamingTest::violationsFor)
                    .sorted()
                    .toList();

            assertTrue(violations.isEmpty(), () -> "Production Java contains mod-named compatibility code:\n"
                    + String.join("\n", violations));
        }
    }

    private static Stream<String> violationsFor(Path path) {
        String normalizedPath = path.toString().replace('\\', '/');
        Stream<String> pathViolations = BANNED_PATH_TOKENS.stream()
                .filter(pattern -> pattern.matcher(normalizedPath).find())
                .map(pattern -> normalizedPath + " matches path rule " + pattern.pattern());

        try {
            String source = Files.readString(path);
            Stream<String> identifierViolations = BANNED_IDENTIFIER_TOKENS.stream()
                    .filter(source::contains)
                    .map(token -> normalizedPath + " contains identifier token " + token);

            Stream<String> packageViolations = source.lines()
                    .filter(line -> line.startsWith("package ") || line.startsWith("import "))
                    .filter(line -> line.toLowerCase(Locale.ROOT).contains("distanthorizons"))
                    .map(line -> normalizedPath + " contains mod-named package/import: " + line.trim());

            return Stream.concat(pathViolations, Stream.concat(identifierViolations, packageViolations));
        } catch (Exception e) {
            return Stream.concat(pathViolations, Stream.of(normalizedPath + " could not be read: " + e.getMessage()));
        }
    }
}
