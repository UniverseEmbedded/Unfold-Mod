好的，根据你提供的34个项目文件，我详细分析了该项目的架构、核心功能、技术实现和现有文档，为你生成了一份全面且专业的 `README.md` 文档。

这份文档不仅整合了你原始 `README.md` 的优秀内容，还根据源代码的实际逻辑进行了深化、修正和补充，使其更加准确和详尽。

---

# Unfold Mod

[![Minecraft Version: 1.21.5](https://img.shields.io/badge/Minecraft-1.21.5-brightgreen.svg)](https://www.minecraft.net/)
[![Fabric API](https://img.shields.io/badge/Requires-Fabric%20API-blue.svg)](https://fabricmc.net/use/)

**Unfold Mod** 是一款为 Minecraft 引入全新空间解谜维度的 Fabric 模组。它深受《纪念碑谷》和《FEZ》等经典益智游戏的启发，允许你将三维的 Minecraft 世界“展开”成一个二维的正交视图。

在这个特殊的“展开模式”下，世界的深度感消失了，透视不复存在。你可以利用这种视错觉，通过基于上下文的自动传送机制，在看似遥远但视觉上相连的平台之间穿梭，探索和解决基于视觉错位的谜题。

![GIF/截图展示 (建议在此处放置一个展示Mod核心玩法的GIF动图或截图)](https://raw.githubusercontent.com/pama1234/Unfold/master/run/demo/unfold_demo.gif)

---

## 核心特性 (Core Features)

### 1. 展开模式 (Unfold Mode)
- **一键切换**: 按下默认按键 **`F`**，即可在标准的三维透视视角和独特的二维正交视角之间无缝切换。
- **智能对齐**: 进入模式时，视角会自动对齐到14个预设的固定角度之一（8个斜向45度角，6个标准正交角），为你呈现一个完美的、类似建筑蓝图的视图。
- **沉浸式体验**: 自动切换到第三人称视角，以获得最佳的全局观察视野。退出时恢复原始视角。

### 2. 视错觉传送 (Optical Illusion Teleportation)
本模组的核心玩法。在“展开模式”下，你不再需要手动瞄准传送，系统会根据你的行为意图自动判断并执行传送：
- **空中跨越 (Air Strafe)**: 当你在空中向某个方向移动时，如果视错觉让你看起来能“走”到另一个平台，系统会自动将你传送到该平台上方。这是实现《FEZ》风格跳跃的核心。
- **信仰之跃 (Leap of Faith)**: 从平台边缘走下或潜行，如果下方在二维视角内存在可落脚的平台，你将被安全地传送到那里。
- **向上跳跃 (Vertical Jump)**: 当你向上跳跃时，如果头顶有更高的平台，你可能会被直接传送到该平台上。

### 3. 多视角模式 (Multiple View Modes)
根据你进入展开模式时的视角，系统会自动切换到最合适的模式：
- **水平模式 (Horizontal Mode)**: 当你平视时进入，提供类似《FEZ》的侧向卷轴平台体验。
- **垂直模式 (Vertical Mode)**: 当你俯视或仰视时进入，提供顶视或底视的平面视图。
- **斜向模式 (Isometric Mode)**: 当你从斜向角度进入时，提供类似《纪念碑谷》的45度等轴测视图。

### 4. 流畅的视觉体验 (Smooth Visual Experience)
- **稳定正交投影**: 通过深度定制渲染管线，本Mod实现了稳定、无闪烁的正交投影。
- **完整场景渲染**: 在展开模式下，原生的视锥体剔除（Frustum Culling）被智能禁用，确保了所有加载的场景都能被完整渲染，避免了因原生剔除算法与正交投影不兼容而导致的方块消失问题。
- **操作保护**: 内置的按键防抖（Debounce）机制可以防止因快速切换模式而引发的游戏异常，保证了流畅的操作体验。

---

## 如何游玩 (How to Play)

1.  **安装**: 确保你已安装 [Fabric Loader](https://fabricmc.net/) 和 [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)。然后将Mod的 `.jar` 文件放入你的Minecraft实例的 `mods` 文件夹中。
2.  **进入游戏**: 加载一个世界（推荐创造模式以搭建有趣的解谜场景）。
3.  **切换模式**: 按下 **`F`** 键，世界将在你眼前“展开”。
4.  **观察与探索**: 在普通模式下旋转你的角色来改变你进入展开模式时的初始角度，寻找那些因视角变化而产生的奇妙路径。
5.  **触发传送**:
  - 走到平台边缘，然后继续向前走。
  - 助跑并跳向一个在视觉上相邻但实际很远的平台。
  - 尝试各种移动方式，探索视错觉带来的可能性！

---

## 技术细节 (Technical Deep Dive)

本Mod主要是一个客户端模组，其核心技术点包括：

-   **渲染管线注入 (Rendering Pipeline Injection)**: 使用Mixin深度注入到`GameRenderer`、`WorldRenderer`和`Camera`类。
  -   `GameRendererMixin`: 将原生的透视投影矩阵（Projection Matrix）替换为自定义的正交投影矩阵。同时，强制锁定摄像机旋转以匹配预设的 `ViewDirection`。
  -   `CameraMixin`: 锁定摄像机的Yaw和Pitch，并禁用鼠标对视角的控制，确保视角在展开模式下绝对稳定。
  -   `WorldRendererMixin`: 通过在`setupTerrain`方法的开头注入并取消后续代码执行，完全绕过原生视锥体剔除逻辑，从根源上解决了正交投影下的区块渲染问题。

-   **传送逻辑 (Teleportation Logic)**:
  -   `UnfoldTeleportLogic`类是整个玩法的核心。它在客户端的每一帧（`ClientTick`）中运行。
  -   该逻辑会根据玩家的当前状态（如是否在地面、是否在跳跃、速度向量等）和当前的 `UnfoldMode`，从玩家周围发射多条虚拟的射线（Raycast）。
  -   通过分析射线检测的结果，判断是否存在一个符合视错觉传送条件的安全目标平台，如果存在，则直接在客户端修改玩家位置 (`player.setPos`) 实现传送。

-   **自动化测试框架 (Automated Test Framework)**:
  -   项目内置了一个强大的客户端测试框架，可通过指令 `/unfold test <test_name|all>` 启动。
  -   `TestRunner` 类负责管理和执行一系列 `TestTask`，如场景搭建 (`BuildPlatformTask`)、模拟玩家移动 (`SimulateMoveTask`) 和结果断言 (`AssertPositionTask`)。
  -   这套框架极大地保证了核心传送逻辑的稳定性和正确性。

-   **网络通信 (Networking)**:
  -   虽然当前传送逻辑主要在客户端实现，但项目已定义了 `TeleportRequestPayload`，为未来将传送逻辑迁移到服务器端进行验证预留了接口，以增强多人游戏环境下的兼容性和安全性。

---

## 安装 (Installation)

### 普通用户

1.  下载并安装 [Fabric Loader](https://fabricmc.net/use/installer/)。
2.  下载 [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api) 并将其放入 `mods` 文件夹。
3.  从 [Releases 页面](https://github.com/pama1234/Unfold/releases) 下载本Mod的最新 `.jar` 文件。
4.  将下载的 `.jar` 文件放入你的 Minecraft 游戏目录下的 `mods` 文件夹中。
5.  启动游戏。

### 开发者 (Building from Source)

1.  克隆本仓库: `git clone https://github.com/pama1234/unfold-mod-template-1.21.5.git`
2.  进入项目目录: `cd unfold-mod-template-1.21.5`
3.  构建项目:
  -   Windows: `gradlew build`
  -   Linux / macOS: `./gradlew build`
4.  构建完成的jar包位于 `build/libs/` 目录下。

---

## 鸣谢 (Credits)

-   本Mod的开发过程受到了 **OrthoCamera** Mod 的启发，特别是在正交矩阵的参数设置上。感谢原作者为社区提供的宝贵参考。
-   感谢所有为Fabric和Minecraft Mod社区做出贡献的开发者。
-   感谢 **Gemini 2.5 Pro** 协助分析代码和生成文档。

---

## 许可 (License)

保留所有权力。