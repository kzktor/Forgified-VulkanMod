# Forgified VulkanMod - VulkanMod Forge Unofficial Fork | VulkanMod Forge 非官方分支

This repository is an unofficial modified fork.
本仓库是非官方修改分支。

It has been modified for Minecraft 1.20.1 on Forge and is not the official VulkanMod project.
该项目已针对 Minecraft 1.20.1 Forge 进行移植，并非 VulkanMod 官方项目。

Prominent modification notice: this fork was ported to Forge 1.20.1 on 2026-08-22. It retains the original project's licensing and attribution.
重要修改说明： 本分支于 2026-08-22 移植至 Forge 1.20.1。项目保留原项目的许可证及署名信息。

## Attribution 署名

Original project: [xCollateral/VulkanMod](https://github.com/xCollateral/VulkanMod)
原始项目： [xCollateral/VulkanMod](https://github.com/xCollateral/VulkanMod)

Original authors and contributors: xCollateral and VulkanMod contributors.
原作者及贡献者：xCollateral 以及 VulkanMod 贡献者。

VulkanMod Reforged maintainer: Rindw.
VulkanMod Reforged 维护者: Rindw。

Fork maintainer:kzktor
分支维护者：kzktor

## Support 支持

-

## Features 功能

- Forge support for Minecraft 1.20.1. 支持 Minecraft 1.20.1 Forge。
- Vulkan-based renderer that replaces Minecraft's default OpenGL rendering path. 基于 Vulkan 的渲染器，用 Vulkan 渲染路径替代 Minecraft 默认的 OpenGL 渲染路径。
- Reduced CPU overhead through Vulkan rendering and optimized chunk submission. 通过 Vulkan 渲染以及优化区块提交，降低 CPU 开销。
- Chunk rendering and culling optimizations for smoother world rendering. 优化区块渲染与视锥体剔除，使世界渲染更加流畅。
- GPU selection and a revamped in-game graphics settings screen. 支持 GPU 选择，并重新设计了游戏内图形设置界面。
- Compatibility helpers for external render paths, GUI rendering, and OpenGL-style calls used by other mods. 提供兼容辅助功能，以支持外部渲染路径、GUI 渲染以及其他模组所使用的 OpenGL 风格调用。
- Performance preset and render scale options for easier tuning. 提供性能预设和渲染缩放选项，方便进行性能调节。

## License 许可证

This project remains licensed under the GNU Lesser General Public License version 3.0 only. See [LICENSE](LICENSE) for the LGPLv3 terms and [COPYING](COPYING) for the GPLv3 text referenced by the LGPLv3.
本项目继续采用 GNU Lesser General Public License version 3.0 only（LGPLv3） 许可证。 LGPLv3 的完整条款请参阅 [LICENSE](LICENSE) ，其中引用的 GPLv3 文本请参阅 [COPYING](COPYING) 。

The VulkanMod name, logos, CurseForge page, Modrinth page, Discord, and donation links belong to their respective original project owners. This fork is not endorsed by or affiliated with the upstream VulkanMod maintainers.
VulkanMod 的名称、Logo、CurseForge 页面、Modrinth 页面、Discord 以及捐赠链接均属于原项目的相应所有者。 本分支不代表 VulkanMod 官方，也未获得上游 VulkanMod 维护者的认可或授权。

## Installation 安装

1. Install [Forge](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html) for Minecraft 1.20.1. 为 Minecraft 1.20.1 安装 [Forge](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html) 。
2. Put the compiled jar into your Minecraft profile's `mods` folder. 将编译好的 .jar 文件放入版本对应的 mods 文件夹中。
3. Launch the Forge profile 启动游戏。

## Publishing Notes 发布说明

If you publish a binary, publish the exact corresponding source from the same tree, commit, or tag used to build that binary. Do not publish a jar built from local uncommitted changes unless those exact changes are also provided as source.
如果你发布二进制文件，请确保同时发布与该二进制文件完全对应的源代码，源代码应来自构建该二进制文件时所使用的相同代码树、Commit 或 Tag。 不要发布由本地未提交修改构建的 JAR，除非同时提供这些完全相同的修改后的源代码。

## About 关于

VulkanMod replaces Minecraft's default OpenGL renderer with a Vulkan-based renderer. This fork is experimental and currently targets Forge rather than Fabric.
VulkanMod 使用基于 Vulkan 的渲染器替代 Minecraft 默认的 OpenGL 渲染器。 该分支目前处于实验阶段，目标平台为 Forge，而不是 Fabric。

For the official upstream project, use [xCollateral/VulkanMod](https://github.com/xCollateral/VulkanMod).
如需使用官方上游项目，请前往 [xCollateral/VulkanMod](https://github.com/xCollateral/VulkanMod)。
