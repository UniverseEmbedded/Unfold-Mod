# Unfold Mod 功能对账（原版 vs 当前实现）

## 0. 口径与证据来源

- **原版**取三处：初始提交 `41c4a54`（`git show 41c4a54:<path>`）、仓库内 `doc/算法设计01.md`（2025-07-09，40 种瞬移与测试系统规程）、当前 `README.md`（对外描述）。
- **当前实现**以工作区 `25c719b` 为准。
- 行号指向当前工作区文件。
- 证据等级：`[源码]` = 本仓库可直接读到；`[历史]` = 只在 git 历史里存在；`[文档]` = 文档声明，代码中无对应实现。

## 1. 功能总表

| ID | 功能面 | 原版定义与证据 | 当前状态 | 当前证据 |
|---|---|---|---|---|
| I1 | 一键切换展开模式 | `41c4a54` UnfoldModClient 注册 `GLFW_KEY_F` [历史]；README 写「F」[文档] | **已实现，键位改为 V** | `UnfoldModClient.java:34-36` |
| I2 | 手动传送键 G | `41c4a54` 注册 `teleportKey = GLFW_KEY_G`，按下后 `findTeleportLocation()` 并发请求 [历史] | **已废弃**：设计规程明确「摒弃按键瞬移，瞬移是环境几何的自然结果」；当前无此键 | `doc/算法设计01.md:22`；当前 `UnfoldModClient.java:32-44` 只有 3 个键 |
| I3 | 相机缩放 `-` / `=` | 原版无 | **新增，已实现** | `UnfoldModClient.java:39-44,108-126` |
| I4 | 进入自动第三人称、退出恢复 | README 写「自动切换第三人称」[文档]；`41c4a54` 无 | **已实现（硬编码）** | `UnfoldModClient.java:165-166,180-182` |
| M1 | 斜向 ISOMETRIC | `doc/算法设计01.md:92-113` 统一二维行走+三维吸附 | **已实现** | `UnfoldTeleportLogic.java:290` `handleIsometricTeleport` |
| M2 | 斜向双辅助线与阴影安全区 | `doc/算法设计01.md`；README「阴影安全区」[文档] | **已实现** | `UnfoldTeleportLogic.java:293-381`（辅助线 1/2，偏移 0.01 / 0.5） |
| M3 | 水平 HORIZONTAL（地面穿越） | `doc/算法设计01.md:63-69` 4 条辅助线穿墙 + 向下找连续平台 | **部分实现**：候选改为沿 ±d 的 `LayerRaycaster` 区间 + 完整碰撞箱复核，未保留原「4 条侧向辅助线」的取点方式 | `UnfoldTeleportLogic.java:189-198,722-746` |
| M4 | 水平模式跳跃 / 边缘下落 / 空中侧跨 | `doc/算法设计01.md:70-88` 三种触发 | **已实现，但改挂到垂直/斜向兜底**：水平基 `d⊥Y` 下这三条恒无候选 | `UnfoldTeleportLogic.java:216-236,92-97` |
| M5 | 垂直 VERTICAL（层间升降） | `doc/算法设计01.md:36-55` 4 条边中点射线、上/下 8 种 | **已实现** | `UnfoldTeleportLogic.java:151-187,627-638`（4 个边中点探针）、`650,674,814` |
| M6 | 12 个预设视角方向 | README「8 个斜向 45 度」[文档] | **已实现，扩展为 8 斜 + 4 水平 + 2 垂直** | `ViewDirection.java:5-24` |
| M7 | 模式判定（pitch 耦合） | 原版无 `UnfoldMode` | **新增，已实现** | `UnfoldMode.java:18-32`；`UnfoldManager.java:13` |
| T1 | 投影不变量 Π(p)=Π(p_desired) 复核 | `doc/算法设计01.md:99-105` | **已实现（客户端第 8 步）** | `UnfoldTeleportLogic.java:606-624` |
| T2 | Vanilla 优先（本 tick 原版已满足则不介入） | 原版无 | **新增，已实现** | `UnfoldTeleportLogic.java:118-145` |
| T3 | 层序号单调滞回 | 原版无 | **新增，已实现** | `UnfoldTeleportLogic.java:55-70`（`jumpLayerDirection`） |
| T4 | 水平同层正/反向重穿抑制 | 原版无 | **新增，已实现** | `UnfoldTeleportLogic.java:558-604` |
| T5 | 客户端预测 + 服务端权威 | `41c4a54` 只有 C2S 请求、服务端仅距离检查 | **已扩展，已实现** | `UnfoldTeleportLogic.java:402-436`；`UnfoldMod.java:32-66` |
| T6 | 服务端终点校验（7 步） | 原版仅 `distance > 100` [历史] | **已实现** | `TeleportTargetValidator.java`；`UnfoldMod.java:52-58` |
| T7 | 拉回观测（权威位置包） | 原版无 | **新增，已实现** | `ClientPlayNetworkHandlerMixin.java:14-46` |
| T8 | 请求限流（每 tick 1 次、在途不重发、冷却） | 原版无 | **新增，已实现** | `UnfoldModClient.java:66-96`；`UnfoldManager.java:37,44,50-56` |
| R1 | 正交投影矩阵 | 原版有 | **已实现** | `GameRendererMixin.java:28-37` |
| R2 | 相机视角锁定 | 原版有 | **已实现** | `CameraMixin.java:49` |
| R3 | 禁用视锥剔除 | 原版有 | **已实现** | `WorldRendererMixin.java:16-22` |
| D1 | `/unfold test <name|all>` 测试框架 | README 记载；`29937d5` 起存在 14 个 `test/tasks/*` + `TestCommand` [历史] | **缺失**：当前仓库无 `command/`、`test/` 目录 | 当前 `src/**` 文件清单无对应类 |
| D2 | 入口过渡时间戳 `transitionStartTick` | `41c4a54` 有 `transitionStartTick = System.currentTimeMillis()/50` [历史] | **已废弃**：当前 `UnfoldManager` 无该字段，渲染无过渡动画 | `UnfoldManager.java:8-63` |
| D3 | `ExampleClientMixin` 空注入 | `41c4a54` 存在 [历史] | **已删除（模板残留）** | 当前 `src/client/.../mixin/client/` 无该类 |
| D4 | `ExampleMixin` 空注入 `MinecraftServer.loadWorld` | `41c4a54` 存在 | **仍存在（模板残留，空方法）** | `ExampleMixin.java:11-16` |
| E1 | `PlayerTeleportCallback` 扩展事件 | 原版无 | **新增，已实现**（每次切层触发；仓内无订阅者，仅对外扩展点） | `UnfoldTeleportLogic.java:423`；`PlayerTeleportCallback.java:8-18` |

分类合计 27 项：已实现 20（含 1 项键位变更 I1）、部分实现 2（M3、M4）、缺失 1（D1）、已废弃 3（I2、D2、D3）、模板残留 1（D4）。

## 2. 配置项核对（`UnfoldConfig`）

| 字段 | 声明 | 是否被读用 | 读取处 | 结论 |
|---|---|---|---|---|
| `scale` | `UnfoldConfig.java:7` | 是 | `GameRendererMixin.java:32`；`UnfoldModClient.java:123`；`UnfoldConfig.java:29-34` | 正常 |
| `MIN_SCALE` | `:11` | 是 | `UnfoldConfig.java:34` | 正常 |
| `MAX_SCALE` | `:13` | 是 | `UnfoldConfig.java:34` | 正常 |
| `SCALE_STEP_MULTIPLIER` | `:15` | 是 | `UnfoldConfig.java:29,31` | 正常 |
| `MIN_DISTANCE` | `:18` | 是 | `GameRendererMixin.java:37` | 正常 |
| `MAX_DISTANCE` | `:19` | 是 | `GameRendererMixin.java:37` | 正常 |
| `AUTO_THIRD_PERSON` | `:20` | **否** | 全仓仅声明处出现 | **声明未用**：第三人称切换在 `UnfoldModClient.java:165-166` 硬编码，把该常量设为 `false` 不会生效 |

## 3. 网络包对比

| 包 | 方向 | 原版 | 当前 | 证据 |
|---|---|---|---|---|
| `TeleportRequestPayload` | C2S | 有 | 有 | `TeleportRequestPayload.java`；`UnfoldMod.java:22` |
| `TeleportResponsePayload` | S2C | 无 | **新增**（带 `RejectionReason` 枚举回执） | `TeleportResponsePayload.java`；`UnfoldMod.java:23` |
| 其他包 | — | 无 | 无 | 当前 `network/` 只有上述两个文件 |

结论：没有丢包。原版唯一的 C2S 包仍在，新增了 S2C 回执。

## 4. 缺失项分类

### A. 可直接做（改动明确、风险低）

- **A1 README 键位过时**：README 写「按 F 切换」，实际是 V（`UnfoldModClient.java:35`）。只需改文档或键位，二选一，不涉及逻辑。
- **A2 `AUTO_THIRD_PERSON` 未读**：把 `UnfoldModClient.java:165-166` 的第三人称切换包在该常量下（进入与退出各一处判断）。一行改动即可让声明生效。

### B. 需先定语义或成本较高

- **B1 `/unfold test` 测试框架**：原版有 `TestCommand` + `TestRunner` + 13 个任务类（历史 `29937d5`），当前全删。恢复需要重建命令注册、任务调度、场景搭建与断言体系，且要按新架构（服务端权威切层）重写测试用例。属独立工程，不宜顺手补。
- **B2 水平模式 L2 完整度**：原设计含「跳跃到障碍物上方 / 高台边缘下落 / 空中侧跨」，当前这三条已从水平模式移出（水平基下恒无候选），改挂垂直/斜向兜底。若要恢复水平模式内的表现，需先确定它们在 `d⊥Y` 下的语义（是水平模式专属动作，还是仅垂直/斜向可用），再决定是否新增水平专用路径。

## 5. 明显问题（只记录，未动手）

1. `AUTO_THIRD_PERSON` 声明未用（见 §2）。
2. README 的键位（F）与实际（V）不一致；README 称有 `/unfold test`，仓库中不存在。
3. `ExampleMixin.java` 是模板残留：`MinecraftServer.loadWorld` HEAD 注入空方法，无任何行为。

## 6. 未验证

本对账基于源码阅读与 git 历史，未编译、未测试、未启动游戏。运行时行为（尤其水平/垂直模式的实际手感与联机回执）仍需按 `docs/MULTIPLAYER_ACCEPTANCE_CHECKLIST.md` 实地验收。
