# Unfold Mod 算法规格（A1–A5 汇总）

本文把散在代码注释与答复文档里的 A1–A5 推导整理成一份可独立评审的规格。读者不需要先读代码，就能判断每一步是否自洽；需要核对实现时，再按文中给出的落点回查。

证据等级约定：

- **已证实**：能指向仓库内的实现（文件名与方法名），或能指向静态推导（代数/字节码/数值计算）。不要求运行过游戏。
- **推断**：设计取舍、未做运行时验证的结论，或数值选用的合理性判断。推断不等于错误，但评审时应按推断对待。

本文只描述现状，不提出新的行为，也不修改任何代码。

## 0. 实现落点

| 内容 | 位置 |
| --- | --- |
| 投影基 d、e_u、e_r、Π、λ | `src/main/java/pama1234/unfold/util/ProjectionBasis.java` |
| 层区间枚举（DDA） | `src/main/java/pama1234/unfold/logic/LayerRaycaster.java` |
| 候选搜索与客户端 8 步判定 | `src/client/java/pama1234/unfold/logic/UnfoldTeleportLogic.java` |
| 客户端预测、回执分流、限流 | `src/client/java/pama1234/unfold/UnfoldModClient.java` |
| 权威位置包观察（拉回） | `src/client/java/pama1234/unfold/mixin/client/ClientPlayNetworkHandlerMixin.java` |
| 服务端 7 步判定与拒绝原因 | `src/main/java/pama1234/unfold/logic/TeleportTargetValidator.java` |
| 请求接收、服务端授权 | `src/main/java/pama1234/unfold/UnfoldMod.java` |
| 回执协议 | `src/main/java/pama1234/unfold/network/TeleportResponsePayload.java` |
| 在途预测与限流状态 | `src/main/java/pama1234/unfold/logic/UnfoldManager.java` |

以上路径与职责划分（已证实）。

## 1. 记号与定义

### 1.1 位置

- `p_desired`：本 tick 原版移动与碰撞处理结束后的玩家脚底位置。（已证实：`UnfoldTeleportLogic.checkAndPerformTeleport` 在 `END_CLIENT_TICK` 调用，此时 `player.getPos()` 已包含本 tick 的原版结果。）
- `p_c`：候选位置，即最终可能提交给服务端的落点。（已证实：`tryCandidate` / `tryHorizontalCandidate` 的返回值。）
- 判定与重建统一以脚底为锚点；碰撞箱由“实体当前碰撞箱 + (候选 − 实体当前脚底)”平移得到。（已证实：`TeleportTargetValidator.validateEndpoint`。）

### 1.2 展开轴 d

`d` 是按当前预设视角的 yaw/pitch 计算的 Minecraft 相机前向，并归一化：

```
d = normalize( -sin(yaw)·cos(pitch), -sin(pitch), cos(yaw)·cos(pitch) )
```

（已证实：`ProjectionBasis` 构造器。）

各模式的 d 性质：

| 模式 | 视角预设 | d 的性质 |
| --- | --- | --- |
| HORIZONTAL | pitch = 0°（东/西/南/北） | `d.y = 0`，即 d⊥Y（已证实：代入公式） |
| VERTICAL | pitch = ±90°（POS_Y / NEG_Y） | `d = (0, ∓1, 0)`，即 d∥Y（已证实） |
| ISOMETRIC | pitch = ±35.26438968° | `|d.y| = sin(35.26438968°) ≈ 0.57735 = 1/√3`（已证实：数值计算） |

`35.26438968°` 是 arctan(1/√2) 的精确值，初始提交里手写为 35.26；仓库没有“从游戏实测得到该角度”的记录。（已证实：`ProjectionBasis.ISO_PITCH_DEGREES` 注释与提交历史。）它与相机实际成像是否完全一致，未做运行时验证。（推断）

### 1.3 屏幕基 e_u、e_r

```
e_u = normalize( Y − (Y·d)d ),  Y = (0,1,0)
e_r = normalize( d × e_u )
```

- `e_u` 是屏幕上的方向。当 d∥Y 时上式退化（分母为零），实现改取该 yaw 对应的水平前向。（已证实：代码分支；该退化选择本身是推断。）
- `e_r` 就是相机的屏幕右方向，不需要反号。依据是 1.21.5 的 `Camera.setRotation = rotationYXZ(π − yaw, −pitch, 0)`、`GameRenderer` 取共轭构造视图矩阵（纯旋转、无镜像），可推出 `R·(1,0,0) = (−cos yaw, 0, −sin yaw) = d × e_u`。（已证实：字节码静态推导。）该结论没有与实机画面逐帧比对过。（推断）

由构造可直接得到 `e_u·d = 0`、`e_r·d = 0`，因此 `Π(d) = (0,0)`。（已证实：内积展开。）

### 1.4 投影 Π、自由量 λ 与不变量

```
Π(p) = ( p·e_r , p·e_u ) ∈ R²
λ    = ( target − anchor )·d
p(λ) = anchor + λ d
```

- 不变量表述：**候选必须满足 `Π(p_c) = Π(p_desired)`**。（已证实：规格 A3，实现见 `ProjectionBasis.preservesProjection`。）
- 该比较使用平方距离与容差 `1e-6`，容差只用于浮点比较，不作为允许的位移量。（已证实：代码注释与实现。）
- 因为 Π 是二维线性映射且 `e_u`、`e_r` 线性无关（仅在 d∥Y 的退化分支需另行取 e_u），Π 的核是一维且包含 `span(d)`；结合既有的 Π 值，满足不变量的点集是仿射直线 `p_desired + span(d)`。（已证实：线性代数推导。）因此候选只可能写成 `p_desired + λd`，λ 是唯一未知量。（已证实）
- `1e-6` 是否足以容纳双精度累积误差，未做误差分析。（推断：按数量级判断足够。）

## 2. 候选集

### 2.1 两种模式共用的候选线

垂直/斜向与水平模式的候选都只由 `p_desired + λd` 重建，区别只在“λ 从哪里来”。（已证实：`candidateOnLayer` 与 `candidateAtLambda` 都调用 `ProjectionBasis.reconstruct`。）

这样做的理由：沿 d 的位移在 Π 下不可见，因此它不改变屏幕位置；其它方向的位移都会改变 Π 分量，无法同时满足不变量。也就是说，“切层”等于“在屏幕上不可见的深度方向换一层”。（已证实：由 1.4 的核结论。）

### 2.2 垂直 / 斜向：λ 由层顶面 Y 反解

流程：

1. 用 `LayerRaycaster` 沿 ±Y（垂直模式）或沿 ±d（斜向模式）枚举沿途每个方块碰撞形状的区间 `[tEntry, tExit]`，不取首个命中。（已证实：`handleVerticalTeleport`、`handleIsometricTeleport`。）
2. 层证据的站立高度 `feetY` 取该方块碰撞形状的实际顶面（非满方块按包围盒最大值）。（已证实：`layerTopY`。）
3. 用 `λ = (feetY − p_desired.y) / d.y` 反解自由量，再令 `p_c = p_desired + λd`。（已证实：`candidateOnLayer`。）
4. 当 `d.y ≈ 0`（水平模式）时该式不可用，直接返回无候选，改走 2.3。（已证实：`canChangeWorldY`。）
5. 重建后立即做 `Π` 硬检查（`1e-6`）。（已证实）

`isSafePlatform` 的“站立处与上方一格为空气”只作前置粗筛，不能当作安全证明；最终由完整碰撞箱判定承担。（已证实：代码注释与调用顺序。）

垂直模式下探测起点取玩家水平四角，向外偏移半个宽度加 `EPSILON`，以保证射线从碰撞箱外出发。（已证实：`getVerticalProbeOrigins`。）

### 2.3 水平：λ 由沿 d 的层区间给出

水平模式 `d⊥Y`，沿 d 无法改变世界 Y，层顶面也不能反解 λ。因此：

1. 沿 +d 与 −d 各发一条长度 `MAX_DISTANCE` 的射线，得到按 `tEntry` 升序的区间列表。（已证实：`checkHorizontalWalk`。）
2. 取该区间的 `tExit`，加上越层余量 `clearance = 0.5·width·(|d.x|+|d.z|) + EPSILON`，得到 `λ = ±(tExit + clearance)`。（已证实：`tryHorizontalCandidate`。）
3. 用 `p_desired + λd` 重建候选并做 Π 硬检查。（已证实：`candidateAtLambda`。）
4. 候选的世界 Y 与 `p_desired` 相同；脚底高度 `feetY` 直接取候选自身的 Y。（已证实：调用处传入 `candidate.y`。）
5. 候选必须在触发方向（玩家实际水平速度方向）上留出 `0.2` 格前瞻余量，否则拒绝。（已证实：`tryHorizontalCandidate` 的横向碰撞箱偏移。）

区间只确定切层参数，射线不参与屏幕位移；这是“候选只由 `p_desired + λd` 重建”的直接结果。（已证实：实现；设计意图见 `candidateAtLambda` 注释。）

同一层在相邻两 tick 内先 + 后 − 的反向重穿会被抑制，抑制半径为 2.5 格。（已证实：`isReversedHorizontalCut`；2.5 的取值是推断，见第 5 节。）

### 2.4 触发方向与候选方向

水平模式的移动速度由 `strafeAxis = d × Y` 给出，与 d 垂直，速度在 d 上没有分量。因此：

- 若把触发条件限定为“沿 d 即将碰撞”，水平地面穿越永远不会触发。（已证实：`handlePlayerMovement` 的赋值与 `isAboutToCollideHorizontally` 注释。）
- 实现改为按任意水平速度方向触发，但仍只在 `p_desired + λd` 上搜索；代价是触发面变宽，且横向余量只是候选处的静态检测，不验证从原位到候选的横向路径。（已证实：代码注释；代价评估为推断。）

## 3. 合法性判定：客户端 8 步、服务端 7 步

两边都以实体当前 pose 的完整碰撞箱、平移到候选位置后判定。客户端实现见 `UnfoldTeleportLogic.isCandidateClear`，服务端见 `TeleportTargetValidator.validateEndpoint`，两端的第 1–7 步共用同一份代码与同一组常量。（已证实）

### 3.1 客户端 8 步

| 步 | 检查 | 失败返回 |
| --- | --- | --- |
| 1 | 碰撞箱在世界合法高度范围内 | false |
| 2 | 碰撞箱在世界边界内 | false |
| 3 | `isSpaceEmpty(entity, box)` 主判据 | false |
| 4 | `getBlockCollisions` 无实际方块碰撞形状 | false |
| 5 | `getEntityCollisions` 无实体重叠 | false |
| 6 | 能 `findSupportingBlockPos`，且支撑面顶面与 `feetY` 之差 ≤ `1e-4` | false |
| 7 | 碰撞箱底部与 `feetY` 之差 ≤ `1e-4` | false |
| 8 | 投影不变量 `Π(p_c) = Π(p_desired)`，容差 `1e-6` | false |

（第 1–7 步已证实：`TeleportTargetValidator.validateEndpoint` 与客户端委托调用；第 8 步已证实：`isCandidateClear`。）

第 6 步的支撑查询只作辅助，但“查不到支撑”同样不能放行；不能把 `findSupportingBlockPos` 单独当作安全证明。（已证实：代码注释。）

### 3.2 服务端 7 步

服务端顺序与客户端第 1–7 步逐条相同，第 6/7 步的失败原因统一映射为 `NO_SUPPORT`，第 1–5 步映射为 `COLLISION`。（已证实：`TeleportTargetValidator`。）

服务端在进入 7 步之前还有一道距离粗界（`MAX_REQUEST_DISTANCE`），它不是 A5 的一部分，只用于拦截明显不是切层的请求。（已证实：`UnfoldMod` 接收器。）

世界状态查询与 `player.teleport` 一并放在服务器主线程执行，避免在网络线程读方块碰撞。（已证实：`player.getServer().execute`。）

### 3.3 两步差异与依据

- **差异**：服务端没有第 8 步 Π 复核。（已证实）
- **依据**：Π 依赖 `ProjectionBasis` 与客户端当前展开方向；服务端没有展开方向，也不应拿客户端的投影参数去否决一个位置本身合法的落点。第 8 步是“预测一致性”约束，不是“这个世界是否允许站人”的约束。（推断：设计判断。）
- **后果**：同一算法在两端的输入不一定同一时刻同一。客户端在 `END_CLIENT_TICK` 用客户端世界，服务端在处理包时用服务端世界；区块加载、实体位置、tick 对齐都可能不同，因此仍可能出现“客户端通过、服务端拒绝”。共享代码只消除“同一输入下判定不同”的分叉，不消除输入差异。（推断）
- **失败粒度**：客户端对外只返回布尔，拒绝原因只有服务端回执携带；客户端内部没有把步骤拆成枚举。（已证实：`isCandidateClear` 与方法签名。）

## 4. 权威链

### 4.1 客户端发起

1. `teleportPlayer` 先做本地预测：`setPos(p_c)`，把垂直速度清零，记录 `pendingTeleportTarget / pendingTeleportOrigin / pendingTeleportTicks`，设置请求间隔冷却，然后发送 `TeleportRequestPayload(p_c)`。（已证实：`UnfoldTeleportLogic.teleportPlayer`。）
2. 限流三道：每 tick 最多一次 `checkAndPerformTeleport`；上一请求未确认（`pendingTeleportTarget != null`）不重发；请求间隔冷却 `2 tick` 未清零不重发。既有的 `teleportCooldown` 仍在落地时解除。（已证实：`UnfoldModClient` 的 tick 逻辑。）
3. 本地位置只是预测，不是权威结果。（已证实：代码注释与后续回执处理。）

### 4.2 服务端处理

1. 网络线程先做距离粗界；超限立即回执 `DISTANCE_EXCEEDED`。（已证实：`UnfoldMod`。）
2. 主线程做 7 步终点校验；失败回执对应 `COLLISION` 或 `NO_SUPPORT`，不执行传送。（已证实）
3. 通过则 `player.teleport(p_c)` 落权威位置，再回执 `NONE`；先落位再回执，保证接受回执到达时服务端位置已是目标点。（已证实）

### 4.3 回执与三种分流

`TeleportResponsePayload` 不再只回布尔，而是携带枚举 `RejectionReason`：`NONE` / `DISTANCE_EXCEEDED` / `NO_SUPPORT` / `COLLISION`。协议只传枚举序号的 varint，读取端对越界序号做边界检查并按 `COLLISION` 处理。（已证实：`TeleportResponsePayload`。）

客户端按原因分三路处置（已证实：`UnfoldModClient.handleTeleportResponse`）：

| 原因 | 本地处置 | 依据 |
| --- | --- | --- |
| `NONE` | 清空在途预测，不回写位置；等待权威位置包 | 服务端已落位 |
| `DISTANCE_EXCEEDED` / `COLLISION` | 回退到 `pendingTeleportOrigin` | 位置不可接受，不能停在越界或几何体内 |
| `NO_SUPPORT` | 保持预测位置，丢弃在途预测 | 位置无碰撞但无支撑，交给重力与权威位置包纠正 |

三种拒绝都会重置请求间隔冷却。（已证实：`discardPendingTeleport`。）

`NO_SUPPORT` 选择“保持”而不是“回退”的理由：预测点在几何上是空的，直接瞬回 origin 会在同一 tick 内造成来回跳；让它自然下落更连续。（推断。实际抖动与连续拒绝的风险未实测。）

### 4.4 第三种纠正：位置包拉回

服务端还有一条与回执无关的纠正路径：原版 `ServerPlayNetworkHandler.onPlayerMove` 在判定 `moved too quickly` / `moved wrongly` 后，用 `requestTeleport` 发 `PlayerPositionLookS2CPacket`。客户端在 `onPlayerPositionLook` 的 TAIL 比较最终位置与在途预测：等于预测目标则视为接受并清空；落在别处则丢弃预测。（已证实：`ClientPlayNetworkHandlerMixin` 与其注释。）

原版服务端的具体行为（静态分析）：

- 对移动包做位移检查，超阈值判 `moved too quickly`；专用服生效，单人/局域网因 `isHost` 跳过该检查。（已证实：1.21.5 字节码分析；原版阈值不在本仓库常量表内，也未在联机中实测。）
- 位置残差检查判 `moved wrongly`，即使 `isHost` 跳过前者，后者仍可能把客户端拉回。（已证实：字节码分析；实机表现推断。）
- 拉回采用 `requestTeleport`，并有约 `20 tick` 的重发窗口。（已证实：字节码分析。）

**拉回后为什么不回写目标位置**：TAIL 注入点位于位置包已写入玩家实体之后，此时本地位置就是服务端权威值；若再把 `pendingTeleportTarget` 写回去，等于当场撤销服务端的纠正，下一 tick 很可能再次被判 `moved wrongly`，形成“请求—拉回—再写回”的循环。此外拉回意味着这次请求没有被接受，`pending` 已失效，回写没有对应的授权。（已证实：包应用时序与代码行为；循环后果为推断。）

### 4.5 历史状态

`TeleportRequestPayload` 的接收器曾经注册但客户端从未发送，切层只有本地 `setPos` 加普通移动包；从提交 `b3e59cb` 起客户端才真正发送请求。（已证实：`UnfoldMod` 接收器上方的历史注释与提交记录。）

## 5. 全部常量与依据

| 常量 | 值 | 位置 | 依据 | 标签 |
| --- | --- | --- | --- | --- |
| `MAX_DISTANCE` | `64.0` | `UnfoldTeleportLogic` | 客户端射线最大长度；层证据与水平 λ 都在此范围内取值 | 值已证实；取 64 的理由未记录，推断 |
| `MAX_REQUEST_DISTANCE` | `256.0` | `TeleportTargetValidator` | 服务端距离粗界，拦伪造包 | 值已证实；只作粗界的定位推断 |
| `SURFACE_TOLERANCE` | `1.0e-4` | `TeleportTargetValidator`（客户端引用同一常量） | 脚底与支撑面顶面高度比较 | 值已证实；是否足以吸收浮点误差推断 |
| `PROJECTION_TOLERANCE` | `1.0e-6` | `UnfoldTeleportLogic` | Π 不变量数值比较，不作允许位移 | 值已证实；量级判断推断 |
| `AUTHORITY_TOLERANCE` | `1.0e-4` | `ClientPlayNetworkHandlerMixin` | 权威位置与预测目标的比对 | 值已证实；远小于任一拉回位移，推断 |
| `TELEPORT_REQUEST_INTERVAL_TICKS` | `2` | `UnfoldManager` | 两次请求之间至少隔一个服务端 tick，避免同 tick 叠发 | 值已证实；时序解释为推断 |
| `PENDING_TELEPORT_TIMEOUT_TICKS` | `20` | `UnfoldManager` | 与服务端 `handlePendingTeleport` 的重发窗口对齐 | 值已证实；对齐合理性推断 |
| `REVERSE_CUT_RADIUS` | `2.5` | `UnfoldTeleportLogic` | 同层反向重穿的抑制半径 | 推断（代码注释自标） |
| `LATERAL_LOOKAHEAD` | `0.2` | `UnfoldTeleportLogic` | 触发前瞻与候选处横向余量 | 值已证实；0.2 的取值推断 |
| `EPSILON`（几何） | `0.01` | `UnfoldTeleportLogic` | 越层余量与探针外扩的浮点垫量 | 值已证实；取值推断 |
| `MIN_HORIZONTAL_SPEED_SQ` | `0.001` | `UnfoldTeleportLogic` | 视为“没有水平移动意图”的速度阈值 | 值已证实；取值推断 |
| `VANILLA_DELTA_EPSILON` | `1.0e-6` | `UnfoldTeleportLogic` | 无输入时判定原版投影位移为零 | 值已证实；幅度推断 |
| `VANILLA_PROGRESS_EPSILON` | `1.0e-4` | `UnfoldTeleportLogic` | 有输入时原版投影位移的最小进度 | 推断（规格未给幅度，注释自标） |
| `ISO_PITCH_DEGREES` | `35.26438968` | `ProjectionBasis` | arctan(1/√2) 的精确值 | 来源已证实；与相机一致未验证，推断 |

**`MAX_REQUEST_DISTANCE` 的复核结论**（已证实：由代码与数值计算推出）：

- 水平模式：`λ = tExit + clearance ≤ 64 + 0.5·width·(|d.x|+|d.z|) + 0.01`，玩家宽度 0.6 时约 `64.31`。
- 垂直模式：`d.y ≈ ±1`，`λ = Δy / |d.y| ≤ 64`。
- 斜向模式的主路径沿 ±d 取证据，`λ ≤ 64` 上下；但从竖直射线取层证据的两条路径（潜行下落、空中侧向跨越）用 `λ = Δy / d.y`，`Δy ≤ 64`、`|d.y| = 1/√3 ≈ 0.577`，因此 `λ ≤ 64 / 0.577 ≈ 110.9`，再加探针偏移约 1 格，总位移不超过约 112 格。
- 因此全部合法候选的位移都小于 256，256 对正常切层不生效，只拦明显伪造的请求。若要让上限真正贴合玩法，可下调到约 128；本次不改代码（推断：下调建议未实施）。

## 6. 已知未验证项

以下条目不影响“代码当前如何工作”的描述，但影响“这套算法是否真的成立”的判断，评审时应单独看待。

1. **相机一致性未实机验证**：`ProjectionBasis` 类注释自述“尚未在运行时验证其与相机实际成像完全一致”。e_r 的符号有字节码静态证明，但整条投影基与画面是否逐帧吻合没有实测。（推断基准）
2. **玩家 yaw 未锁定**：鼠标移动会改变原版输入的世界方向，使其偏离当前基；这只影响输入映射，不改变基的符号，但会让“屏幕输入”与“实际移动”不一致。未量化影响。（推断）
3. **A5 只验终点，不验越层路径**：水平候选允许穿过中间层，理由是 d 方向正是 Π 压缩掉的深度、中间层在画面上不可分辨。该玩法判断没有运行时验证；代价是起点与终点之间的其它层不逐层检查。（推断）
4. **两端输入不同步**：共享第 1–7 步消除了算法分叉，但没有消除“客户端世界与服务端世界、客户端 tick 与服务端 tick”的差异。是否会出现高频的“客户端通过、服务端拒绝”未实测。（推断）
5. **服务端拉回阈值未联机实测**：`moved too quickly` / `moved wrongly` 的判定与 `isHost` 跳过行为来自 1.21.5 字节码静态分析；切层请求在专用服与局域网下是否真的不被拉回，没有运行日志。（推断）
6. **`NO_SUPPORT` 保持预测位置的实际表现**：依赖重力与权威位置包纠正；是否产生连续拒绝、下落穿透或抖动未实测。（推断）
7. **拒绝回退只改位置**：`setPos(origin)` 不重置速度与碰撞状态；回退后下一 tick 的原版移动如何重算未验证。（推断）
8. **抑制与前瞻参数未评估**：`REVERSE_CUT_RADIUS = 2.5`、`LATERAL_LOOKAHEAD = 0.2`、层滞回“同一次跳跃不允许反向换层”的玩法代价，都没有实测数据。（推断）
9. **超时窗口对齐**：客户端 `20 tick` 超时与服务端 `handlePendingTeleport` 窗口一致，但两端时钟偏差与网络延迟未测。（推断）
10. **`ISO_PITCH_DEGREES` 来源**：精确值由 arctan(1/√2) 得出，初始提交为手写 35.26；与相机实际俯仰的偏差未实测。（推断）
11. **`MAX_REQUEST_DISTANCE` 偏宽**：按第 5 节推导，合法位移上界约 112 格，256 有约 2.3 倍余量；是否下调未定。（推断）
12. **层证据与实体碰撞的时序**：服务端在包处理时读取世界状态，客户端在 tick 结束时读取；同一 tick 内实体位置可能不同，`getEntityCollisions` 的结果可能因此变化。未实测。（推断）
