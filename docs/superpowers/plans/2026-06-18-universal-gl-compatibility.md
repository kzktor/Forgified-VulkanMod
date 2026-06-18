# Universal GL Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build VulkanMod toward universal OpenGL/LWJGL compatibility without adding mod-specific adapters, backends, or mod-ID behavior branches.

**Architecture:** Treat OpenGL as the compatibility product surface. Work proceeds by GL contract family: provider coverage, state/query, object lifetime, textures, framebuffers/readback, shaders, draw paths, runtime smoke evidence, and performance guardrails. Every production change must land in generic GL, shader, texture, framebuffer, draw, or renderer code and be protected by contract tests.

**Tech Stack:** Java 21, NeoForge 1.21.1, LWJGL OpenGL/Vulkan/shaderc/VMA 3.3.3, JUnit 5, Gradle, VulkanMod renderer.

---

## Scope Boundary

This plan intentionally rejects per-mod integration work. Do not create production code under names such as `flywheel`, `create`, `distanthorizons`, `iris`, `sodium`, `veil`, `lodestone`, or `tensura`. Real modpacks may be used only as black-box GL callers that expose contract failures.

Existing capability-named production code may remain while this plan is executed, but new fixes for this goal must target universal GL contracts. If a worker finds a failure that appears to require a mod-specific backend, stop and reclassify the failure as the GL contract that backend expected.

## File Structure

- Create `src/main/resources/assets/vulkanmod/compat/gl_contracts.properties`: source-controlled contract ledger used by tests and progress reporting.
- Create `src/test/java/net/vulkanmod/compat/UniversalGlContractLedgerTest.java`: verifies the ledger exists, is mod-name free, and covers required contract families.
- Create `src/test/java/net/vulkanmod/compat/UniversalGlNoCrashPolicyTest.java`: scans GL compatibility code for new hard-fail patterns and mod-specific branches.
- Modify `src/main/java/net/vulkanmod/gl/GlEmulationLog.java`: add contract-keyed gap reporting helpers while preserving once-only logging.
- Create `src/test/java/net/vulkanmod/gl/GlEmulationLogContractTest.java`: tests contract-keyed logging helpers without requiring Minecraft runtime.
- Modify `src/main/java/net/vulkanmod/compat/opengl/GlFunctionRegistry.java`: classify provider functions by contract family and route unimplemented/unsafe paths through contract gap helpers.
- Modify `src/test/java/net/vulkanmod/compat/GlFunctionRegistryTest.java`: assert provider entries are complete and contract-classified.
- Modify `src/main/java/net/vulkanmod/gl/GlTexture.java`: complete generic texture/image behaviors in small slices, beginning with provider-path compressed texture data and 3D/array metadata correctness.
- Modify `src/main/java/net/vulkanmod/mixin/compatibility/gl/GL13M.java`: thread texture data through generic texture APIs where LWJGL overloads provide data.
- Modify `src/main/java/net/vulkanmod/mixin/compatibility/gl/GL30M.java`: turn framebuffer blit/readback gaps into explicit framebuffer contracts.
- Modify `src/main/java/net/vulkanmod/mixin/compatibility/gl/GL11M.java`: prepare readback and query behavior through generic framebuffer/readback helpers.
- Modify `src/main/java/net/vulkanmod/mixin/render/ShaderInstanceM.java`: classify shader fallback causes by GLSL contract feature rather than external shader owner.
- Modify `src/main/java/net/vulkanmod/vulkan/shader/parser/GlslConverter.java`: add generic shader normalization only when backed by fixtures.
- Create `src/test/java/net/vulkanmod/compat/ShaderContractFallbackTest.java`: contract fixtures for GLSL conversion/fallback behavior.
- Create `src/test/java/net/vulkanmod/compat/DrawContractRoutingTest.java`: asserts generic GL draw paths route through GL-named contract code.
- Create `scripts/gl-runtime-smoke-check.ps1`: summarizes runtime logs for GL contract failures, provider count, shader fallbacks, and hard crashes.

## Task 1: Contract Ledger And Progress Categories

**Files:**
- Create: `src/main/resources/assets/vulkanmod/compat/gl_contracts.properties`
- Create: `src/test/java/net/vulkanmod/compat/UniversalGlContractLedgerTest.java`
- Modify: `docs/superpowers/specs/2026-06-18-universal-gl-compatibility-design.md`

- [ ] **Step 1: Write the failing ledger test**

Create `src/test/java/net/vulkanmod/compat/UniversalGlContractLedgerTest.java`:

```java
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
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.UniversalGlContractLedgerTest
```

Expected: `ledgerExistsAndDefinesRequiredUniversalContractFamilies` fails because `gl_contracts.properties` does not exist.

- [ ] **Step 3: Add the contract ledger**

Create `src/main/resources/assets/vulkanmod/compat/gl_contracts.properties`:

```properties
family.provider=LWJGL OpenGL entrypoints resolve to VulkanMod providers
family.state_query=GL state changes and glGet/glIs queries are coherent after Vulkan handoff
family.object_lifetime=GL object names can be generated, bound, queried, deleted, and safely ignored when unknown
family.texture_image=GL texture/image allocation, upload, subupload, sampling metadata, and readback semantics
family.framebuffer_readback=GL framebuffer/renderbuffer attachment, clear, blit, completeness, and pixel readback semantics
family.shader_conversion=GLSL shader JSON, source, include, uniform, sampler, attribute, output, and fallback semantics
family.draw_path=GL array, indexed, range, multi-draw, instanced, indirect, immediate, and DSA draw semantics
family.runtime_smoke=Black-box modpack runtime logs are classified by GL contract family
family.performance=Compatibility code avoids hot-path stalls, repeated allocations, and noisy per-frame diagnostics
```

- [ ] **Step 4: Add a spec pointer to the ledger**

Append this paragraph to the `Progress Model` section of `docs/superpowers/specs/2026-06-18-universal-gl-compatibility-design.md`:

```markdown
The canonical local ledger for these categories is `src/main/resources/assets/vulkanmod/compat/gl_contracts.properties`. Tests should fail if a new progress category is invented outside the ledger or if the ledger starts naming specific mods as fix targets.
```

- [ ] **Step 5: Run the test and verify it passes**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.UniversalGlContractLedgerTest
```

Expected: 2 tests pass.

- [ ] **Step 6: Commit**

Run:

```powershell
git add src/main/resources/assets/vulkanmod/compat/gl_contracts.properties src/test/java/net/vulkanmod/compat/UniversalGlContractLedgerTest.java docs/superpowers/specs/2026-06-18-universal-gl-compatibility-design.md
git commit -m "test: add universal GL contract ledger"
```

## Task 2: No Mod-Specific GL Policy Test

**Files:**
- Create: `src/test/java/net/vulkanmod/compat/UniversalGlNoCrashPolicyTest.java`

- [ ] **Step 1: Write the failing policy test**

Create `src/test/java/net/vulkanmod/compat/UniversalGlNoCrashPolicyTest.java`:

```java
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
                    String source = Files.readString(file).toLowerCase(Locale.ROOT);
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
                        if (source.contains(forbidden)) {
                            violations.append(file).append(" contains ").append(forbidden).append('\n');
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
                    if (source.contains("new UnsupportedOperationException")) {
                        violations.append(file).append(" throws UnsupportedOperationException\n");
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "GL compatibility must degrade by contract gap, not UnsupportedOperationException:\n" + violations);
    }
}
```

- [ ] **Step 2: Run the test and verify current failures**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.UniversalGlNoCrashPolicyTest
```

Expected: if the scan fails, it names exact files that still mention a mod name or hard unsupported exception inside universal GL code. Treat this as inventory, not a production fix yet.

- [ ] **Step 3: If the scan fails on comments only, narrow the scan to executable code**

Replace the source read line in both tests:

```java
String source = Files.readString(file).toLowerCase(Locale.ROOT);
```

with:

```java
String source = stripLineComments(Files.readString(file)).toLowerCase(Locale.ROOT);
```

Add this helper at the end of the class:

```java
private static String stripLineComments(String source) {
    StringBuilder stripped = new StringBuilder(source.length());
    for (String line : source.split("\\R", -1)) {
        int commentStart = line.indexOf("//");
        stripped.append(commentStart >= 0 ? line.substring(0, commentStart) : line).append('\n');
    }
    return stripped.toString();
}
```

Make the same replacement in the second test before checking for `UnsupportedOperationException`.

- [ ] **Step 4: Run the policy test**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.UniversalGlNoCrashPolicyTest
```

Expected: pass, or fail with executable-code violations that must be handled before continuing.

- [ ] **Step 5: Commit**

Run:

```powershell
git add src/test/java/net/vulkanmod/compat/UniversalGlNoCrashPolicyTest.java
git commit -m "test: enforce universal GL compatibility policy"
```

## Task 3: Contract-Keyed GL Gap Logging

**Files:**
- Modify: `src/main/java/net/vulkanmod/gl/GlEmulationLog.java`
- Create: `src/test/java/net/vulkanmod/gl/GlEmulationLogContractTest.java`

- [ ] **Step 1: Inspect the current logger**

Run:

```powershell
Get-Content src\main\java\net\vulkanmod\gl\GlEmulationLog.java
```

Expected: existing once-only warning behavior is visible.

- [ ] **Step 2: Write the failing logger test**

Create `src/test/java/net/vulkanmod/gl/GlEmulationLogContractTest.java`:

```java
package net.vulkanmod.gl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlEmulationLogContractTest {
    @Test
    void contractGapKeyIncludesFamilyAndOperation() throws Exception {
        Method method = GlEmulationLog.class.getDeclaredMethod("contractGapKey", String.class, String.class);
        method.setAccessible(true);

        assertEquals("texture_image.glTexImage3D", method.invoke(null, "texture_image", "glTexImage3D"));
        assertEquals("framebuffer_readback.glReadPixels", method.invoke(null, "framebuffer_readback", "glReadPixels"));
    }

    @Test
    void contractGapLoggingAcceptsStableFamilyAndOperationNames() {
        assertDoesNotThrow(() -> GlEmulationLog.warnContractGap(
                "shader_conversion",
                "externalShaderFallback",
                "Generic shader fallback used for unsupported GLSL contract"));
    }

    @Test
    void contractGapRejectsModNamedFamilies() throws Exception {
        Method method = GlEmulationLog.class.getDeclaredMethod("contractGapKey", String.class, String.class);
        method.setAccessible(true);

        try {
            method.invoke(null, "flywheel", "backend");
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception.getCause();
            assertTrue(cause instanceof IllegalArgumentException);
            assertTrue(cause.getMessage().contains("GL contract family"));
        }
    }
}
```

- [ ] **Step 3: Run the test and verify it fails**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.gl.GlEmulationLogContractTest
```

Expected: compilation fails because `warnContractGap` and `contractGapKey` do not exist.

- [ ] **Step 4: Add contract gap helpers**

In `src/main/java/net/vulkanmod/gl/GlEmulationLog.java`, add this code inside the class:

```java
private static final java.util.Set<String> CONTRACT_FAMILIES = java.util.Set.of(
        "provider",
        "state_query",
        "object_lifetime",
        "texture_image",
        "framebuffer_readback",
        "shader_conversion",
        "draw_path",
        "runtime_smoke",
        "performance");

public static void warnContractGap(String family, String operation, String message, Object... args) {
    warnOnce(contractGapKey(family, operation), message, args);
}

private static String contractGapKey(String family, String operation) {
    if (!CONTRACT_FAMILIES.contains(family)) {
        throw new IllegalArgumentException("Unknown GL contract family: " + family);
    }
    if (operation == null || operation.isBlank()) {
        throw new IllegalArgumentException("GL contract operation must not be blank");
    }
    return family + "." + operation;
}
```

Use fully qualified `java.util.Set` if the file currently has no imports.

- [ ] **Step 5: Run the logger test**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.gl.GlEmulationLogContractTest
```

Expected: 3 tests pass.

- [ ] **Step 6: Commit**

Run:

```powershell
git add src/main/java/net/vulkanmod/gl/GlEmulationLog.java src/test/java/net/vulkanmod/gl/GlEmulationLogContractTest.java
git commit -m "feat: add contract-keyed GL gap logging"
```

## Task 4: Provider Function Contract Classification

**Files:**
- Modify: `src/main/java/net/vulkanmod/compat/opengl/GlFunctionRegistry.java`
- Modify: `src/test/java/net/vulkanmod/compat/GlFunctionRegistryTest.java`

- [ ] **Step 1: Add a failing registry classification test**

Append this test to `src/test/java/net/vulkanmod/compat/GlFunctionRegistryTest.java`:

```java
@Test
void registeredFunctionsExposeUniversalContractFamilies() throws Exception {
    Method allFunctions = GlFunctionRegistry.class.getDeclaredMethod("allFunctionsForTesting");
    allFunctions.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, ?> functions = (Map<String, ?>) allFunctions.invoke(null);

    Method contractFamily = GlFunctionRegistry.class.getDeclaredMethod("contractFamilyForTesting", String.class);
    contractFamily.setAccessible(true);

    for (String name : functions.keySet()) {
        String family = (String) contractFamily.invoke(null, name);
        assertNotNull(family, "Missing GL contract family for " + name);
        assertFalse(family.isBlank(), "Blank GL contract family for " + name);
    }
}
```

Add missing imports if needed:

```java
import java.lang.reflect.Method;
import java.util.Map;
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.GlFunctionRegistryTest.registeredFunctionsExposeUniversalContractFamilies
```

Expected: compilation fails because `contractFamilyForTesting` does not exist.

- [ ] **Step 3: Add a contract-family classifier**

In `src/main/java/net/vulkanmod/compat/opengl/GlFunctionRegistry.java`, add this helper inside the class:

```java
static String contractFamilyForTesting(String functionName) {
    return contractFamily(functionName);
}

private static String contractFamily(String functionName) {
    if (functionName == null || functionName.isBlank()) {
        return "provider";
    }
    String name = functionName.toLowerCase(java.util.Locale.ROOT);
    if (name.contains("shader") || name.contains("program") || name.contains("uniform") || name.contains("attrib")) {
        return "shader_conversion";
    }
    if (name.contains("tex") || name.contains("sampler") || name.contains("image") || name.contains("mipmap")) {
        return "texture_image";
    }
    if (name.contains("framebuffer") || name.contains("renderbuffer") || name.contains("readpixels")
            || name.contains("blit") || name.contains("clear")) {
        return "framebuffer_readback";
    }
    if (name.contains("draw") || name.contains("begin") || name.contains("end") || name.contains("calllist")
            || name.contains("arrayelement")) {
        return "draw_path";
    }
    if (name.contains("buffer") || name.contains("vertexarray") || name.contains("query") || name.contains("sync")
            || name.contains("fence") || name.contains("list")) {
        return "object_lifetime";
    }
    if (name.startsWith("glget") || name.startsWith("glis") || name.contains("enable") || name.contains("disable")
            || name.contains("blend") || name.contains("depth") || name.contains("stencil") || name.contains("scissor")
            || name.contains("viewport") || name.contains("cull") || name.contains("polygon")) {
        return "state_query";
    }
    return "provider";
}
```

- [ ] **Step 4: Run the focused test**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.GlFunctionRegistryTest.registeredFunctionsExposeUniversalContractFamilies
```

Expected: the new test passes.

- [ ] **Step 5: Run the existing registry suite**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.GlFunctionRegistryTest --tests net.vulkanmod.compat.GlCapabilitiesFallbackTest
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit**

Run:

```powershell
git add src/main/java/net/vulkanmod/compat/opengl/GlFunctionRegistry.java src/test/java/net/vulkanmod/compat/GlFunctionRegistryTest.java
git commit -m "test: classify GL provider functions by contract"
```

## Task 5: Compressed Texture Provider Path Parity

**Files:**
- Modify: `src/main/java/net/vulkanmod/compat/opengl/GlFunctionRegistry.java`
- Modify: `src/main/java/net/vulkanmod/gl/GlTexture.java`
- Modify: `src/test/java/net/vulkanmod/gl/GlTextureTest.java`
- Modify: `src/test/java/net/vulkanmod/compat/GlFunctionRegistryTest.java`

- [ ] **Step 1: Add a failing test that provider compressed uploads keep data**

Append this test to `src/test/java/net/vulkanmod/compat/GlFunctionRegistryTest.java`:

```java
@Test
void compressedTexImage2DProviderPathPassesPayloadToTextureLayer() throws Exception {
    String source = Files.readString(Path.of("src/main/java/net/vulkanmod/compat/opengl/GlFunctionRegistry.java"));

    assertTrue(source.contains("glCompressedTexImage2D"));
    assertTrue(source.contains("MemoryUtil.memByteBuffer"));
    assertTrue(source.contains("GlTexture.compressedTexImage2D(target, level, internalFormat, width, height, border, dataBuffer)"),
            "provider path must preserve compressed payload instead of recording metadata only");
}
```

Add missing imports if needed:

```java
import java.nio.file.Files;
import java.nio.file.Path;
```

- [ ] **Step 2: Run the failing test**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.GlFunctionRegistryTest.compressedTexImage2DProviderPathPassesPayloadToTextureLayer
```

Expected: fails because the provider path discards the compressed payload or lacks the exact data-buffer call.

- [ ] **Step 3: Implement provider data threading**

In `src/main/java/net/vulkanmod/compat/opengl/GlFunctionRegistry.java`, update the `glCompressedTexImage2D` handler so the pointer overload reads the data when `imageSize > 0` and `data != 0`.

Use this implementation shape inside the handler:

```java
int target = argI(args, 0);
int level = argI(args, 1);
int internalFormat = argI(args, 2);
int width = argI(args, 3);
int height = argI(args, 4);
int border = argI(args, 5);
int imageSize = argI(args, 6);
long data = argP(args, 7);
java.nio.ByteBuffer dataBuffer = data != 0L && imageSize > 0 ? MemoryUtil.memByteBuffer(data, imageSize) : null;
GlTexture.compressedTexImage2D(target, level, internalFormat, width, height, border, dataBuffer);
```

Keep the existing metadata-only overload for signatures that do not provide a payload.

- [ ] **Step 4: Run the focused test**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.GlFunctionRegistryTest.compressedTexImage2DProviderPathPassesPayloadToTextureLayer
```

Expected: pass.

- [ ] **Step 5: Run texture and registry tests**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.gl.GlTextureTest --tests net.vulkanmod.compat.GlFunctionRegistryTest
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit**

Run:

```powershell
git add src/main/java/net/vulkanmod/compat/opengl/GlFunctionRegistry.java src/test/java/net/vulkanmod/compat/GlFunctionRegistryTest.java
git commit -m "fix: preserve compressed texture payloads in GL provider path"
```

## Task 6: 3D And Array Texture Contract Slice

**Files:**
- Modify: `src/main/java/net/vulkanmod/gl/GlTexture.java`
- Modify: `src/main/java/net/vulkanmod/mixin/compatibility/gl/GL13M.java`
- Create: `src/test/java/net/vulkanmod/gl/GlTextureDimensionalContractTest.java`

- [ ] **Step 1: Write failing metadata contract tests**

Create `src/test/java/net/vulkanmod/gl/GlTextureDimensionalContractTest.java`:

```java
package net.vulkanmod.gl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GlTextureDimensionalContractTest {
    @Test
    void texImage3DRecordsDepthAndTargetAsDimensionalMetadata() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/gl/GlTexture.java"));

        assertTrue(source.contains("recordDimensionalMetadata(target, level, internalFormat, width, height, depth, border, format, type)"));
        assertTrue(source.contains("depth"));
        assertTrue(source.contains("GL_TEXTURE_2D_ARRAY"));
        assertTrue(source.contains("GL_TEXTURE_3D"));
    }

    @Test
    void gl13DataOverloadsForwardByteBufferDataToDimensionalTextureLayer() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/mixin/compatibility/gl/GL13M.java"));

        assertTrue(source.contains("GlTexture.texImage3D(target, level, internalformat, width, height, depth, border, format, type, data)"));
    }
}
```

- [ ] **Step 2: Run the failing test**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.gl.GlTextureDimensionalContractTest
```

Expected: fails because dimensional metadata/data overloads are incomplete.

- [ ] **Step 3: Add a dimensional metadata helper**

In `src/main/java/net/vulkanmod/gl/GlTexture.java`, add an overload and helper next to the existing `texImage3D` method:

```java
public static void texImage3D(int target, int level, int internalFormat, int width, int height, int depth,
                              int border, int format, int type, java.nio.ByteBuffer data) {
    recordDimensionalMetadata(target, level, internalFormat, width, height, depth, border, format, type);
    if (data != null) {
        GlEmulationLog.warnContractGap("texture_image", "glTexImage3D.data",
                "glTexImage3D data upload is not allocated yet; recorded dimensional metadata for target 0x{}",
                Integer.toHexString(target));
    }
}

private static void recordDimensionalMetadata(int target, int level, int internalFormat, int width, int height,
                                              int depth, int border, int format, int type) {
    if (!isDimensionalTextureTarget(target)) {
        GlEmulationLog.warnContractGap("texture_image", "glTexImage3D.target",
                "glTexImage3D target 0x{} is not emulated; upload ignored", Integer.toHexString(target));
        return;
    }
    texImage3D(target, level, internalFormat, width, height, depth, border, format, type);
}
```

If the existing metadata-only `texImage3D` already contains target checks, keep that logic and make the helper call the existing metadata path once. Avoid recursive calls between the overload and helper.

- [ ] **Step 4: Forward GL13 ByteBuffer overload data**

In `src/main/java/net/vulkanmod/mixin/compatibility/gl/GL13M.java`, change the ByteBuffer `glTexImage3D` overload body to:

```java
GlTexture.texImage3D(target, level, internalformat, width, height, depth, border, format, type, data);
```

- [ ] **Step 5: Run the dimensional tests**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.gl.GlTextureDimensionalContractTest
```

Expected: 2 tests pass.

- [ ] **Step 6: Run texture suite**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.gl.GlTextureTest --tests net.vulkanmod.gl.GlTextureDimensionalContractTest
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit**

Run:

```powershell
git add src/main/java/net/vulkanmod/gl/GlTexture.java src/main/java/net/vulkanmod/mixin/compatibility/gl/GL13M.java src/test/java/net/vulkanmod/gl/GlTextureDimensionalContractTest.java
git commit -m "fix: preserve dimensional texture contract metadata"
```

## Task 7: Framebuffer Readback Contract Classification

**Files:**
- Modify: `src/main/java/net/vulkanmod/mixin/compatibility/gl/GL11M.java`
- Modify: `src/main/java/net/vulkanmod/mixin/compatibility/gl/GL30M.java`
- Create: `src/test/java/net/vulkanmod/compat/gl/FramebufferReadbackContractTest.java`

- [ ] **Step 1: Write failing framebuffer contract tests**

Create `src/test/java/net/vulkanmod/compat/gl/FramebufferReadbackContractTest.java`:

```java
package net.vulkanmod.compat.gl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FramebufferReadbackContractTest {
    @Test
    void readPixelsFallbackIsReportedAsFramebufferReadbackContractGap() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/mixin/compatibility/gl/GL11M.java"));

        assertTrue(source.contains("GlEmulationLog.warnContractGap(\"framebuffer_readback\", \"glReadPixels\""));
    }

    @Test
    void framebufferBlitFallbackIsReportedAsFramebufferReadbackContractGap() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/mixin/compatibility/gl/GL30M.java"));

        assertTrue(source.contains("GlEmulationLog.warnContractGap(\"framebuffer_readback\", \"glBlitFramebuffer\""));
    }
}
```

- [ ] **Step 2: Run the failing test**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.gl.FramebufferReadbackContractTest
```

Expected: fails because the readback/blit fallbacks are not contract-keyed.

- [ ] **Step 3: Add readback contract warning**

In every `glReadPixels` fallback in `src/main/java/net/vulkanmod/mixin/compatibility/gl/GL11M.java`, add this before the zero-fill or no-op:

```java
GlEmulationLog.warnContractGap("framebuffer_readback", "glReadPixels",
        "glReadPixels real Vulkan readback is not implemented yet; returning safe fallback data");
```

Add the import if needed:

```java
import net.vulkanmod.gl.GlEmulationLog;
```

- [ ] **Step 4: Add blit contract warning**

In `src/main/java/net/vulkanmod/mixin/compatibility/gl/GL30M.java`, add this in the `glBlitFramebuffer` fallback path:

```java
GlEmulationLog.warnContractGap("framebuffer_readback", "glBlitFramebuffer",
        "glBlitFramebuffer Vulkan image blit is not implemented yet; dropping blit safely");
```

Add the import if needed:

```java
import net.vulkanmod.gl.GlEmulationLog;
```

- [ ] **Step 5: Run framebuffer contract test**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.gl.FramebufferReadbackContractTest
```

Expected: 2 tests pass.

- [ ] **Step 6: Run GL compatibility tests**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.GlCompatibilityMixinTest --tests net.vulkanmod.compat.gl.FramebufferReadbackContractTest
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit**

Run:

```powershell
git add src/main/java/net/vulkanmod/mixin/compatibility/gl/GL11M.java src/main/java/net/vulkanmod/mixin/compatibility/gl/GL30M.java src/test/java/net/vulkanmod/compat/gl/FramebufferReadbackContractTest.java
git commit -m "test: classify framebuffer readback gaps"
```

## Task 8: Generic Shader Fallback Classification

**Files:**
- Modify: `src/main/java/net/vulkanmod/mixin/render/ShaderInstanceM.java`
- Create: `src/test/java/net/vulkanmod/compat/ShaderContractFallbackTest.java`

- [ ] **Step 1: Write failing shader fallback test**

Create `src/test/java/net/vulkanmod/compat/ShaderContractFallbackTest.java`:

```java
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
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/mixin/render/ShaderInstanceM.java")).toLowerCase();

        for (String forbidden : new String[]{"flywheel", "create", "distanthorizons", "iris", "sodium", "veil", "lodestone", "tensura"}) {
            assertFalse(source.contains("if") && source.contains(forbidden), "Shader fallback must not branch on " + forbidden);
        }
    }
}
```

- [ ] **Step 2: Run the failing test**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.ShaderContractFallbackTest
```

Expected: fails because shader fallback is not contract-keyed.

- [ ] **Step 3: Add shader fallback classification helper**

In `src/main/java/net/vulkanmod/mixin/render/ShaderInstanceM.java`, add:

```java
private static String classifyShaderFailure(Throwable throwable) {
    String message = throwable != null && throwable.getMessage() != null ? throwable.getMessage().toLowerCase(java.util.Locale.ROOT) : "";
    if (message.contains("include")) return "include";
    if (message.contains("uniform")) return "uniform";
    if (message.contains("sampler")) return "sampler";
    if (message.contains("attribute") || message.contains("attrib")) return "attribute";
    if (message.contains("syntax")) return "syntax";
    return "unknown";
}
```

- [ ] **Step 4: Log fallback through contract gap helper**

Where `createExternalFallbackShader(format)` is called after shader creation or conversion failure, add:

```java
GlEmulationLog.warnContractGap("shader_conversion", "fallbackShader",
        "Using generic fallback shader for unsupported GLSL contract {}; shader={}",
        classifyShaderFailure(e), this.name);
```

Add import if needed:

```java
import net.vulkanmod.gl.GlEmulationLog;
```

- [ ] **Step 5: Run shader fallback tests**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.ShaderContractFallbackTest --tests net.vulkanmod.compat.ShaderResourceLookupTest
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit**

Run:

```powershell
git add src/main/java/net/vulkanmod/mixin/render/ShaderInstanceM.java src/test/java/net/vulkanmod/compat/ShaderContractFallbackTest.java
git commit -m "test: classify shader fallback contracts"
```

## Task 9: Introduce A Universal GL Draw Contract Boundary

**Files:**
- Create: `src/main/java/net/vulkanmod/compat/opengl/GlDrawOptions.java`
- Create: `src/main/java/net/vulkanmod/compat/opengl/GlDrawContract.java`
- Modify: `src/main/java/net/vulkanmod/compat/opengl/GlFunctionRegistry.java`
- Modify: `src/main/java/net/vulkanmod/mixin/compatibility/gl/GL11M.java`
- Modify: `src/main/java/net/vulkanmod/mixin/compatibility/gl/GL12M.java`
- Modify: `src/main/java/net/vulkanmod/mixin/compatibility/gl/GL14M.java`
- Modify: `src/main/java/net/vulkanmod/mixin/compatibility/gl/GL32M.java`
- Create: `src/test/java/net/vulkanmod/compat/DrawContractRoutingTest.java`

- [ ] **Step 1: Write failing draw routing test**

Create `src/test/java/net/vulkanmod/compat/DrawContractRoutingTest.java`:

```java
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
```

- [ ] **Step 2: Run the failing test**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.DrawContractRoutingTest
```

Expected: fails because GL draw call sites do not yet route through `GlDrawContract`.

- [ ] **Step 3: Create generic draw options**

Create `src/main/java/net/vulkanmod/compat/opengl/GlDrawOptions.java`:

```java
package net.vulkanmod.compat.opengl;

public final class GlDrawOptions {
    private static final String PRESERVE_LEGACY_PROPERTY = "vulkanmod.compat.glDraw.preserveLegacyBridge";
    private static final String DEBUG_PROPERTY = "vulkanmod.compat.glDraw.debug";

    private GlDrawOptions() {
    }

    public static boolean shouldPreserveLegacyBridge() {
        return Boolean.parseBoolean(System.getProperty(PRESERVE_LEGACY_PROPERTY, "true"));
    }

    public static boolean debugDrawContracts() {
        return Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
    }
}
```

- [ ] **Step 4: Create the GL draw contract boundary**

Create `src/main/java/net/vulkanmod/compat/opengl/GlDrawContract.java`:

```java
package net.vulkanmod.compat.opengl;

import net.vulkanmod.compat.external.ExternalTerrainRenderBridge;
import net.vulkanmod.gl.GlEmulationLog;

public final class GlDrawContract {
    private GlDrawContract() {
    }

    public static void drawArrays(int mode, int first, int count) {
        if (GlDrawOptions.shouldPreserveLegacyBridge()) {
            ExternalTerrainRenderBridge.drawArrays(mode, first, count);
            return;
        }

        warnUnsupported("glDrawArrays", mode, count);
    }

    public static void drawElements(int mode, int count, int type, long indices) {
        if (GlDrawOptions.shouldPreserveLegacyBridge()) {
            ExternalTerrainRenderBridge.drawElements(mode, count, type, indices);
            return;
        }

        warnUnsupported("glDrawElements", mode, count);
    }

    public static void onBufferDeleted(int id) {
        if (GlDrawOptions.shouldPreserveLegacyBridge()) {
            ExternalTerrainRenderBridge.onBufferDeleted(id);
        }
    }

    private static void warnUnsupported(String function, int mode, int count) {
        GlEmulationLog.warnContractGap("draw_path", function,
                "{} generic Vulkan submission is not implemented for mode 0x{} count {}; dropping draw safely",
                function, Integer.toHexString(mode), count);
    }
}
```

This step deliberately does not copy old bridge internals into the GL contract. The old bridge remains a preservation path for existing behavior while new universal work attaches to the GL-named contract surface.

- [ ] **Step 5: Replace draw call imports and call sites**

In each listed file, replace:

```java
import net.vulkanmod.compat.external.ExternalTerrainRenderBridge;
```

with:

```java
import net.vulkanmod.compat.opengl.GlDrawContract;
```

Replace call sites:

```java
ExternalTerrainRenderBridge.drawArrays(
ExternalTerrainRenderBridge.drawElements(
```

with:

```java
GlDrawContract.drawArrays(
GlDrawContract.drawElements(
```

If a file only calls `ExternalTerrainRenderBridge.onBufferDeleted`, replace it with:

```java
GlDrawContract.onBufferDeleted(
```

- [ ] **Step 6: Run draw routing test**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.DrawContractRoutingTest
```

Expected: pass.

- [ ] **Step 7: Run GL draw-adjacent tests**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.DrawContractRoutingTest --tests net.vulkanmod.compat.GlFunctionRegistryTest --tests net.vulkanmod.compat.GlCallCoverageTest
```

Expected: all selected tests pass.

- [ ] **Step 8: Commit**

Run:

```powershell
git add src/main/java/net/vulkanmod/compat/opengl/GlDrawOptions.java src/main/java/net/vulkanmod/compat/opengl/GlDrawContract.java src/main/java/net/vulkanmod/compat/opengl/GlFunctionRegistry.java src/main/java/net/vulkanmod/mixin/compatibility/gl/GL11M.java src/main/java/net/vulkanmod/mixin/compatibility/gl/GL12M.java src/main/java/net/vulkanmod/mixin/compatibility/gl/GL14M.java src/main/java/net/vulkanmod/mixin/compatibility/gl/GL32M.java src/test/java/net/vulkanmod/compat/DrawContractRoutingTest.java
git commit -m "refactor: route GL draws through universal contract"
```

## Task 10: Runtime GL Smoke Log Classifier

**Files:**
- Create: `scripts/gl-runtime-smoke-check.ps1`
- Create: `src/test/java/net/vulkanmod/compat/RuntimeSmokeScriptTest.java`

- [ ] **Step 1: Write failing script test**

Create `src/test/java/net/vulkanmod/compat/RuntimeSmokeScriptTest.java`:

```java
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
```

- [ ] **Step 2: Run the failing test**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.RuntimeSmokeScriptTest
```

Expected: fails because the script does not exist.

- [ ] **Step 3: Create the smoke classifier script**

Create `scripts/gl-runtime-smoke-check.ps1`:

```powershell
param(
    [string]$LogPath = "run/logs/latest.log"
)

if (-not (Test-Path -LiteralPath $LogPath)) {
    Write-Error "Log file not found: $LogPath"
    exit 2
}

$text = Get-Content -LiteralPath $LogPath -Raw

$checks = [ordered]@{
    provider = @("Installed emulated GL capabilities", "GLCapabilities", "OpenGL")
    state_query = @("glGet", "state_query")
    texture_image = @("texture_image", "glTex", "glCompressedTex")
    framebuffer_readback = @("framebuffer_readback", "glReadPixels", "glBlitFramebuffer")
    shader_conversion = @("shader_conversion", "fallback shader", "Failed to compile shader")
    draw_path = @("draw_path", "glDraw", "drawElements", "drawArrays")
}

Write-Output "GL contract summary for $LogPath"

foreach ($family in $checks.Keys) {
    $count = 0
    foreach ($pattern in $checks[$family]) {
        $count += ([regex]::Matches($text, [regex]::Escape($pattern))).Count
    }
    Write-Output ("{0}: {1}" -f $family, $count)
}

$fatalPatterns = @("Exception", "FATAL", "GL contract violation", "VK_ERROR_DEVICE_LOST")
$fatalCount = 0
foreach ($pattern in $fatalPatterns) {
    $fatalCount += ([regex]::Matches($text, [regex]::Escape($pattern))).Count
}

Write-Output ("runtime_smoke: fatal_patterns={0}" -f $fatalCount)

if ($fatalCount -gt 0) {
    exit 1
}

exit 0
```

- [ ] **Step 4: Run the script test**

Run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.RuntimeSmokeScriptTest
```

Expected: 1 test passes.

- [ ] **Step 5: Run the script against available logs**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\gl-runtime-smoke-check.ps1 -LogPath run\logs\latest.log
```

Expected: prints `GL contract summary`. Exit code may be `1` if the log contains old exceptions; that is acceptable for this step because the script is a classifier.

- [ ] **Step 6: Commit**

Run:

```powershell
git add scripts/gl-runtime-smoke-check.ps1 src/test/java/net/vulkanmod/compat/RuntimeSmokeScriptTest.java
git commit -m "test: add GL runtime smoke classifier"
```

## Task 11: Focused Verification Matrix

**Files:**
- Modify: `docs/superpowers/specs/2026-06-18-universal-gl-compatibility-design.md`

- [ ] **Step 1: Add verification commands to the spec**

Append this section to `docs/superpowers/specs/2026-06-18-universal-gl-compatibility-design.md`:

```markdown
## Verification Commands

Focused universal GL suite:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.UniversalGlContractLedgerTest --tests net.vulkanmod.compat.UniversalGlNoCrashPolicyTest --tests net.vulkanmod.gl.GlEmulationLogContractTest --tests net.vulkanmod.compat.GlFunctionRegistryTest --tests net.vulkanmod.compat.GlCapabilitiesFallbackTest --tests net.vulkanmod.compat.GlCallCoverageTest --tests net.vulkanmod.gl.GlTextureTest --tests net.vulkanmod.gl.GlTextureDimensionalContractTest --tests net.vulkanmod.compat.gl.FramebufferReadbackContractTest --tests net.vulkanmod.compat.ShaderContractFallbackTest --tests net.vulkanmod.compat.DrawContractRoutingTest --tests net.vulkanmod.compat.RuntimeSmokeScriptTest
```

Full build:

```powershell
.\gradlew.bat build
```

Runtime smoke classifier:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\gl-runtime-smoke-check.ps1 -LogPath run\logs\latest.log
```
```

- [ ] **Step 2: Run the focused universal GL suite**

Run the full command from the spec.

Expected: all selected tests pass. If a listed test class does not exist because its task was not executed in this branch, remove it from the command only after confirming the corresponding task was intentionally not part of the current execution batch.

- [ ] **Step 3: Run the full build**

Run:

```powershell
.\gradlew.bat build
```

Expected: build passes.

- [ ] **Step 4: Commit**

Run:

```powershell
git add docs/superpowers/specs/2026-06-18-universal-gl-compatibility-design.md
git commit -m "docs: add universal GL verification matrix"
```

## Execution Notes

- Run tasks in order. The early ledger/logging/policy tasks make later compatibility fixes measurable.
- Do not claim a category is complete unless its targeted tests and runtime evidence pass in the same work turn.
- If a runtime modpack exposes a failure, record the modpack only in notes/log evidence. The production fix and regression test must name the GL contract.
- If three attempted fixes for one GL contract fail, stop and revisit the architecture before continuing.

## Final Verification

After the selected task batch is complete, run:

```powershell
.\gradlew.bat test --tests net.vulkanmod.compat.GlFunctionRegistryTest --tests net.vulkanmod.compat.GlCapabilitiesFallbackTest --tests net.vulkanmod.compat.GlCallCoverageTest --tests net.vulkanmod.compat.UniversalGlContractLedgerTest --tests net.vulkanmod.compat.UniversalGlNoCrashPolicyTest
.\gradlew.bat build
```

Expected: both commands exit `0`.

Then run the smoke classifier on the latest available runtime log:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\gl-runtime-smoke-check.ps1 -LogPath run\logs\latest.log
```

Expected: prints a GL contract summary. Treat nonzero output as evidence for the next GL contract task, not as a reason to hide the result.
