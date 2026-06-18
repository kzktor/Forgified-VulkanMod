# Universal GL Compatibility Design

## Purpose

VulkanMod compatibility work for this goal must focus on a universal OpenGL/LWJGL emulation layer. The target is not a growing list of mod-specific bridges or native backends. The target is that any mod using OpenGL through LWJGL gets the same stable contract: calls are present, state is coherent, objects have GL-like lifetimes, and rendering behavior degrades safely when exact Vulkan parity is not implemented yet.

This design supersedes compatibility directions that solve one ecosystem by adding a named integration such as a native Flywheel backend or an external-terrain bridge. Those modpacks remain valuable test cases, but production fixes must live in shared GL, shader, texture, framebuffer, draw, and renderer contracts.

## Non-Negotiable Scope

Production code for this goal must not add new mod-named fixes, mod-specific adapters, or ecosystem-specific rendering backends.

Allowed:

- GL entrypoint implementations.
- GL state/query emulation.
- GL object lifetime emulation for textures, buffers, vertex arrays, framebuffers, renderbuffers, shaders, programs, queries, samplers, and sync objects.
- Generic GLSL-to-Vulkan shader handling.
- Generic draw routing for GL immediate, array, indexed, multi-draw, instanced, indirect, and DSA paths.
- Generic fallbacks that preserve stability and log the missing GL contract.

Not allowed:

- New production packages/classes named after third-party mods.
- New native backends for one mod rendering engine.
- Runtime branches that special-case a mod ID to change GL behavior.
- Fixes that only work because a specific mod's class or package is targeted.

## Current Evidence

The previous "100%" number only meant `GLCapabilities` direct-provider function pointers were filled. It did not prove universal mod compatibility.

Current runtime evidence from the `RE Spellerium` instance shows:

- The large modpack can launch with VulkanMod and exit cleanly.
- The pack still exposes GL contract gaps, including shader conversion fallbacks and GL-backend fallbacks from libraries that expect a fuller OpenGL implementation.
- Therefore the current compatibility status is not "100% every GL mod works."

The right progress metric is no longer a single direct-provider percentage. Progress must be tracked by GL contract families and runtime validation.

## Architecture

### 1. Entrypoint Coverage

Every LWJGL OpenGL entrypoint exposed through `GLCapabilities` should resolve to a VulkanMod provider. Provider coverage prevents immediate crashes, but it is only the floor. Each function then needs one of three documented behaviors:

- implemented: updates VulkanMod state or submits real Vulkan work;
- safe emulation: returns GL-compatible state or a safe fallback without breaking caller assumptions;
- explicit gap: logs once with the GL contract name and degrades without throwing.

No reachable GL entrypoint should hard-crash because it is missing, null, or intentionally unimplemented.

### 2. State And Query Compatibility

Mods commonly save GL state, render, then restore it. VulkanMod must keep a coherent GL-state mirror for capability bits, bindings, viewport/scissor, blend/depth/stencil/cull state, pixel-store state, active texture unit, shader/program state, framebuffer bindings, buffer bindings, vertex array bindings, and supported limits.

`glGet*`, `glIs*`, and object-query functions must report values consistent with the emulated state. Returning "present but wrong" is worse than a safe fallback because it makes mods restore broken state later.

### 3. Object Lifetime

GL object names must be generated, bound, queried, deleted, and reused safely without relying on a native OpenGL context after Vulkan handoff. Unknown or deleted object names should behave like GL-compatible no-ops or false queries where possible, not crash.

This includes objects from legacy APIs and aliases: ARB/EXT framebuffer objects, ARB shader objects, ARB vertex buffers, DSA texture/buffer/framebuffer functions, queries, samplers, syncs, display lists, and compatibility-profile objects.

### 4. Texture And Image Semantics

Texture support must move from metadata-only placeholders toward real behavior:

- uncompressed 1D, 2D, 3D, array, and cubemap allocation/upload;
- compressed texture upload or CPU/GPU decode fallback;
- sub-image updates, copy paths, mipmap generation, sampler parameters, swizzle/wrap/filter state;
- pixel-store unpack rules;
- readback paths for APIs that inspect texture/framebuffer data.

When exact support is missing, VulkanMod should preserve object metadata and return stable results, but the gap must be tracked by GL contract.

### 5. Framebuffer And Readback Semantics

Framebuffer and renderbuffer emulation must support common color, depth, stencil, packed depth-stencil, multisample, blit, clear, invalidate, and attachment-query behavior. `glReadPixels` must eventually perform real Vulkan readback with GL-compatible format conversion and Y orientation.

Until a readback path is correct, fallback behavior must be safe and explicit. A black zero-fill is acceptable only as a known compatibility gap, not as a final 100% claim.

### 6. Shader Compatibility

Shader compatibility must be generic GLSL handling, not a shader pack or mod-specific whitelist.

The shader layer should:

- parse common Mojang and external shader JSON conventions;
- support common GLSL syntax and include patterns;
- map uniforms, samplers, attributes, fragment outputs, and state-derived globals consistently;
- reject unsupported shader features with a stable fallback shader only when necessary;
- log conversion failures by shader feature/contract, not by mod.

Shader fallbacks are a survival mechanism, not proof that rendering is correct.

### 7. Draw Compatibility

GL draw calls should route through a generic draw contract that understands current GL state and bound objects. This includes array, indexed, range, multi-draw, instanced, indirect, base-vertex/base-instance, and legacy immediate/display-list paths.

The draw layer should degrade unsupported primitive modes consistently, preserve performance for hot paths, and avoid per-mod render ownership assumptions.

### 8. Performance Guardrails

Compatibility cannot blindly add stalls or allocations to hot render paths. Each GL feature should choose the least risky implementation:

- pure state/query emulation when no GPU work is required;
- staged GPU upload/download only when GL semantics require it;
- cached conversion and pipeline state where possible;
- once-only diagnostics outside hot loops;
- feature flags only for generic GL contract experiments, not mod IDs.

## Testing Strategy

Tests must be contract-based:

- provider coverage across LWJGL GL capability fields;
- state save/restore and `glGet*` correctness;
- object lifetime and unknown-name safety;
- texture upload, compressed texture decode/upload, 3D/array texture metadata and allocation behavior;
- framebuffer completeness, attachment queries, blits, clears, and readback fallbacks;
- shader conversion fixtures for generic GLSL patterns;
- draw routing fixtures for primitive modes, instancing, and indirect/multi-draw variants.

Runtime validation should use real modpacks as black-box GL callers. Logs and screenshots may mention the modpack that exposed a failure, but the fix and test must name the GL contract.

## Progress Model

Compatibility percentage should not be reported as "100%" until all tracked GL contract families are implemented or safely emulated, and runtime validation shows no GL-contract failures in representative packs.

Recommended status categories:

- Provider coverage.
- State/query coverage.
- Object lifetime coverage.
- Texture/image coverage.
- Framebuffer/readback coverage.
- Shader conversion coverage.
- Draw-path coverage.
- Runtime smoke coverage.
- Performance regression coverage.

Each category should have evidence: tests, logs, or in-game verification.

## Success Criteria

This goal is satisfied only when:

- no mod-specific production integration is required for GL-using mods to load;
- GL entrypoints used by real mods do not crash after Vulkan handoff;
- common GL state save/restore patterns work;
- common texture, framebuffer, shader, and draw workflows produce correct or intentionally degraded output;
- unsupported GL features degrade safely with once-only diagnostics;
- the same fixes improve multiple unrelated mods because they target GL contracts;
- performance remains within measured bounds on the target packs.

Until then, the honest claim is "universal GL compatibility is improving by contract family," not "100% every mod works."
