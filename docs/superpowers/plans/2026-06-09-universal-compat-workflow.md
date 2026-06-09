# Universal Compatibility Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove mod-named production Java compatibility files and replace them with universal renderer contracts plus capability-named adapters.

**Architecture:** Compatibility work becomes contract-owned: GL emulation, framebuffer semantics, shader translation, chunk model rendering, block-entity lifecycle, and external render paths. Existing mod-named files are migrated into capability-named packages and classes, while narrow loader-target strings remain internal implementation details when a third-party API must be intercepted.

**Tech Stack:** Java 21, NeoForge 1.21.1, Sponge Mixin, Gradle, JUnit 5, VulkanMod GL/Vulkan renderer.

---

## File Structure

### New Or Renamed Production Packages

- Create `src/main/java/net/vulkanmod/compat/capabilities/ExternalRenderPathOptions.java`
  - Owns capability-named runtime properties.
  - Preserves old user property fallback only inside this migration boundary.
- Create `src/main/java/net/vulkanmod/compat/external/ExternalRenderPathSupport.java`
  - Replaces mod-named compatibility policy checks.
  - Answers whether external render path hooks should apply.
- Create `src/main/java/net/vulkanmod/compat/external/ExternalTerrainRenderBridge.java`
  - Replaces the current external LOD bridge.
  - Owns buffer lifetime, matrix state, and render params for external terrain paths.
- Rename `src/main/java/net/vulkanmod/mixin/compatibility/distanthorizons/` to `src/main/java/net/vulkanmod/mixin/compatibility/external/`
  - File names become capability names such as `ExternalFramebufferMixin.java`.
  - Mixin target annotations may still target third-party classes by string where unavoidable.
- Rename `src/main/resources/assets/vulkanmod/shaders/basic/distant_horizons/` to `src/main/resources/assets/vulkanmod/shaders/basic/external_lod/`
  - Shader names and pipeline names become capability names.

### Existing Files To Modify

- `build.gradle`
  - Replace dev runtime properties with capability-named properties.
- `src/main/java/net/vulkanmod/mixin/MixinPlugin.java`
  - Replace mod-named package checks with capability package checks.
- `src/main/java/net/vulkanmod/render/PipelineManager.java`
  - Replace external LOD pipeline names and imports.
- `src/main/java/net/vulkanmod/vulkan/shader/Uniforms.java`
  - Replace external LOD uniform source imports and option checks.
- `src/main/java/net/vulkanmod/gl/GlBuffer.java`
  - Replace bridge import and deletion callback.
- `src/main/resources/vulkanmod.mixins.json`
  - Rename mixin entries to capability package/class names.
- Existing tests under `src/test/java/net/vulkanmod/compat/distanthorizons/`
  - Rename package/path to `src/test/java/net/vulkanmod/compat/external/`.

### New Tests

- Create `src/test/java/net/vulkanmod/compat/UniversalCompatibilityNamingTest.java`
  - Fails while production Java paths/classes contain banned third-party mod names.
- Create `src/test/java/net/vulkanmod/compat/external/ExternalRenderPathOptionsTest.java`
  - Verifies capability-named properties and old property fallback.
- Create `src/test/java/net/vulkanmod/compat/external/ExternalTerrainRenderBridgeTest.java`
  - Verifies bridge lifecycle hooks were renamed and call sites use generic names.
- Update source-scanning tests that currently include mod names in class names.

---

### Task 1: Add Universal Naming Guard

**Files:**
- Create: `src/test/java/net/vulkanmod/compat/UniversalCompatibilityNamingTest.java`

- [ ] **Step 1: Write the failing production Java naming test**

Create `src/test/java/net/vulkanmod/compat/UniversalCompatibilityNamingTest.java`:

```java
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
```

- [ ] **Step 2: Run the naming test and verify it fails**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.UniversalCompatibilityNamingTest
```

Expected: `BUILD FAILED` with violations for current production Java paths or identifiers that still contain mod-named compatibility code.

- [ ] **Step 3: Commit the red guard**

Run:

```powershell
git add src/test/java/net/vulkanmod/compat/UniversalCompatibilityNamingTest.java
git commit -m "test: add universal compatibility naming guard"
```

Expected: commit succeeds and the test remains failing until later migration tasks complete.

---

### Task 2: Introduce Capability-Named Runtime Options

**Files:**
- Create: `src/main/java/net/vulkanmod/compat/capabilities/ExternalRenderPathOptions.java`
- Modify: `src/main/java/net/vulkanmod/compat/RuntimeOptions.java`
- Create: `src/test/java/net/vulkanmod/compat/external/ExternalRenderPathOptionsTest.java`
- Modify: `build.gradle`

- [ ] **Step 1: Write the failing runtime option test**

Create `src/test/java/net/vulkanmod/compat/external/ExternalRenderPathOptionsTest.java`:

```java
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
```

- [ ] **Step 2: Run the runtime option test and verify it fails**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.external.ExternalRenderPathOptionsTest
```

Expected: compile fails because `ExternalRenderPathOptions` does not exist.

- [ ] **Step 3: Add capability option class**

Create `src/main/java/net/vulkanmod/compat/capabilities/ExternalRenderPathOptions.java`:

```java
package net.vulkanmod.compat.capabilities;

public final class ExternalRenderPathOptions {
    private static final String EXTERNAL_LOD = "vulkanmod.compat.externalLod";
    private static final String EXTERNAL_LOD_DRAW = "vulkanmod.compat.externalLod.draw";
    private static final String EXTERNAL_LOD_DEBUG_DRAW = "vulkanmod.compat.externalLod.debugDraw";

    private static final String LEGACY_EXTERNAL_LOD = "vulkanmod.compat.distanthorizons";
    private static final String LEGACY_EXTERNAL_LOD_DRAW = "vulkanmod.compat.distanthorizons.draw";
    private static final String LEGACY_EXTERNAL_LOD_DEBUG_DRAW = "vulkanmod.compat.distanthorizons.debugDraw";

    private ExternalRenderPathOptions() {
    }

    public static boolean externalLodEnabled() {
        return "on".equalsIgnoreCase(getProperty(EXTERNAL_LOD, LEGACY_EXTERNAL_LOD, "off"));
    }

    public static boolean externalLodDrawEnabled() {
        return Boolean.parseBoolean(getProperty(EXTERNAL_LOD_DRAW, LEGACY_EXTERNAL_LOD_DRAW, "true"));
    }

    public static boolean externalLodDebugDrawEnabled() {
        return Boolean.parseBoolean(getProperty(EXTERNAL_LOD_DEBUG_DRAW, LEGACY_EXTERNAL_LOD_DEBUG_DRAW, "false"));
    }

    private static String getProperty(String primary, String legacy, String defaultValue) {
        String value = System.getProperty(primary);
        if (value != null) {
            return value;
        }

        value = System.getProperty(legacy);
        return value != null ? value : defaultValue;
    }
}
```

- [ ] **Step 4: Add compatibility forwarding methods to RuntimeOptions**

Modify `src/main/java/net/vulkanmod/compat/RuntimeOptions.java` so external LOD methods delegate to `ExternalRenderPathOptions`. Use these exact method bodies where old methods currently exist:

```java
public static boolean externalLodEnabled() {
    return net.vulkanmod.compat.capabilities.ExternalRenderPathOptions.externalLodEnabled();
}

public static boolean externalLodDrawEnabled() {
    return net.vulkanmod.compat.capabilities.ExternalRenderPathOptions.externalLodDrawEnabled();
}

public static boolean externalLodDebugDrawEnabled() {
    return net.vulkanmod.compat.capabilities.ExternalRenderPathOptions.externalLodDebugDrawEnabled();
}
```

Do not remove old forwarding methods in this task. Task 3 updates call sites, and Task 8 removes any old forwarding methods whose names mention third-party mods.

- [ ] **Step 5: Rename Gradle dev run properties**

In `build.gradle`, replace:

```groovy
systemProperty 'vulkanmod.compat.distanthorizons', System.getProperty('vulkanmod.compat.distanthorizons', 'off')
systemProperty 'vulkanmod.compat.distanthorizons.draw', System.getProperty('vulkanmod.compat.distanthorizons.draw', 'true')
systemProperty 'vulkanmod.compat.distanthorizons.debugDraw', System.getProperty('vulkanmod.compat.distanthorizons.debugDraw', 'false')
```

with:

```groovy
systemProperty 'vulkanmod.compat.externalLod', System.getProperty('vulkanmod.compat.externalLod', 'off')
systemProperty 'vulkanmod.compat.externalLod.draw', System.getProperty('vulkanmod.compat.externalLod.draw', 'true')
systemProperty 'vulkanmod.compat.externalLod.debugDraw', System.getProperty('vulkanmod.compat.externalLod.debugDraw', 'false')
```

- [ ] **Step 6: Run the runtime option test and verify it passes**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.external.ExternalRenderPathOptionsTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit runtime option migration**

Run:

```powershell
git add build.gradle src/main/java/net/vulkanmod/compat/RuntimeOptions.java src/main/java/net/vulkanmod/compat/capabilities/ExternalRenderPathOptions.java src/test/java/net/vulkanmod/compat/external/ExternalRenderPathOptionsTest.java
git commit -m "refactor: use capability names for external render options"
```

Expected: commit succeeds.

---

### Task 3: Rename External Render Path Support Classes

**Files:**
- Rename: `src/main/java/net/vulkanmod/compat/distanthorizons/DistantHorizonsCompat.java` to `src/main/java/net/vulkanmod/compat/external/ExternalRenderPathSupport.java`
- Rename: `src/main/java/net/vulkanmod/compat/distanthorizons/DistantHorizonsRenderBridge.java` to `src/main/java/net/vulkanmod/compat/external/ExternalTerrainRenderBridge.java`
- Modify: `src/main/java/net/vulkanmod/render/PipelineManager.java`
- Modify: `src/main/java/net/vulkanmod/vulkan/shader/Uniforms.java`
- Modify: `src/main/java/net/vulkanmod/gl/GlBuffer.java`
- Modify: `src/main/java/net/vulkanmod/mixin/MixinPlugin.java`
- Create: `src/test/java/net/vulkanmod/compat/external/ExternalTerrainRenderBridgeTest.java`

- [ ] **Step 1: Write bridge source test before renaming**

Create `src/test/java/net/vulkanmod/compat/external/ExternalTerrainRenderBridgeTest.java`:

```java
package net.vulkanmod.compat.external;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalTerrainRenderBridgeTest {
    @Test
    void rendererCallSitesUseCapabilityNamedExternalTerrainBridge() throws Exception {
        String glBuffer = Files.readString(Path.of("src/main/java/net/vulkanmod/gl/GlBuffer.java"));
        String uniforms = Files.readString(Path.of("src/main/java/net/vulkanmod/vulkan/shader/Uniforms.java"));
        String pipelineManager = Files.readString(Path.of("src/main/java/net/vulkanmod/render/PipelineManager.java"));

        assertTrue(glBuffer.contains("ExternalTerrainRenderBridge.onBufferDeleted"));
        assertTrue(uniforms.contains("ExternalTerrainRenderBridge::getCombinedMatrix"));
        assertTrue(pipelineManager.contains("ExternalRenderPathSupport.shouldCreateExternalLodPipeline()"));

        assertFalse(glBuffer.contains("DistantHorizons"));
        assertFalse(uniforms.contains("DistantHorizons"));
        assertFalse(pipelineManager.contains("DistantHorizons"));
    }
}
```

- [ ] **Step 2: Run the bridge source test and verify it fails**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.external.ExternalTerrainRenderBridgeTest
```

Expected: test fails because call sites still use old mod-named bridge/support classes.

- [ ] **Step 3: Rename the support and bridge files**

Run:

```powershell
New-Item -ItemType Directory -Force -Path src/main/java/net/vulkanmod/compat/external | Out-Null
git mv src/main/java/net/vulkanmod/compat/distanthorizons/DistantHorizonsCompat.java src/main/java/net/vulkanmod/compat/external/ExternalRenderPathSupport.java
git mv src/main/java/net/vulkanmod/compat/distanthorizons/DistantHorizonsRenderBridge.java src/main/java/net/vulkanmod/compat/external/ExternalTerrainRenderBridge.java
```

Expected: files are moved into `compat/external`.

- [ ] **Step 4: Rename class declarations and public API**

In `ExternalRenderPathSupport.java`, set:

```java
package net.vulkanmod.compat.external;

import net.vulkanmod.compat.capabilities.ExternalRenderPathOptions;

public final class ExternalRenderPathSupport {
    private ExternalRenderPathSupport() {
    }

    public static boolean shouldApplyMixin() {
        return ExternalRenderPathOptions.externalLodEnabled();
    }

    public static boolean shouldCreateExternalLodPipeline() {
        return ExternalRenderPathOptions.externalLodEnabled();
    }

    public static boolean isExternalLodBridgeEnabled() {
        return ExternalRenderPathOptions.externalLodEnabled();
    }

    public static boolean shouldDrawExternalLod() {
        return ExternalRenderPathOptions.externalLodDrawEnabled();
    }

    public static boolean shouldDebugDrawExternalLod() {
        return ExternalRenderPathOptions.externalLodDebugDrawEnabled();
    }
}
```

If the old support class contains extra non-option behavior, keep that behavior and rename its methods to capability names using the same pattern.

- [ ] **Step 5: Rename bridge class declaration**

In `ExternalTerrainRenderBridge.java`, change the package and class declaration:

```java
package net.vulkanmod.compat.external;

public final class ExternalTerrainRenderBridge {
    private ExternalTerrainRenderBridge() {
    }
}
```

Preserve all existing fields and methods from the old bridge, but replace every self-reference to the old class name with `ExternalTerrainRenderBridge`.

- [ ] **Step 6: Update call-site imports and method names**

Replace imports:

```java
import net.vulkanmod.compat.distanthorizons.DistantHorizonsCompat;
import net.vulkanmod.compat.distanthorizons.DistantHorizonsRenderBridge;
```

with:

```java
import net.vulkanmod.compat.external.ExternalRenderPathSupport;
import net.vulkanmod.compat.external.ExternalTerrainRenderBridge;
```

Apply these method replacements:

```text
DistantHorizonsCompat.shouldCreateLodPipeline() -> ExternalRenderPathSupport.shouldCreateExternalLodPipeline()
DistantHorizonsCompat.isExperimentalBridgeEnabled() -> ExternalRenderPathSupport.isExternalLodBridgeEnabled()
DistantHorizonsCompat.shouldApplyMixin() -> ExternalRenderPathSupport.shouldApplyMixin()
DistantHorizonsRenderBridge. -> ExternalTerrainRenderBridge.
```

- [ ] **Step 7: Run bridge source test and compile**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.external.ExternalTerrainRenderBridgeTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit support and bridge rename**

Run:

```powershell
git add src/main/java/net/vulkanmod/compat src/main/java/net/vulkanmod/render/PipelineManager.java src/main/java/net/vulkanmod/vulkan/shader/Uniforms.java src/main/java/net/vulkanmod/gl/GlBuffer.java src/main/java/net/vulkanmod/mixin/MixinPlugin.java src/test/java/net/vulkanmod/compat/external/ExternalTerrainRenderBridgeTest.java
git commit -m "refactor: rename external render bridge by capability"
```

Expected: commit succeeds.

---

### Task 4: Rename External Render Mixins To Capability Names

**Files:**
- Rename all files under `src/main/java/net/vulkanmod/mixin/compatibility/distanthorizons/`
- Modify: `src/main/resources/vulkanmod.mixins.json`
- Modify: `src/main/java/net/vulkanmod/mixin/MixinPlugin.java`
- Test: `src/test/java/net/vulkanmod/compat/UniversalCompatibilityNamingTest.java`

- [ ] **Step 1: Move the mixin package**

Run:

```powershell
New-Item -ItemType Directory -Force -Path src/main/java/net/vulkanmod/mixin/compatibility/external | Out-Null
Get-ChildItem src/main/java/net/vulkanmod/mixin/compatibility/distanthorizons -Filter *.java | ForEach-Object {
    git mv $_.FullName ("src/main/java/net/vulkanmod/mixin/compatibility/external/" + $_.Name)
}
```

Expected: files move to `mixin/compatibility/external`.

- [ ] **Step 2: Rename files whose class names contain mod-specific abbreviations**

Run these exact moves for known class names:

```powershell
git mv src/main/java/net/vulkanmod/mixin/compatibility/external/ClientApiMixin.java src/main/java/net/vulkanmod/mixin/compatibility/external/ExternalRendererClientApiMixin.java
git mv src/main/java/net/vulkanmod/mixin/compatibility/external/GlDhApplyShaderMixin.java src/main/java/net/vulkanmod/mixin/compatibility/external/ExternalApplyShaderMixin.java
git mv src/main/java/net/vulkanmod/mixin/compatibility/external/GlDhColorTextureMixin.java src/main/java/net/vulkanmod/mixin/compatibility/external/ExternalColorTextureMixin.java
git mv src/main/java/net/vulkanmod/mixin/compatibility/external/GlDhDebugWireframeRendererMixin.java src/main/java/net/vulkanmod/mixin/compatibility/external/ExternalDebugWireframeRendererMixin.java
git mv src/main/java/net/vulkanmod/mixin/compatibility/external/GlDhDepthTextureMixin.java src/main/java/net/vulkanmod/mixin/compatibility/external/ExternalDepthTextureMixin.java
git mv src/main/java/net/vulkanmod/mixin/compatibility/external/GlDhFramebufferMixin.java src/main/java/net/vulkanmod/mixin/compatibility/external/ExternalFramebufferMixin.java
git mv src/main/java/net/vulkanmod/mixin/compatibility/external/GlDhMetaRendererMixin.java src/main/java/net/vulkanmod/mixin/compatibility/external/ExternalMetaRendererMixin.java
git mv src/main/java/net/vulkanmod/mixin/compatibility/external/GlDhPostProcessingRendererMixin.java src/main/java/net/vulkanmod/mixin/compatibility/external/ExternalPostProcessingRendererMixin.java
git mv src/main/java/net/vulkanmod/mixin/compatibility/external/GlDhPostProcessingShaderMixin.java src/main/java/net/vulkanmod/mixin/compatibility/external/ExternalPostProcessingShaderMixin.java
git mv src/main/java/net/vulkanmod/mixin/compatibility/external/GlDhTerrainShaderProgramMixin.java src/main/java/net/vulkanmod/mixin/compatibility/external/ExternalTerrainShaderProgramMixin.java
git mv src/main/java/net/vulkanmod/mixin/compatibility/external/NeoforgeClientProxyMixin.java src/main/java/net/vulkanmod/mixin/compatibility/external/ExternalRendererClientProxyMixin.java
```

Expected: filenames no longer contain `Dh` or old mod names.

- [ ] **Step 3: Update package declarations**

For every file in `src/main/java/net/vulkanmod/mixin/compatibility/external`, replace:

```java
package net.vulkanmod.mixin.compatibility.distanthorizons;
```

with:

```java
package net.vulkanmod.mixin.compatibility.external;
```

- [ ] **Step 4: Update class declarations to match filenames**

For each renamed file, make the public class name match the file name. Example:

```java
public class ExternalFramebufferMixin {
}
```

Keep all annotations, injection methods, and target strings unchanged except imports renamed in Task 3.

- [ ] **Step 5: Update mixin config entries**

In `src/main/resources/vulkanmod.mixins.json`, replace every entry beginning with:

```json
"compatibility.distanthorizons.
```

with:

```json
"compatibility.external.
```

Also replace class suffixes to match renamed files, for example:

```json
"compatibility.external.ExternalFramebufferMixin"
```

- [ ] **Step 6: Update MixinPlugin package filter**

In `src/main/java/net/vulkanmod/mixin/MixinPlugin.java`, replace:

```java
if (mixinClassName.startsWith("net.vulkanmod.mixin.compatibility.distanthorizons.")) {
    return DistantHorizonsCompat.shouldApplyMixin() && isModLoaded("distanthorizons");
}
```

with:

```java
if (mixinClassName.startsWith("net.vulkanmod.mixin.compatibility.external.")) {
    return ExternalRenderPathSupport.shouldApplyMixin() && isExternalRendererLoaded();
}
```

Add this helper in the same class:

```java
private boolean isExternalRendererLoaded() {
    return isModLoaded("distanthorizons");
}
```

The helper name is capability-based; the loader ID remains an internal detection value.

- [ ] **Step 7: Run compile and naming guard**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.UniversalCompatibilityNamingTest
```

Expected: if target strings are the only remaining third-party names, the naming guard passes because it checks paths, package/import names, and public identifier tokens rather than unavoidable target strings.

- [ ] **Step 8: Commit mixin rename**

Run:

```powershell
git add src/main/java/net/vulkanmod/mixin src/main/resources/vulkanmod.mixins.json src/test/java/net/vulkanmod/compat/UniversalCompatibilityNamingTest.java
git commit -m "refactor: rename external render mixins by capability"
```

Expected: commit succeeds.

---

### Task 5: Rename External LOD Shader Pipeline Assets

**Files:**
- Rename: `src/main/resources/assets/vulkanmod/shaders/basic/distant_horizons/` to `src/main/resources/assets/vulkanmod/shaders/basic/external_lod/`
- Modify: `src/main/java/net/vulkanmod/render/PipelineManager.java`
- Modify: `src/test/java/net/vulkanmod/render/PipelineManagerDistantHorizonsTest.java`

- [ ] **Step 1: Rename pipeline manager test**

Run:

```powershell
git mv src/test/java/net/vulkanmod/render/PipelineManagerDistantHorizonsTest.java src/test/java/net/vulkanmod/render/ExternalLodPipelineManagerTest.java
```

Set the test class to:

```java
package net.vulkanmod.render;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalLodPipelineManagerTest {
    @Test
    void pipelineManagerUsesCapabilityNamedExternalLodPipeline() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/render/PipelineManager.java"));

        assertTrue(source.contains("ExternalRenderPathSupport.shouldCreateExternalLodPipeline()"));
        assertTrue(source.contains("external_lod"));
        assertFalse(source.contains("distant_horizons"));
        assertFalse(source.contains("DistantHorizons"));
    }
}
```

- [ ] **Step 2: Run the renamed pipeline test and verify it fails**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.render.ExternalLodPipelineManagerTest
```

Expected: test fails until shader path and pipeline names are renamed.

- [ ] **Step 3: Move shader directory**

Run:

```powershell
New-Item -ItemType Directory -Force -Path src/main/resources/assets/vulkanmod/shaders/basic | Out-Null
git mv src/main/resources/assets/vulkanmod/shaders/basic/distant_horizons src/main/resources/assets/vulkanmod/shaders/basic/external_lod
```

Expected: shader assets move to `external_lod`.

- [ ] **Step 4: Rename pipeline fields and shader path**

In `src/main/java/net/vulkanmod/render/PipelineManager.java`, replace:

```java
private static GraphicsPipeline distantHorizonsLodPipeline;
```

with:

```java
private static GraphicsPipeline externalLodPipeline;
```

Replace pipeline creation:

```java
if (ExternalRenderPathSupport.shouldCreateExternalLodPipeline()) {
    externalLodPipeline = createPipeline("external_lod", "lod", "lod", CustomVertexFormat.DISTANT_HORIZONS_LOD);
}
```

Rename the getter:

```java
public static GraphicsPipeline getExternalLodPipeline() {
    return externalLodPipeline;
}
```

Keep `CustomVertexFormat.DISTANT_HORIZONS_LOD` until Task 8, where vertex format names are migrated.

- [ ] **Step 5: Update bridge and mixin call sites for pipeline getter**

Replace:

```java
PipelineManager.getDistantHorizonsLodPipeline()
```

with:

```java
PipelineManager.getExternalLodPipeline()
```

- [ ] **Step 6: Run the pipeline test**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.render.ExternalLodPipelineManagerTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit shader pipeline rename**

Run:

```powershell
git add src/main/java/net/vulkanmod/render/PipelineManager.java src/main/resources/assets/vulkanmod/shaders/basic src/test/java/net/vulkanmod/render
git commit -m "refactor: rename external lod pipeline assets"
```

Expected: commit succeeds.

---

### Task 6: Rename External LOD Uniform And Vertex Format Tests

**Files:**
- Rename: `src/test/java/net/vulkanmod/vulkan/shader/UniformsDistantHorizonsTest.java` to `src/test/java/net/vulkanmod/vulkan/shader/ExternalLodUniformsTest.java`
- Modify: `src/main/java/net/vulkanmod/vulkan/shader/Uniforms.java`
- Modify: `src/main/java/net/vulkanmod/render/vertex/CustomVertexFormat.java`

- [ ] **Step 1: Rename uniforms test**

Run:

```powershell
git mv src/test/java/net/vulkanmod/vulkan/shader/UniformsDistantHorizonsTest.java src/test/java/net/vulkanmod/vulkan/shader/ExternalLodUniformsTest.java
```

Set the test class to:

```java
package net.vulkanmod.vulkan.shader;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalLodUniformsTest {
    @Test
    void externalLodUniformsUseCapabilityNamedBridge() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/vulkan/shader/Uniforms.java"));

        assertTrue(source.contains("ExternalRenderPathSupport.isExternalLodBridgeEnabled()"));
        assertTrue(source.contains("ExternalTerrainRenderBridge::getCombinedMatrix"));
        assertTrue(source.contains("ExternalTerrainRenderBridge::getModelOffsetAndYOffset"));
        assertTrue(source.contains("ExternalTerrainRenderBridge::getRenderParams"));
        assertFalse(source.contains("DistantHorizons"));
    }
}
```

- [ ] **Step 2: Rename vertex format constant**

In `src/main/java/net/vulkanmod/render/vertex/CustomVertexFormat.java`, replace:

```java
DISTANT_HORIZONS_LOD
```

with:

```java
EXTERNAL_LOD
```

Update every reference in production and tests.

- [ ] **Step 3: Update Uniforms imports and method calls**

In `src/main/java/net/vulkanmod/vulkan/shader/Uniforms.java`, use:

```java
import net.vulkanmod.compat.external.ExternalRenderPathSupport;
import net.vulkanmod.compat.external.ExternalTerrainRenderBridge;
```

Replace bridge gates and method refs with:

```java
if (ExternalRenderPathSupport.isExternalLodBridgeEnabled()) {
    mat4f_uniformMap.put("DH_CombinedMatrix", ExternalTerrainRenderBridge::getCombinedMatrix);
}
```

and:

```java
if (ExternalRenderPathSupport.isExternalLodBridgeEnabled()) {
    vec4f_uniformMap.put("DH_ModelOffsetAndYOffset", ExternalTerrainRenderBridge::getModelOffsetAndYOffset);
    vec4f_uniformMap.put("DH_RenderParams", ExternalTerrainRenderBridge::getRenderParams);
}
```

The uniform names remain unchanged because they are part of the external shader ABI.

- [ ] **Step 4: Run uniforms test and naming guard**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.vulkan.shader.ExternalLodUniformsTest --tests net.vulkanmod.compat.UniversalCompatibilityNamingTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit uniform and vertex format rename**

Run:

```powershell
git add src/main/java/net/vulkanmod/vulkan/shader/Uniforms.java src/main/java/net/vulkanmod/render/vertex/CustomVertexFormat.java src/test/java/net/vulkanmod/vulkan/shader src/test/java/net/vulkanmod/compat/UniversalCompatibilityNamingTest.java
git commit -m "refactor: rename external lod uniforms and vertex format"
```

Expected: commit succeeds.

---

### Task 7: Rename Remaining Mod-Named Tests And Test Packages

**Files:**
- Rename: `src/test/java/net/vulkanmod/compat/distanthorizons/` to `src/test/java/net/vulkanmod/compat/external/`
- Modify moved test classes to capability names.
- Test: `src/test/java/net/vulkanmod/compat/UniversalCompatibilityNamingTest.java`

- [ ] **Step 1: Move test package**

Run:

```powershell
New-Item -ItemType Directory -Force -Path src/test/java/net/vulkanmod/compat/external | Out-Null
Get-ChildItem src/test/java/net/vulkanmod/compat/distanthorizons -Filter *.java | ForEach-Object {
    git mv $_.FullName ("src/test/java/net/vulkanmod/compat/external/" + $_.Name)
}
```

Expected: tests move to `compat/external`.

- [ ] **Step 2: Rename test classes**

Rename class files that contain old mod names:

```powershell
Get-ChildItem src/test/java/net/vulkanmod/compat/external -Filter *DistantHorizons*.java | ForEach-Object {
    $newName = $_.Name.Replace("DistantHorizons", "ExternalRenderPath")
    git mv $_.FullName ("src/test/java/net/vulkanmod/compat/external/" + $newName)
}
```

Then update package declarations in moved files:

```java
package net.vulkanmod.compat.external;
```

- [ ] **Step 3: Rename test class declarations**

For each moved test file, make the class name match the filename. Example:

```java
class ExternalRenderPathBridgeTest {
}
```

Replace test assertions that reference old production class names with the new capability class names introduced in earlier tasks.

- [ ] **Step 4: Run all external compatibility tests**

Run:

```powershell
.\gradlew.bat test --tests "net.vulkanmod.compat.external.*"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit test package rename**

Run:

```powershell
git add src/test/java/net/vulkanmod/compat
git commit -m "test: rename external render path tests by capability"
```

Expected: commit succeeds.

---

### Task 8: Remove Compatibility Shims And Final Mod-Named Java Identifiers

**Files:**
- Modify: `src/main/java/net/vulkanmod/compat/RuntimeOptions.java`
- Modify: `src/main/java/net/vulkanmod/compat/capabilities/ExternalRenderPathOptions.java`
- Modify: every file reported by `UniversalCompatibilityNamingTest`
- Test: `src/test/java/net/vulkanmod/compat/UniversalCompatibilityNamingTest.java`

- [ ] **Step 1: Run naming guard and capture remaining violations**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.UniversalCompatibilityNamingTest
```

Expected: either `BUILD SUCCESSFUL` or a short list of remaining production Java violations.

- [ ] **Step 2: Remove old mod-named forwarding methods**

In `RuntimeOptions.java`, remove methods whose names mention old third-party mods after all call sites use capability methods.

Keep generic methods only:

```java
public static boolean externalLodEnabled() {
    return net.vulkanmod.compat.capabilities.ExternalRenderPathOptions.externalLodEnabled();
}

public static boolean externalLodDrawEnabled() {
    return net.vulkanmod.compat.capabilities.ExternalRenderPathOptions.externalLodDrawEnabled();
}

public static boolean externalLodDebugDrawEnabled() {
    return net.vulkanmod.compat.capabilities.ExternalRenderPathOptions.externalLodDebugDrawEnabled();
}
```

- [ ] **Step 3: Keep old property fallback inside the capability boundary**

Keep these fallback constants inside `ExternalRenderPathOptions.java` during this migration:

```java
private static final String LEGACY_EXTERNAL_LOD = "vulkanmod.compat.distanthorizons";
private static final String LEGACY_EXTERNAL_LOD_DRAW = "vulkanmod.compat.distanthorizons.draw";
private static final String LEGACY_EXTERNAL_LOD_DEBUG_DRAW = "vulkanmod.compat.distanthorizons.debugDraw";
```

The old property strings are allowed only in this capability option class. They are migration shims for user launch profiles, not production Java package, class, method, or field names.

- [ ] **Step 4: Rename any remaining identifiers reported by the guard**

For every violation reported by `UniversalCompatibilityNamingTest`, rename production identifiers to capability terms. Use this mapping:

```text
DistantHorizons -> ExternalLod
DistantHorizonsRenderBridge -> ExternalTerrainRenderBridge
DistantHorizonsCompat -> ExternalRenderPathSupport
GlDh -> External
distant_horizons -> external_lod
```

- [ ] **Step 5: Run naming guard**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.UniversalCompatibilityNamingTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit final naming cleanup**

Run:

```powershell
git add src/main/java src/test/java build.gradle src/main/resources
git commit -m "refactor: remove mod-named production java compatibility identifiers"
```

Expected: commit succeeds.

---

### Task 9: Update Workflow Documentation Index And Historical Notes

**Files:**
- Modify: `docs/superpowers/specs/2026-06-09-universal-compat-workflow-design.md`
- Create: `docs/superpowers/specs/2026-06-09-universal-compat-inventory.md`

- [ ] **Step 1: Create migration inventory document**

Create `docs/superpowers/specs/2026-06-09-universal-compat-inventory.md`:

```markdown
# Universal Compatibility Migration Inventory

## Production Java Migration Status

| Old Area | Universal Contract | New Area | Status |
| --- | --- | --- | --- |
| Mod-named support policy | External render path capability | `net.vulkanmod.compat.external.ExternalRenderPathSupport` | migrated |
| Mod-named render bridge | External terrain render bridge | `net.vulkanmod.compat.external.ExternalTerrainRenderBridge` | migrated |
| Mod-named mixin package | External renderer mixin hooks | `net.vulkanmod.mixin.compatibility.external` | migrated |
| Mod-named LOD shaders | External LOD shader assets | `assets/vulkanmod/shaders/basic/external_lod` | migrated |

## Enforcement

`net.vulkanmod.compat.UniversalCompatibilityNamingTest` prevents new mod-named production Java paths, packages, imports, or public identifier tokens.

## Allowed Internal References

Mixin target strings and legacy property fallback strings may reference third-party identifiers only when required to target an external API or migrate existing user profiles. Public production Java names must remain capability-named.
```

- [ ] **Step 2: Add implementation link to design spec**

Append this section to `docs/superpowers/specs/2026-06-09-universal-compat-workflow-design.md`:

```markdown
## Implementation Tracking

Implementation plan: `docs/superpowers/plans/2026-06-09-universal-compat-workflow.md`

Migration inventory: `docs/superpowers/specs/2026-06-09-universal-compat-inventory.md`
```

- [ ] **Step 3: Commit documentation updates**

Run:

```powershell
git add docs/superpowers/specs/2026-06-09-universal-compat-workflow-design.md docs/superpowers/specs/2026-06-09-universal-compat-inventory.md
git commit -m "docs: track universal compatibility migration"
```

Expected: commit succeeds.

---

### Task 10: Full Verification And Deployment

**Files:**
- Verify all changed files.
- Deploy: `C:\Users\Administrator\AppData\Roaming\ModrinthApp\profiles\RE_ Spellerium\mods\VulkanMod_1.21.1-0.4.9-dev.jar`

- [ ] **Step 1: Run full build**

Run:

```powershell
.\gradlew.bat build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run touched-file whitespace check**

Run:

```powershell
git diff --check -- src/main/java src/test/java src/main/resources build.gradle docs/superpowers
```

Expected: no whitespace errors for files changed in this migration.

- [ ] **Step 3: Verify no mod-named production Java identifiers remain**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.UniversalCompatibilityNamingTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Deploy rebuilt jar**

Run:

```powershell
$src = "C:\Users\Administrator\Documents\Vulkan\VulkanMod\build\libs\VulkanMod_1.21.1-0.4.9-dev.jar"
$dst = "C:\Users\Administrator\AppData\Roaming\ModrinthApp\profiles\RE_ Spellerium\mods\VulkanMod_1.21.1-0.4.9-dev.jar"
Copy-Item -LiteralPath $src -Destination $dst -Force
$srcHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $src).Hash
$dstHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $dst).Hash
[pscustomobject]@{
    Source = $src
    Destination = $dst
    SourceSHA256 = $srcHash
    DestinationSHA256 = $dstHash
    Same = ($srcHash -eq $dstHash)
}
```

Expected: `Same : True`.

- [ ] **Step 5: Commit deployment-ready migration state**

If the migration changed files after the last task commit, run:

```powershell
git status --short
git add src/main/java src/test/java src/main/resources build.gradle docs/superpowers
git commit -m "chore: complete universal compatibility migration"
```

Expected: either no commit is needed because previous task commits captured everything, or the final commit succeeds.
