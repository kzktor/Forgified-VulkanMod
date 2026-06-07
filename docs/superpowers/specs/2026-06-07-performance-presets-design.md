# Performance Presets Design

## Goal

Add one-click performance presets that tune both vanilla Minecraft video options and VulkanMod optimization settings. The feature should make low-end tuning easy while still allowing players to customize individual settings after applying a preset.

## Current Context

VulkanMod already has optimization controls for advanced chunk culling, entity culling, block entity culling, leaves culling, unique opaque layer, indirect draw, particle culling, and frame queue size. Minecraft video options are exposed from `Options.getGraphicsOpts()`, and VulkanMod options are exposed from `Options.getOptimizationOpts()` and `Options.getOtherOpts()`.

The chunk build path already has separate high and low priority task queues in `TaskDispatcher`, and section uploads are capped at a hardcoded eight uploads per frame. Particle culling, entity culling, and block entity culling already exist and can be folded into the presets instead of rebuilt from scratch.

## User Experience

Add a `Performance Preset` cycling option near the top of the Optimizations page with these values:

- `Custom`
- `Potato`
- `Balanced`
- `Vulkan Fast`
- `Smooth FPS`

Selecting a non-custom preset immediately applies that preset to both Minecraft options and VulkanMod config. If the player later changes one of the controlled options manually, the active preset should become `Custom`.

The preset option should not hide or replace the existing individual controls. Players can apply a preset, then adjust specific settings.

## Preset Values

| Setting | Potato | Balanced | Vulkan Fast | Smooth FPS |
| --- | --- | --- | --- | --- |
| Render distance | 6 | 10 | 12 | 8 |
| Simulation distance | 5 | 6 | 6 | 5 |
| Graphics | Fast | Fast | Fast | Fast |
| Particles | Minimal | Decreased | Decreased | Decreased |
| Clouds | Off | Off | Fast | Off |
| Entity shadows | Off | Off | Off | Off |
| Entity distance | 50% | 75% | 100% | 75% |
| Biome blend radius | 0 | 1 | 1 | 0 |
| Ambient occlusion | Off | Smooth | Smooth | Off |
| Advanced chunk culling | Aggressive | Normal | Normal | Conservative |
| Entity culling | On | On | On | On |
| Block entity culling | On | On | On | On |
| Leaves culling | On | On | On | On |
| Particle culling | Performance | Balanced | Balanced | Performance |
| Unique opaque layer | On | On | On | On |
| Indirect draw | Off | Supported device only | Supported device only | Off |
| Frame queue size | 2 | 2 | 2 | 3 |
| Chunk uploads per frame | 3 | 6 | 8 | 4 |

`Smooth FPS` favors steadier frame pacing over raw throughput. `Vulkan Fast` favors broad renderer performance while keeping a moderate visual baseline. `Potato` is the most aggressive low-end profile.

## Code Structure

Add a small preset model under `net.vulkanmod.config`:

- `PerformancePreset` enum for preset names and translation keys.
- `PerformancePresetApplier` utility that applies a preset to `Minecraft.options` and `Initializer.CONFIG`.

Extend `Config` with:

- `public int performancePreset = 0;`
- `public int chunkUploadsPerFrame = 8;`

Use integer storage to match existing config style, but keep preset behavior in the enum/applier so the raw values do not leak through the UI.

## Options UI

In `Options.getOptimizationOpts()`, add the preset cycling option before the individual optimization controls. The setter should:

1. Store the selected preset in `config.performancePreset`.
2. Apply Minecraft video settings.
3. Apply VulkanMod settings.
4. Trigger `minecraft.levelRenderer.allChanged()` when changed settings affect chunk meshes or render traversal.
5. Trigger `Renderer.scheduleSwapChainUpdate()` if frame queue size changes.

Manual setters for controlled options should mark `config.performancePreset = Custom` when the user changes them outside the preset control.

## Chunk Scheduling

Replace the hardcoded `MAX_UPLOADS_PER_FRAME = 8` in `TaskDispatcher.updateSections()` with `Initializer.CONFIG.chunkUploadsPerFrame`, clamped to a conservative valid range such as 1 to 16.

Do not change the existing high and low priority queue model in the first implementation. The first useful scheduling improvement is making upload pressure preset-controlled, because that directly targets stutter and has a small blast radius.

## Culling and Particles

Keep the current culling implementations as the first version:

- Entity culling continues to use visible section membership.
- Block entity culling continues to use distance and frustum checks.
- Particle culling continues to use distance, FOV, and same-type count budgets.

The presets only configure these systems. Further improvements, such as per-block-entity type limits or occlusion queries, should be separate follow-up work because they carry higher compatibility risk.

## Error Handling

Preset application should tolerate a missing level or renderer state during startup. Calls like `minecraft.levelRenderer.allChanged()` should only run when a level renderer is available.

Unsupported indirect draw should resolve to `false` through `DeviceManager.supportsFastIndirectDraw()` so a preset never enables a setting the current GPU path cannot use.

## Testing

Add focused tests where practical:

- Config serialization preserves `performancePreset` and `chunkUploadsPerFrame`.
- Preset application maps each preset to the expected VulkanMod config values.
- Chunk upload budget clamps invalid config values before use.

Manual verification should cover opening the options screen, applying each preset, confirming visible option values update, then changing an individual option and confirming the preset returns to `Custom`.

## Out of Scope

This design does not add automatic FPS-based preset switching, GPU timing heuristics, new occlusion algorithms, or a new options screen layout. Those can be layered on later after the one-click preset path is reliable.
