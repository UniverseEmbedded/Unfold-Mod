# runClient 实测记录（2026-11-03）

## 环境盘点（只读）

- JDK：`java -version` = GraalVM 24.0.2。本机 `~/.jdks` 只有 17、23、24、25，无 21。Gradle 8.14.2 以 JDK 24 启动；`build.gradle` 的 source/target 为 21；Fabric 日志记录 `java 24` 与 `Compatibility level set to JAVA_21`。
- Minecraft 1.21.5：Loom 缓存 `~/.gradle/caches/fabric-loom/1.21.5/` 下 `minecraft-client.jar`、`minecraft-client-only.jar`、`minecraft-merged.jar` 均存在；`fabric-loom/assets` 的 `indexes`、`objects` 存在。
- Fabric：loader 0.16.14，fabric-api 0.128.1+1.21.5。
- 离线包目录（`D:\pama1234\pfp\p-2026-01\dim3d\temp\git-bundles\bundles`）：`mc-mod-packs.zip`、forge/neoforge 源码包（1.20.1 / 1.21.11 / 26.2）、`v1建筑mod/cityunbuilt` 源码。没有 Fabric 1.21.5 运行时或客户端包，与本 mod 无直接关系。
- 依赖：本次 `runClient` 未出现依赖缺失。日志中唯一错误是 Realms 认证失败（开发环境没有正版会话），单人流程不受影响。

## 已跑出来

`gradlew runClient` 启动成功，55 个 mod 加载，含 `unfold 1.0.0`。

关键日志：

```
[13:07:49] [main/INFO] (FabricLoader/GameProvider) Loading Minecraft 1.21.5 with Fabric Loader 0.16.14
	- java 24
	- minecraft 1.21.5
	- unfold 1.0.0
[13:07:50] [main/INFO] (FabricLoader/Mixin) Compatibility level set to JAVA_21
[13:08:09] [Render thread/INFO] (unfold) Unfold Mod initialized.
[13:08:10] [Render thread/INFO] (Minecraft) Backend library: LWJGL version 3.3.3-snapshot
[13:08:17] [Render thread/INFO] (Minecraft) OpenAL initialized on device OpenAL Soft on 扬声器 (2- Senary Audio)
[13:08:18] [Render thread/INFO] (Minecraft) Created: 1024x512x4 minecraft:textures/atlas/blocks.png-atlas
```

- 无 Mixin 报错，无客户端崩溃，全部纹理图集创建完成。
- 进程 4264，窗口标题 `Minecraft* 1.21.5`，启动后持续运行。

## 未知

进入世界后按 V 的实际画面未观测。当前环境没有向游戏窗口发送按键或截图的能力，这一步需要在窗口里人工完成。

## 运行时注意

LWJGL 在 JDK 24 下输出一条 `[ThreadLocalUtil] Unsupported JNI version detected, this may result in a crash`。本次未因此崩溃，但它记录在日志里。
