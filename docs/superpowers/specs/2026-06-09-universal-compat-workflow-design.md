# Universal Compatibility Workflow Design

## Purpose

VulkanMod compatibility work must move away from production Java files named after specific mods. The project should fix rendering contracts once, in universal systems, so a bug exposed by one mod improves behavior for every mod using the same Minecraft, NeoForge, OpenGL, or Vulkan surface.

The selected direction is universal systems, with capability-based boundaries only where the renderer truly needs an explicit integration point. Production Java names must describe the contract or capability, not the mod that revealed the issue.

## Non-Negotiable Rule

No production Java package, class, method, or field may be named after a specific third-party mod.

Examples of disallowed production names:

- `CreateCompat`
- `DistantHorizonsCompat`
- `mixin.compatibility.distanthorizons`
- `shouldApplyDistantHorizonsMixin`
- `createOnlyFix`

Examples of allowed production names:

- `ExternalTerrainRenderBridge`
- `LodRenderPath`
- `LegacyGlStateBridge`
- `FramebufferCompatibility`
- `ModelDataChunkRenderer`
- `BlockEntityRenderLifecycle`

Temporary comments may mention the mod that exposed a bug while a migration is in progress, but the final production code should explain the rendering contract instead.

## Bug Triage Workflow

Every compatibility bug must be classified by the universal contract it violates.

1. Identify the symptom and reproduction.
2. Identify the rendering contract involved.
3. Fix the contract in the universal layer.
4. Add a generic regression test for that contract.
5. Only add a capability adapter if the contract cannot be represented by existing Minecraft, NeoForge, OpenGL, or Vulkan abstractions.

Examples:

- A stencil crash from a UI scene is a framebuffer format and stencil state issue.
- Duplicate kinetic models are a NeoForge model data and render-type aware chunk rendering issue.
- Ghost block entities after breaking blocks are a block-entity lifecycle issue.
- External LOD terrain is an external terrain render path issue.
- Legacy OpenGL state calls are GL state emulation issues.

## Architecture

### Universal Rendering Contracts

Compatibility behavior should live under renderer-owned systems:

- GL API emulation: OpenGL entrypoints, state tracking, object lifetime, and compatibility queries.
- Framebuffer semantics: color/depth/stencil attachment formats, clears, load/store behavior, blits, and render target lifecycle.
- Shader translation: uniform parsing, sampler binding, generated Vulkan layouts, and shader feature coverage.
- Chunk model rendering: NeoForge model data, render layer selection, dynamic baked models, translucent sorting, and model lifecycle.
- Block entity rendering: registration, offscreen/global renderer lifecycle, culling, destruction overlays, and batch flush behavior.
- External render paths: capability-based bridges for renderers that own separate terrain, LOD, or post-processing flows.

### Capability Adapters

Capability adapters are allowed only when a subsystem cannot be expressed as a normal Minecraft or NeoForge render path.

Adapters must be named after capabilities, not mods:

- `ExternalTerrainRenderer`
- `ExternalLodRenderer`
- `LegacyGlDrawConsumer`
- `PostProcessRenderOwner`
- `CustomFramebufferOwner`

Adapters may detect available capabilities at runtime, but detection results must be stored as capability flags rather than mod IDs. A loader check may exist at the boundary if unavoidable, but the public production API and class names must stay generic.

### Diagnostics

Diagnostics should report capabilities and render contracts, not prescribe mod-specific paths.

Good diagnostic labels:

- `legacy_gl_calls`
- `external_lod_renderer`
- `framebuffer_depth_stencil`
- `dynamic_baked_model_data`
- `global_block_entity_lifecycle`

Poor diagnostic labels:

- `distant_horizons_mode`
- `create_fix_enabled`
- `ponder_stencil_hack`

## Migration Plan

The current compatibility code should be migrated in small, verifiable steps:

1. Inventory production Java names that mention a third-party mod.
2. Group each file by the universal contract it actually implements.
3. Rename packages/classes/methods to contract or capability names.
4. Move genuinely generic behavior into renderer, GL, Vulkan, or chunk systems.
5. Replace mod-named runtime options with capability-named options.
6. Replace mod-named tests with generic contract tests.
7. Keep old config keys only through a documented migration shim if users already depend on them.

The migration should avoid broad rewrites. Each step should preserve behavior, add or update tests, and keep deployable builds.

## Testing Strategy

Tests should assert universal contracts:

- GL methods update VulkanMod state without native OpenGL access after Vulkan handoff.
- Framebuffer format and stencil behavior supports common OpenGL formats.
- Dynamic baked models receive `ModelData` and render-type-aware quad requests.
- Chunk rebuilds unregister stale block entities.
- External terrain render paths receive stable matrices, buffers, and lifecycle callbacks.
- Shader translator supports uniform and sampler patterns used by legacy GL-style renderers.

Mod names should not appear in new production tests unless the test is explicitly documenting a migration from an old name. Fixture names can describe behavior, such as `external_lod_shader_uniforms` or `dynamic_model_with_custom_render_layer`.

## Workflow Enforcement

Before accepting a compatibility patch, reviewers should ask:

- Does any production Java name mention a third-party mod?
- Is the fix classified by a universal rendering contract?
- Would another mod with the same rendering behavior benefit from this fix?
- Is there a generic regression test?
- If an adapter was added, is it named by capability rather than mod?
- Are config keys and diagnostics capability-named?

If the answer fails any of these checks, the patch should be redesigned before implementation continues.

## Success Criteria

The workflow is successful when:

- New compatibility work no longer adds mod-named production Java files.
- Existing mod-named production Java files are migrated or removed.
- Bug reports are tracked by rendering contract.
- Regression tests describe renderer behavior, not individual mods.
- Optional integrations expose capability names only.
- VulkanMod becomes more compatible with many mods each time one mod exposes a rendering contract gap.
