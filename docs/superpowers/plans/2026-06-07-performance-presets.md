# Performance Presets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one-click low-end performance presets that apply both Minecraft video options and VulkanMod optimization settings.

**Architecture:** Add a small preset enum and applier under `net.vulkanmod.config`, store the selected preset and chunk upload budget in `Config`, expose the preset in the existing optimization options page, and make `TaskDispatcher` read the upload budget from config. Existing entity culling, block entity culling, particle culling, and chunk task queues stay intact; presets configure them.

**Tech Stack:** Java 21, NeoForge Minecraft client APIs, Sponge Mixin-adjacent renderer code, Gson config serialization, JUnit 5 source-level tests.

---

## File Structure

- Create `src/main/java/net/vulkanmod/config/PerformancePreset.java`
  - Owns preset ids, translation keys, VulkanMod config values, and vanilla video values that are simple to store as primitives.
- Create `src/main/java/net/vulkanmod/config/PerformancePresetApplier.java`
  - Applies a preset to `Config` and `Minecraft.options`.
  - Handles renderer refresh and swapchain update hooks.
- Modify `src/main/java/net/vulkanmod/config/Config.java`
  - Add `performancePreset` and `chunkUploadsPerFrame`.
- Modify `src/main/java/net/vulkanmod/config/option/Options.java`
  - Add the preset option.
  - Mark the preset as `Custom` when controlled options are changed manually.
- Modify `src/main/java/net/vulkanmod/render/chunk/build/TaskDispatcher.java`
  - Replace the hardcoded upload cap with a clamped config-backed cap.
- Modify `src/main/resources/assets/vulkanmod/lang/en_us.json`
  - Add translations for the preset control and preset names.
- Test `src/test/java/net/vulkanmod/config/PerformancePresetTest.java`
  - Verify source-level preset values and UI integration.
- Test `src/test/java/net/vulkanmod/render/chunk/build/TaskDispatcherUploadBudgetTest.java`
  - Verify upload budget reads config and clamps.

---

### Task 1: Add Failing Tests For Preset Model And UI Wiring

**Files:**
- Create: `src/test/java/net/vulkanmod/config/PerformancePresetTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/net/vulkanmod/config/PerformancePresetTest.java`:

```java
package net.vulkanmod.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformancePresetTest {
    @Test
    void configStoresPresetAndUploadBudget() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/config/Config.java"));

        assertTrue(source.contains("public int performancePreset = 0;"));
        assertTrue(source.contains("public int chunkUploadsPerFrame = 8;"));
    }

    @Test
    void presetEnumDefinesExpectedProfiles() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/config/PerformancePreset.java"));

        assertTrue(source.contains("CUSTOM(0, \"vulkanmod.options.performancePreset.custom\""));
        assertTrue(source.contains("POTATO(1, \"vulkanmod.options.performancePreset.potato\""));
        assertTrue(source.contains("BALANCED(2, \"vulkanmod.options.performancePreset.balanced\""));
        assertTrue(source.contains("VULKAN_FAST(3, \"vulkanmod.options.performancePreset.vulkanFast\""));
        assertTrue(source.contains("SMOOTH_FPS(4, \"vulkanmod.options.performancePreset.smoothFps\""));
        assertTrue(source.contains("chunkUploadsPerFrame"));
    }

    @Test
    void optionsExposePresetBeforeOptimizationToggles() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/config/option/Options.java"));

        assertTrue(source.contains("PerformancePreset.values()"));
        assertTrue(source.contains("PerformancePresetApplier.apply"));
        assertTrue(source.contains("markPerformancePresetCustom()"));
    }

    @Test
    void languageFileContainsPresetTranslations() throws Exception {
        String lang = Files.readString(Path.of("src/main/resources/assets/vulkanmod/lang/en_us.json"));

        assertTrue(lang.contains("\"vulkanmod.options.performancePreset\": \"Performance Preset\""));
        assertTrue(lang.contains("\"vulkanmod.options.performancePreset.potato\": \"Potato\""));
        assertTrue(lang.contains("\"vulkanmod.options.performancePreset.vulkanFast\": \"Vulkan Fast\""));
        assertTrue(lang.contains("\"vulkanmod.options.performancePreset.smoothFps\": \"Smooth FPS\""));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
.\gradlew test --tests net.vulkanmod.config.PerformancePresetTest
```

Expected: FAIL because `PerformancePreset.java`, config fields, option wiring, and language keys do not exist yet.

- [ ] **Step 3: Commit the failing test**

```powershell
git add src\test\java\net\vulkanmod\config\PerformancePresetTest.java
git commit -m "test: cover performance preset wiring"
```

---

### Task 2: Add Preset Config Fields And Enum

**Files:**
- Modify: `src/main/java/net/vulkanmod/config/Config.java`
- Create: `src/main/java/net/vulkanmod/config/PerformancePreset.java`

- [ ] **Step 1: Add config fields**

In `Config.java`, add the fields near the other optimization fields:

```java
public int performancePreset = 0;
public int chunkUploadsPerFrame = 8;
```

- [ ] **Step 2: Create the preset enum**

Create `src/main/java/net/vulkanmod/config/PerformancePreset.java`:

```java
package net.vulkanmod.config;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.ParticleStatus;
import net.vulkanmod.render.chunk.build.light.LightMode;

public enum PerformancePreset {
    CUSTOM(0, "vulkanmod.options.performancePreset.custom", 2, true, true, true, 2, true, false, 8,
            10, 6, GraphicsStatus.FAST, ParticleStatus.DECREASED, CloudStatus.OFF, false, 75, 1, LightMode.SMOOTH),
    POTATO(1, "vulkanmod.options.performancePreset.potato", 1, true, true, true, 3, true, false, 3,
            6, 5, GraphicsStatus.FAST, ParticleStatus.MINIMAL, CloudStatus.OFF, false, 50, 0, LightMode.FLAT),
    BALANCED(2, "vulkanmod.options.performancePreset.balanced", 2, true, true, true, 2, true, true, 6,
            10, 6, GraphicsStatus.FAST, ParticleStatus.DECREASED, CloudStatus.OFF, false, 75, 1, LightMode.SMOOTH),
    VULKAN_FAST(3, "vulkanmod.options.performancePreset.vulkanFast", 2, true, true, true, 2, true, true, 8,
            12, 6, GraphicsStatus.FAST, ParticleStatus.DECREASED, CloudStatus.FAST, false, 100, 1, LightMode.SMOOTH),
    SMOOTH_FPS(4, "vulkanmod.options.performancePreset.smoothFps", 3, true, true, true, 3, true, false, 4,
            8, 5, GraphicsStatus.FAST, ParticleStatus.DECREASED, CloudStatus.OFF, false, 75, 0, LightMode.FLAT);

    public final int id;
    public final String translationKey;
    public final int advCulling;
    public final boolean entityCulling;
    public final boolean blockEntityCulling;
    public final boolean leavesCulling;
    public final int particleCulling;
    public final boolean uniqueOpaqueLayer;
    public final boolean indirectDraw;
    public final int chunkUploadsPerFrame;
    public final int renderDistance;
    public final int simulationDistance;
    public final GraphicsStatus graphicsStatus;
    public final ParticleStatus particleStatus;
    public final CloudStatus cloudStatus;
    public final boolean entityShadows;
    public final int entityDistancePercent;
    public final int biomeBlendRadius;
    public final int ambientOcclusion;

    PerformancePreset(int id, String translationKey, int advCulling, boolean entityCulling,
                      boolean blockEntityCulling, boolean leavesCulling, int particleCulling,
                      boolean uniqueOpaqueLayer, boolean indirectDraw, int chunkUploadsPerFrame,
                      int renderDistance, int simulationDistance, GraphicsStatus graphicsStatus,
                      ParticleStatus particleStatus, CloudStatus cloudStatus, boolean entityShadows,
                      int entityDistancePercent, int biomeBlendRadius, int ambientOcclusion) {
        this.id = id;
        this.translationKey = translationKey;
        this.advCulling = advCulling;
        this.entityCulling = entityCulling;
        this.blockEntityCulling = blockEntityCulling;
        this.leavesCulling = leavesCulling;
        this.particleCulling = particleCulling;
        this.uniqueOpaqueLayer = uniqueOpaqueLayer;
        this.indirectDraw = indirectDraw;
        this.chunkUploadsPerFrame = chunkUploadsPerFrame;
        this.renderDistance = renderDistance;
        this.simulationDistance = simulationDistance;
        this.graphicsStatus = graphicsStatus;
        this.particleStatus = particleStatus;
        this.cloudStatus = cloudStatus;
        this.entityShadows = entityShadows;
        this.entityDistancePercent = entityDistancePercent;
        this.biomeBlendRadius = biomeBlendRadius;
        this.ambientOcclusion = ambientOcclusion;
    }

    public static PerformancePreset byId(int id) {
        for (PerformancePreset preset : values()) {
            if (preset.id == id) {
                return preset;
            }
        }

        return CUSTOM;
    }
}
```

- [ ] **Step 3: Run the preset test**

Run:

```powershell
.\gradlew test --tests net.vulkanmod.config.PerformancePresetTest
```

Expected: still FAIL because the UI wiring and language keys are not added yet.

- [ ] **Step 4: Commit**

```powershell
git add src\main\java\net\vulkanmod\config\Config.java src\main\java\net\vulkanmod\config\PerformancePreset.java
git commit -m "feat: add performance preset model"
```

---

### Task 3: Add Preset Applier

**Files:**
- Create: `src/main/java/net/vulkanmod/config/PerformancePresetApplier.java`

- [ ] **Step 1: Create the applier**

Create `src/main/java/net/vulkanmod/config/PerformancePresetApplier.java`:

```java
package net.vulkanmod.config;

import net.minecraft.client.Minecraft;
import net.vulkanmod.render.chunk.build.light.LightMode;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.device.DeviceManager;

public final class PerformancePresetApplier {
    private PerformancePresetApplier() {
    }

    public static void apply(PerformancePreset preset, Config config, Minecraft minecraft) {
        if (preset == PerformancePreset.CUSTOM) {
            config.performancePreset = PerformancePreset.CUSTOM.id;
            return;
        }

        int oldFrameQueueSize = config.frameQueueSize;
        config.performancePreset = preset.id;
        config.advCulling = preset.advCulling;
        config.entityCulling = preset.entityCulling;
        config.blockEntityCulling = preset.blockEntityCulling;
        config.leavesCulling = preset.leavesCulling;
        config.particleCulling = preset.particleCulling;
        config.uniqueOpaqueLayer = preset.uniqueOpaqueLayer;
        config.indirectDraw = preset.indirectDraw && DeviceManager.supportsFastIndirectDraw();
        config.chunkUploadsPerFrame = preset.chunkUploadsPerFrame;
        config.frameQueueSize = preset == PerformancePreset.SMOOTH_FPS ? 3 : 2;
        config.ambientOcclusion = preset.ambientOcclusion;

        if (minecraft != null && minecraft.options != null) {
            minecraft.options.renderDistance().set(preset.renderDistance);
            minecraft.options.simulationDistance().set(preset.simulationDistance);
            minecraft.options.graphicsMode().set(preset.graphicsStatus);
            minecraft.options.particles().set(preset.particleStatus);
            minecraft.options.cloudStatus().set(preset.cloudStatus);
            minecraft.options.entityShadows().set(preset.entityShadows);
            minecraft.options.entityDistanceScaling().set(preset.entityDistancePercent * 0.01);
            minecraft.options.biomeBlendRadius().set(preset.biomeBlendRadius);
            minecraft.options.ambientOcclusion().set(preset.ambientOcclusion > LightMode.FLAT);

            if (minecraft.levelRenderer != null) {
                minecraft.levelRenderer.allChanged();
            }
        }

        if (oldFrameQueueSize != config.frameQueueSize) {
            Renderer.scheduleSwapChainUpdate();
        }
    }
}
```

- [ ] **Step 2: Run compile-focused test**

Run:

```powershell
.\gradlew test --tests net.vulkanmod.config.PerformancePresetTest
```

Expected: still FAIL because options and translations are not wired yet. Java compilation should succeed.

- [ ] **Step 3: Commit**

```powershell
git add src\main\java\net\vulkanmod\config\PerformancePresetApplier.java
git commit -m "feat: apply performance presets"
```

---

### Task 4: Wire Presets Into Options UI

**Files:**
- Modify: `src/main/java/net/vulkanmod/config/option/Options.java`

- [ ] **Step 1: Add imports**

Add these imports to `Options.java`:

```java
import net.vulkanmod.config.PerformancePreset;
import net.vulkanmod.config.PerformancePresetApplier;
```

- [ ] **Step 2: Add custom marker helper**

Inside `Options`, near the static fields, add:

```java
private static void markPerformancePresetCustom() {
    config.performancePreset = PerformancePreset.CUSTOM.id;
}
```

- [ ] **Step 3: Add the preset cycling option**

At the start of `getOptimizationOpts()`, add a new first `OptionBlock` before the current optimization block:

```java
new OptionBlock("", new Option[]{
        new CyclingOption<>(Component.translatable("vulkanmod.options.performancePreset"),
                PerformancePreset.values(),
                value -> PerformancePresetApplier.apply(value, config, minecraft),
                () -> PerformancePreset.byId(config.performancePreset))
                .setTranslator(value -> Component.translatable(value.translationKey))
                .setTooltip(Component.translatable("vulkanmod.options.performancePreset.tooltip")),
}),
```

The returned `OptionBlock[]` should contain this block first, followed by the existing optimization block.

- [ ] **Step 4: Mark manual VulkanMod changes as custom**

In each setter for controlled VulkanMod options, call `markPerformancePresetCustom()` before or after setting the value:

```java
value -> {
    config.advCulling = value;
    markPerformancePresetCustom();
}
```

Apply this pattern to `advCulling`, `entityCulling`, `blockEntityCulling`, `leavesCulling`, `uniqueOpaqueLayer`, `indirectDraw`, `particleCulling`, and `frameQueueSize`.

- [ ] **Step 5: Mark manual Minecraft video changes as custom**

In `getGraphicsOpts()`, apply the same marker to setters for render distance, simulation distance, prioritize chunk updates, graphics, particles, clouds, ambient occlusion, biome blend, entity shadows, and entity distance scaling. Example:

```java
(value) -> {
    minecraftOptions.renderDistance().set(value);
    markPerformancePresetCustom();
}
```

For ambient occlusion, preserve the existing `allChanged()` call:

```java
(value) -> {
    if (value > LightMode.FLAT)
        minecraftOptions.ambientOcclusion().set(true);
    else
        minecraftOptions.ambientOcclusion().set(false);

    config.ambientOcclusion = value;
    markPerformancePresetCustom();
    minecraft.levelRenderer.allChanged();
}
```

- [ ] **Step 6: Run the preset wiring test**

Run:

```powershell
.\gradlew test --tests net.vulkanmod.config.PerformancePresetTest
```

Expected: still FAIL because language keys are not added yet.

- [ ] **Step 7: Commit**

```powershell
git add src\main\java\net\vulkanmod\config\option\Options.java
git commit -m "feat: expose performance presets in options"
```

---

### Task 5: Add Language Keys

**Files:**
- Modify: `src/main/resources/assets/vulkanmod/lang/en_us.json`

- [ ] **Step 1: Add translations**

Add these entries to `en_us.json` while keeping valid JSON commas:

```json
"vulkanmod.options.performancePreset": "Performance Preset",
"vulkanmod.options.performancePreset.tooltip": "Applies a group of Minecraft and VulkanMod settings for the selected performance target.",
"vulkanmod.options.performancePreset.custom": "Custom",
"vulkanmod.options.performancePreset.potato": "Potato",
"vulkanmod.options.performancePreset.balanced": "Balanced",
"vulkanmod.options.performancePreset.vulkanFast": "Vulkan Fast",
"vulkanmod.options.performancePreset.smoothFps": "Smooth FPS"
```

- [ ] **Step 2: Run preset test**

Run:

```powershell
.\gradlew test --tests net.vulkanmod.config.PerformancePresetTest
```

Expected: PASS.

- [ ] **Step 3: Commit**

```powershell
git add src\main\resources\assets\vulkanmod\lang\en_us.json
git commit -m "feat: add performance preset translations"
```

---

### Task 6: Make Chunk Upload Budget Configurable

**Files:**
- Create: `src/test/java/net/vulkanmod/render/chunk/build/TaskDispatcherUploadBudgetTest.java`
- Modify: `src/main/java/net/vulkanmod/render/chunk/build/TaskDispatcher.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/net/vulkanmod/render/chunk/build/TaskDispatcherUploadBudgetTest.java`:

```java
package net.vulkanmod.render.chunk.build;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskDispatcherUploadBudgetTest {
    @Test
    void uploadBudgetComesFromConfigAndIsClamped() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/render/chunk/build/TaskDispatcher.java"));

        assertTrue(source.contains("Initializer.CONFIG.chunkUploadsPerFrame"));
        assertTrue(source.contains("Mth.clamp"));
        assertTrue(source.contains("private int getMaxUploadsPerFrame()"));
        assertFalse(source.contains("final int MAX_UPLOADS_PER_FRAME = 8;"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
.\gradlew test --tests net.vulkanmod.render.chunk.build.TaskDispatcherUploadBudgetTest
```

Expected: FAIL because `TaskDispatcher` still uses the hardcoded local constant.

- [ ] **Step 3: Update `TaskDispatcher` imports**

Add imports:

```java
import net.minecraft.util.Mth;
import net.vulkanmod.Initializer;
```

- [ ] **Step 4: Replace hardcoded upload cap**

In `updateSections()`, replace:

```java
final int MAX_UPLOADS_PER_FRAME = 8;

while(uploadsThisFrame < MAX_UPLOADS_PER_FRAME && (result = this.compileResults.poll()) != null) {
```

with:

```java
int maxUploadsPerFrame = this.getMaxUploadsPerFrame();

while(uploadsThisFrame < maxUploadsPerFrame && (result = this.compileResults.poll()) != null) {
```

Add this helper in the class:

```java
private int getMaxUploadsPerFrame() {
    return Mth.clamp(Initializer.CONFIG.chunkUploadsPerFrame, 1, 16);
}
```

- [ ] **Step 5: Run upload budget test**

Run:

```powershell
.\gradlew test --tests net.vulkanmod.render.chunk.build.TaskDispatcherUploadBudgetTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add src\test\java\net\vulkanmod\render\chunk\build\TaskDispatcherUploadBudgetTest.java src\main\java\net\vulkanmod\render\chunk\build\TaskDispatcher.java
git commit -m "feat: configure chunk upload budget"
```

---

### Task 7: Full Verification

**Files:**
- Verify all files changed by this plan.

- [ ] **Step 1: Run focused tests**

Run:

```powershell
.\gradlew test --tests net.vulkanmod.config.PerformancePresetTest --tests net.vulkanmod.render.chunk.build.TaskDispatcherUploadBudgetTest
```

Expected: PASS.

- [ ] **Step 2: Run full test suite**

Run:

```powershell
.\gradlew test
```

Expected: PASS.

- [ ] **Step 3: Check modified files**

Run:

```powershell
git status --short
```

Expected: only files from this feature remain modified or untracked because unrelated pre-existing worktree changes were not touched.

- [ ] **Step 4: Manual client smoke test**

Run:

```powershell
.\gradlew runClient
```

Expected: Minecraft opens. In VulkanMod options, `Performance Preset` appears on the Optimizations page. Selecting `Potato`, `Balanced`, `Vulkan Fast`, and `Smooth FPS` updates the controlled Minecraft and VulkanMod options. Manually changing a controlled option switches the preset to `Custom`.

- [ ] **Step 5: Final commit if needed**

If any verification-only fixes were made:

```powershell
git add src\main\java\net\vulkanmod\config src\main\java\net\vulkanmod\config\option\Options.java src\main\java\net\vulkanmod\render\chunk\build\TaskDispatcher.java src\main\resources\assets\vulkanmod\lang\en_us.json src\test\java\net\vulkanmod
git commit -m "fix: polish performance presets"
```

---

## Self-Review

- Spec coverage: The plan covers the preset option, preset values, config fields, UI behavior, manual custom mode, upload budget configuration, translations, and focused tests.
- Placeholder scan: No placeholder markers or unspecified "handle edge cases" steps remain.
- Type consistency: The same names are used throughout: `PerformancePreset`, `PerformancePresetApplier`, `performancePreset`, `chunkUploadsPerFrame`, and `markPerformancePresetCustom()`.
