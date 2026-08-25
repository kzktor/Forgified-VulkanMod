# VulkanMod Forge Unofficial Fork

This repository is an unofficial modified fork of [xCollateral/VulkanMod](https://github.com/xCollateral/VulkanMod).
It has been modified for Minecraft 1.20.1 on Forge and is not the official VulkanMod project.

Prominent modification notice: this fork was ported to Forge 1.20.1 on 2026-08-22. It retains the original project's licensing and attribution.

## Attribution

Original project: [xCollateral/VulkanMod](https://github.com/xCollateral/VulkanMod)

Original authors and contributors: xCollateral and VulkanMod contributors.

Fork maintainer: Rindw.

## Support

[![Support me on Ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/rindw)

This Ko-fi link supports maintenance of this unofficial Forge fork.

## Features

- Forge support for Minecraft 1.20.1.
- Vulkan-based renderer that replaces Minecraft's default OpenGL rendering path.
- Reduced CPU overhead through Vulkan rendering and optimized chunk submission.
- Chunk rendering and culling optimizations for smoother world rendering.
- GPU selection and a revamped in-game graphics settings screen.
- Compatibility helpers for external render paths, GUI rendering, and OpenGL-style calls used by other mods.
- Performance preset and render scale options for easier tuning.

## License

This project remains licensed under the GNU Lesser General Public License version 3.0 only. See [LICENSE](LICENSE) for the LGPLv3 terms and [COPYING](COPYING) for the GPLv3 text referenced by the LGPLv3.

The VulkanMod name, logos, CurseForge page, Modrinth page, Discord, and donation links belong to their respective original project owners. This fork is not endorsed by or affiliated with the upstream VulkanMod maintainers.

## Installation

1. Install [Forge](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html) for Minecraft 1.20.1.
2. Download this fork's `VulkanMod_1.20.1-0.1.0-ALPHA+1.20.1.jar`.
3. Put the jar into your Minecraft profile's `mods` folder.
4. Launch the Forge profile.

## Publishing Notes

If you publish a binary, publish the exact corresponding source from the same tree, commit, or tag used to build that binary. Do not publish a jar built from local uncommitted changes unless those exact changes are also provided as source.

## About

VulkanMod replaces Minecraft's default OpenGL renderer with a Vulkan-based renderer. This fork is experimental and currently targets Forge rather than Fabric.

For the official upstream project, use [xCollateral/VulkanMod](https://github.com/xCollateral/VulkanMod).
