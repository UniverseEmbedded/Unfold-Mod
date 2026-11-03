package pama1234.unfold.logic;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import pama1234.unfold.UnfoldMod;
import pama1234.unfold.event.PlayerTeleportCallback;
import pama1234.unfold.network.TeleportRequestPayload;
import pama1234.unfold.util.ProjectionBasis;
import pama1234.unfold.util.ViewDirection;

import java.util.ArrayList;
import java.util.List;

/**
 * 此工具类负责处理《Unfold Mod》中的所有玩家瞬移逻辑。
 * 它根据当前激活的展开模式（垂直、水平、斜向）和玩家的运动状态，
 * 通过射线检测来识别视觉上可达的目标平台，并执行自动瞬移，以实现视错觉玩法。
 */
public class UnfoldTeleportLogic {

    // 射线检测的最大距离，用于限定瞬移的范围。
    private static final double MAX_DISTANCE = 64.0;
    // 一个小的浮点数，用于避免浮点精度问题，例如在计算边缘位置时。
    private static final double EPSILON = 0.01;
    // 规格 A3：Π 不变量的数值比较容差，只用于比较，不作为允许的位移量。
    private static final double PROJECTION_TOLERANCE = 1.0e-6;
    // 规格 A5：脚底与支撑面高度的数值比较容差，只用于比较。
    // 与服务端共用的同一份常量，见 TeleportTargetValidator.SURFACE_TOLERANCE。
    private static final double SURFACE_TOLERANCE = TeleportTargetValidator.SURFACE_TOLERANCE;
    // 规格 A4：无屏幕方向输入时，判定“原版没有投影位移”的数值阈值。
    private static final double VANILLA_DELTA_EPSILON = 1.0e-6;
    // 规格 A4：有输入时，原版投影位移在期望方向上的最小进度（[推断]，规格未给幅度）。
    private static final double VANILLA_PROGRESS_EPSILON = 1.0e-4;
    // 卡点 1：候选横向行进前瞻的距离，与 isAboutToCollideHorizontally 的触发前瞻一致。
    private static final double LATERAL_LOOKAHEAD = 0.2;
    // 卡点 4：同层反向重穿的抑制半径（[推断]，见 isReversedHorizontalCut）。
    private static final double REVERSE_CUT_RADIUS = 2.5;
    // 速度或水平分量小于该值时视为没有水平移动意图（与既有触发判定一致）。
    private static final double MIN_HORIZONTAL_SPEED_SQ = 0.001;

    /**
     * 主入口点，在每个客户端Tick（游戏帧）中调用，用于检查并执行可能的瞬移操作。
     * 根据当前激活的展开模式，委托给相应的处理方法。
     *
     * @param player 当前客户端玩家实体。
     * @return 如果成功执行了瞬移，则返回 {@code true}；否则返回 {@code false}。
     */
    public static boolean checkAndPerformTeleport(ClientPlayerEntity player) {
        // 如果未处于展开模式，或者玩家正在飞行（例如创造模式飞行），则不执行瞬移。
        if (!UnfoldManager.unfolding || player.getAbilities().flying) {
            return false;
        }

        // 层滞回（层序号单调性）：落地即结束本次跳跃，清空换层方向。
        // 因此下面的单调约束只作用在同一次跳跃（离地到落地）之内。
        if (player.isOnGround()) {
            UnfoldManager.jumpLayerDirection = 0;
        }

        // 规格 A4：Vanilla 优先。本方法在 END_CLIENT_TICK 调用，
        // 玩家位置已包含本 tick 的 Vanilla 移动与碰撞结果。
        ProjectionBasis basis = ProjectionBasis.of(UnfoldManager.currentDirection);
        if (vanillaMeetsDesiredMotion(player, basis)) {
            return false;
        }

        ClientWorld world = player.clientWorld;
        UnfoldMode currentMode = UnfoldManager.currentMode;

        Vec3d targetPos = null; // 潜在的瞬移目标位置

        // 根据当前展开模式，调用不同的瞬移处理逻辑
        switch (currentMode) {
            case VERTICAL:
                targetPos = handleVerticalTeleport(player, world);
                break;
            case HORIZONTAL:
                targetPos = handleHorizontalTeleport(player, world);
                break;
            case ISOMETRIC:
                targetPos = handleIsometricTeleport(player, world);
                break;
        }

        // 如果找到了有效的瞬移目标位置，则执行瞬移。
        if (targetPos != null) {
            teleportPlayer(player, targetPos);
            return true;
        }
        return false;
    }

    /**
     * 规格 A4：判断本 tick 的 Vanilla 移动是否已经完成期望的屏幕位移。
     *
     * 依据是实际碰撞处理后的位置变化（上一 tick 结束位置到当前位置），而不是按键是否按下。
     * 返回 {@code true} 时完全不启动展开逻辑，保留 Vanilla 结果。
     *
     * [推断] 规格未给出期望投影位移的幅度，这里只比较屏幕位移的方向与进度：
     * 有屏幕方向输入时，原版投影位移在期望方向上的分量达到阈值即视为完成；
     * 没有屏幕方向输入时，只有原版投影位移本身为零才视为完成，
     * 因此重力、击退等非输入位移仍会进入候选搜索（不会因此把玩家留在原地）。
     */
    private static boolean vanillaMeetsDesiredMotion(ClientPlayerEntity player, ProjectionBasis basis) {
        Vec3d previous = UnfoldManager.lastTickPosition;
        if (previous == null) {
            return false;
        }
        ProjectionBasis.ScreenPos vanilla = basis.project(player.getPos().subtract(previous));

        PlayerInput input = player.input.playerInput;
        double right = 0.0;
        double up = 0.0;
        if (input.forward()) up += 1.0;
        if (input.backward()) up -= 1.0;
        if (input.right()) right += 1.0; // e_r 即相机屏幕右向，静态已证实，见 ProjectionBasis 构造
        if (input.left()) right -= 1.0;

        double desiredLength = Math.sqrt(right * right + up * up);
        if (desiredLength < 1.0e-9) {
            double projectedSq = vanilla.right() * vanilla.right() + vanilla.up() * vanilla.up();
            return projectedSq <= VANILLA_DELTA_EPSILON * VANILLA_DELTA_EPSILON;
        }
        double progress = (vanilla.right() * right + vanilla.up() * up) / desiredLength;
        return progress >= VANILLA_PROGRESS_EPSILON;
    }

    /**
     * 处理垂直展开模式（俯视/仰视）下的瞬移逻辑。
     * 主要用于玩家在垂直方向上（上下）穿越视觉重叠的平台。
     *
     * @param player 客户端玩家实体。
     * @param world  客户端世界。
     * @return 瞬移的目标位置 {@code Vec3d}，如果没有合适的瞬移则返回 {@code null}。
     */
    // --- 垂直模式逻辑 ---
    private static Vec3d handleVerticalTeleport(ClientPlayerEntity player, ClientWorld world) {
        // 在垂直模式下，只有玩家不在地面上时才可能触发瞬移（即在空中）。
        if (player.isOnGround()) {
            return null;
        }

        ProjectionBasis basis = ProjectionBasis.of(UnfoldManager.currentDirection);
        // 获取用于垂直方向射线探测的起始点集合。
        // 这些点分布在玩家当前位置的水平面上，略微超出玩家碰撞箱边缘。
        List<Vec3d> origins = getVerticalProbeOrigins(player);
        // 根据玩家的垂直速度方向确定探测方向：向上跳则向上探测，下落则向下探测。
        Direction direction = player.getVelocity().y > 0 ? Direction.UP : Direction.DOWN;

        // 对每个起始点发射射线，逐条使用 LayerRaycaster 的区间证据。
        for (Vec3d origin : origins) {
            Vec3d end = origin.add(Vec3d.of(direction.getVector()).multiply(MAX_DISTANCE));
            for (LayerRaycaster.LayerInterval interval : castLayers(world, player, origin, end)) {
                Vec3d candidate = tryCandidate(player, world, basis, interval);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null; // 没有找到合适的瞬移目标。
    }

    /**
     * 处理水平展开模式（FEZ风格）下的瞬移逻辑。
     * 包含空中跨越、边缘下落、地面穿越等多种水平方向上的瞬移。
     *
     * @param player 客户端玩家实体。
     * @param world  客户端世界。
     * @return 瞬移的目标位置 {@code Vec3d}，如果没有合适的瞬移则返回 {@code null}。
     */
    // --- 水平模式逻辑 ---
    private static Vec3d handleHorizontalTeleport(ClientPlayerEntity player, ClientWorld world) {
        // 1. 跳跃到上方：当玩家意图向上跳跃时（player.jumping为true且Y速度为正）。
        if (player.jumping && player.getVelocity().y > 0) {
            Vec3d target = checkHorizontalJump(player, world);
            if (target != null) return target;
        }

        // 2. 边缘下落：当玩家潜行且位于方块边缘时，尝试向下瞬移。
        if (player.isSneaking() && isAtEdge(player, world)) {
            Vec3d target = checkHorizontalSneak(player, world);
            if (target != null) return target;
        }

        // 3. 地面穿越：水平模式下 d⊥Y，候选只能在 p_desired + λd 上保持同一世界 Y；
        //    触发按任意水平行进方向判定（不要求等于 d），候选仍只沿 d 搜索。
        if (player.isOnGround() && isAboutToCollideHorizontally(player, world)) {
            Vec3d target = checkHorizontalWalk(player, world);
            if (target != null) return target;
        }

        // 4. 【新增】悬空侧向跨越 (核心FEZ玩法)：当玩家不在地面上时，尝试进行水平方向的视错觉瞬移。
        if (!player.isOnGround()) {
            Vec3d target = checkHorizontalAirStrafe(player, world);
            if (target != null) return target;
        }

        return null; // 没有找到合适的瞬移目标。
    }

    /**
     * 规格 A3：候选位置只能取 {@code p_desired + λd}，且 Π(p_c) = Π(p_desired) 是硬约束。
     *
     * @param player 客户端玩家实体；本 tick 原版移动后的位置即 p_desired。
     * @param basis  当前视角的唯一投影基。
     * @param feetY  候选的脚底高度，由层证据形状的顶面给出。
     * @return 满足投影不变量的候选；d⊥Y 时层顶面无法反解 λ，返回 {@code null}
     *         （水平候选改由 {@link #candidateAtLambda} 用层区间给出的 λ 构造）。
     */
    private static Vec3d candidateOnLayer(ClientPlayerEntity player, ProjectionBasis basis, double feetY) {
        Vec3d desired = player.getPos();
        if (!basis.canChangeWorldY()) {
            // 水平模式：沿 d 的 λ 不改变世界 Y，层顶面不能用来反解 λ；
            // 水平候选由 candidateAtLambda 用层区间给出的 λ 构造。
            return null;
        }
        double lambda = (feetY - desired.y) / basis.axis().y;
        Vec3d candidate = basis.reconstruct(desired, lambda);

        // 规格 A3：投影不变量是硬约束；这里的容差只用于数值比较，不作为允许的位移量。
        if (!basis.preservesProjection(desired, candidate, PROJECTION_TOLERANCE)) {
            return null;
        }
        return candidate;
    }

    /**
     * 规格 A3 与《Unfold-水平模式秩一答复》§3：λ 完全由层证据给出，
     * 候选只在 {@code p_desired + λd} 上。射线只确定切层参数，不参与屏幕位移。
     */
    private static Vec3d candidateAtLambda(ClientPlayerEntity player, ProjectionBasis basis, double lambda) {
        Vec3d desired = player.getPos();
        Vec3d candidate = basis.reconstruct(desired, lambda);
        if (!basis.preservesProjection(desired, candidate, PROJECTION_TOLERANCE)) {
            return null;
        }
        return candidate;
    }

    /**
     * 处理斜向展开模式（纪念碑谷风格）下的瞬移逻辑。
     * 采用了统一的单辅助线设计，来同时处理从高到低和从低到高的瞬移。
     *
     * @param player 客户端玩家实体。
     * @param world  客户端世界。
     * @return 瞬移的目标位置 {@code Vec3d}，如果没有合适的瞬移则返回 {@code null}。
     */
    private static Vec3d handleIsometricTeleport(ClientPlayerEntity player, ClientWorld world) {


        // 2. 实现双辅助线逻辑
        // 规格 A1：展开轴、屏幕基与 λ 都从同一个 ProjectionBasis 取得，不再单独推导。
        ProjectionBasis basis = ProjectionBasis.of(UnfoldManager.currentDirection);
        Vec3d unfoldAxis = basis.axis();
        BlockPos currentStandingBlockPos = player.getBlockPos().down();
        int currentStandingY = world.isAir(currentStandingBlockPos) ? -1 : currentStandingBlockPos.getY();
        float hw = player.getWidth() / 2f;

        float inward_offset_1 = 0.01f; // 辅助线1（瞬移探测）的偏移
        float inward_offset_2 = 0.5f; // 辅助线2（路径/阴影探测）的偏移

        // 动态计算基准角落和向内方向
        float pivotXOffset, pivotZOffset;
        float inwardXSign, inwardZSign;

        // ... (switch 语句保持不变) ...
        ViewDirection direction = UnfoldManager.currentDirection;
        switch (direction) {
            case ISO_POS_X_POS_Y: case ISO_POS_X_POS_Y_2:       // 东北
                pivotXOffset = hw; pivotZOffset = -hw;
                inwardXSign = -1.0f; inwardZSign = 1.0f;
                break;
            case ISO_POS_X_NEG_Y: case ISO_POS_X_NEG_Y_2:       // 东南
                pivotXOffset = hw; pivotZOffset = hw;
                inwardXSign = -1.0f; inwardZSign = -1.0f;
                break;
            case ISO_NEG_X_POS_Y: case ISO_NEG_X_POS_Y_2:       // 西北
                pivotXOffset = -hw; pivotZOffset = -hw;
                inwardXSign = 1.0f; inwardZSign = 1.0f;
                break;
            case ISO_NEG_X_NEG_Y: case ISO_NEG_X_NEG_Y_2:       // 西南
                pivotXOffset = -hw; pivotZOffset = hw;
                inwardXSign = 1.0f; inwardZSign = -1.0f;
                break;
            default:
                return null;
        }

        // 计算辅助线1（瞬移探测）的作用点
        Vec3d pivotPoint = player.getPos().add(
                pivotXOffset + (inwardXSign * inward_offset_1),
                0,
                pivotZOffset + (inwardZSign * inward_offset_1)
        );

        // 计算辅助线2（路径/阴影探测）的作用点
        Vec3d shadowProbePoint = player.getPos().add(
                pivotXOffset + (inwardXSign * inward_offset_2),
                0,
                pivotZOffset + (inwardZSign * inward_offset_2)
        );

        // 3. 执行辅助线2（阴影探测），判断是否可以向上瞬移
        // 规格 A3：这里同样使用 LayerRaycaster 的区间列表，不再取 first-hit。
        Vec3d shadowEnd = shadowProbePoint.subtract(unfoldAxis.multiply(MAX_DISTANCE));
        boolean canTeleportUp = true;
        for (LayerRaycaster.LayerInterval interval : castLayers(world, player, shadowProbePoint, shadowEnd)) {
            if (interval.blockPos().getY() != currentStandingY) {
                canTeleportUp = false;
                break;
            }
        }

        // 4. 执行辅助线1（瞬移探测）

        // 探测高处
        if (canTeleportUp) {
            Vec3d highProbeStart = pivotPoint.subtract(unfoldAxis.multiply(MAX_DISTANCE));
            for (LayerRaycaster.LayerInterval interval : castLayers(world, player, highProbeStart, pivotPoint)) {
                if (interval.side() != Direction.UP) {
                    continue;
                }
                if (interval.blockPos().getY() <= currentStandingY) {
                    continue;
                }
                Vec3d candidate = tryCandidate(player, world, basis, interval);
                if (candidate != null) {
                    return candidate;
                }
            }
        }

        // 探测低处（不受辅助线2的影响）
        Vec3d lowProbeEnd = pivotPoint.add(unfoldAxis.multiply(MAX_DISTANCE));
        for (LayerRaycaster.LayerInterval interval : castLayers(world, player, pivotPoint, lowProbeEnd)) {
            if (interval.side() != Direction.UP) {
                continue;
            }
            if (interval.blockPos().getY() >= currentStandingY) {
                continue;
            }
            Vec3d candidate = tryCandidate(player, world, basis, interval);
            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * 将玩家瞬移到指定的目标位置。
     * 本地位置只是预测：同时向服务端发送 TeleportRequestPayload，由服务端 player.teleport 落地权威位置。
     * 同时会清除玩家的垂直速度，并触发瞬移事件回调。
     *
     * @param player    要瞬移的玩家实体。
     * @param targetPos 瞬移的目标位置。
     */
    // --- 辅助方法和几何定义 ---
    private static void teleportPlayer(ClientPlayerEntity player, Vec3d targetPos) {
        Vec3d fromPos = player.getPos(); // 获取瞬移前的位置，用于事件回调。

        // 客户端本地预测（非权威）：先按目标位置摆放，等服务器回执或权威位置包确认。
        // 权威结果由服务端 player.teleport 下发，本地位置不作为最终依据。
        player.setPos(targetPos.x, targetPos.y, targetPos.z);
        // 清除玩家的垂直速度，防止瞬移后继续下落或上升。
        player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);

        // 记录在途预测：限流、拒绝回退、拉回丢弃都依赖这三个字段。
        UnfoldManager.pendingTeleportTarget = targetPos;
        UnfoldManager.pendingTeleportOrigin = fromPos;
        UnfoldManager.pendingTeleportTicks = 0;
        UnfoldManager.teleportRequestCooldownTicks = UnfoldManager.TELEPORT_REQUEST_INTERVAL_TICKS;
        // 真正把切层位置提交给服务端；服务端做距离粗界与 A5 终点校验后执行 player.teleport，
        // 拒绝时回执带原因（TeleportResponsePayload），由 UnfoldModClient 决定回退或保持。
        ClientPlayNetworking.send(new TeleportRequestPayload(targetPos));

        UnfoldMod.LOGGER.info("Teleported to {} (client prediction, awaiting server authority)", targetPos);

        // 【新增】触发瞬移事件，通知监听器玩家已瞬移。
        PlayerTeleportCallback.EVENT.invoker().onTeleport(UnfoldManager.currentMode, fromPos, targetPos);
    }

    /**
     * 规格 A5：前置粗筛，不是最终安全判定。
     *
     * 旧实现只会检查两格空气，无法表达玩家完整碰撞箱、实体碰撞、世界边界与支撑关系。
     * 现在它只用于在生成候选前快速排除明显不可站立的层；最终判定由
     * {@link #isCandidateClear} 承担，不能再用本方法的返回值作为安全证明。
     *
     * @param world   当前世界。
     * @param pos     作为支撑面的方块位置。
     * @param surface 站立方向（本仓库只允许 {@link Direction#UP}）。
     * @return 粗筛通过则返回 {@code true}，否则返回 {@code false}。
     */
    private static boolean isSafePlatform(ClientWorld world, BlockPos pos, Direction surface) {
        BlockPos standPos = pos.offset(surface); // 玩家将要站立的位置（方块上方一格）
        // 检查站立位置和其上方一格是否都是空气方块。
        return world.isAir(standPos) && world.isAir(standPos.up());
    }

    /**
     * 规格 A3：由 {@link LayerRaycaster} 的区间证据构造候选。
     *
     * 候选不是射线命中点，也不是方块中心：先取该层形状的顶面高度，
     * 再沿展开轴从 p_desired 重建 p_desired + λd，并检查投影不变量。
     */
    private static Vec3d tryCandidate(ClientPlayerEntity player, ClientWorld world, ProjectionBasis basis,
                                      LayerRaycaster.LayerInterval interval) {
        BlockPos supportBlock = interval.blockPos();
        // 规格 A5：isSafePlatform 只作为前置粗筛，最终判定由 isCandidateClear 承担。
        if (!isSafePlatform(world, supportBlock, Direction.UP)) {
            return null;
        }
        double feetY = layerTopY(interval);
        Vec3d candidate = candidateOnLayer(player, basis, feetY);
        if (candidate == null) {
            return null;
        }
        // 候选与原位重合时不构成切层。
        if (candidate.squaredDistanceTo(player.getPos()) < 1.0e-12) {
            return null;
        }
        // 规格 A5：最终 clearance 必须使用完整碰撞箱；粗筛通过不代表安全。
        if (!isCandidateClear(player, world, candidate, feetY)) {
            return null;
        }
        // 层滞回（层序号单调性）：Π 相同、世界 Y 不同时，同一次跳跃内只允许沿同一方向换层。
        // 方向以候选脚底相对玩家脚底的世界 Y 符号为准；同层（含浮点容差内）不算换层。
        // 约束只在空中生效，落地时已在 checkAndPerformTeleport 清零，不对地面行走追加限制。
        double deltaY = candidate.y - player.getPos().y;
        if (!player.isOnGround() && Math.abs(deltaY) > SURFACE_TOLERANCE) {
            int direction = deltaY > 0 ? 1 : -1;
            if (UnfoldManager.jumpLayerDirection != 0 && direction != UnfoldManager.jumpLayerDirection) {
                return null;
            }
            UnfoldManager.jumpLayerDirection = direction;
        }
        return candidate;
    }

    /**
     * 水平模式（d⊥Y）：λ 由沿展开轴的层区间给出，候选保持同一世界 Y。
     *
     * directionSign = +1 表示探测沿 +d，-1 表示沿 -d。
     * 区间参数 tEntry/tExit 始终是沿本次射线方向测得的距离，因此：
     * 正向候选取 λ = +(tExit + clearance)，反向候选取 λ = -(tExit + clearance)。
     * 无论符号如何，候选都只由 {@code p_desired + λd} 重建，并经过 preservesProjection 的 1e-6 硬检查。
     *
     * 与 {@link #tryCandidate} 的区别：不把层顶面当作站立面，
     * 因此不经过 isSafePlatform 粗筛；支撑关系完全由 {@link #isCandidateClear} 判定。
     * [推断] 越过该层的距离取碰撞箱半宽沿 d 的水平投影，保证完整碰撞箱退出层表面。
     *
     * 卡点 1：候选必须在触发方向（水平实际移动方向）上留有行进余量。
     * 触发由 {@link #isAboutToCollideHorizontally} 按速度方向给出，而候选只在 p+λd 上，
     * 两者方向不同；若不在这里校验横向余量，切层只换了不可见深度，
     * 玩家仍会被触发时的那面墙挡住，触发与结果不一致。
     *
     * 卡点 3（A5 语义）：A5 只验证终点，不验证从 p_desired 到候选的越层路径。
     * 本仓库判定“允许穿层”：候选 λ 由 tExit + clearance 给出，必然越过证据层；
     * 若不允许，则水平候选恒空，FEZ 深度切层玩法不成立。允许的依据是越过方向就是相机深度轴 d，
     * 该方向正是投影 Π 压缩掉的方向，中间层在画面上不可见也不可分辨。
     * 终点仍由 {@link #isCandidateClear} 按完整碰撞箱校验，穿层后不会把玩家留在几何体内；
     * 代价是起点与终点之间的其它层不会逐层检查。
     * 若将来改判“不允许穿层”，可在不引入新依赖的前提下用 {@code checkHorizontalWalk} 的区间列表做
     * 路径连续性检查：该列表按 tEntry 升序，合法越层要求所有 tEntry <= chosen.tExit() 的区间
     * 都属于 chosen.blockPos()（同一方块的多个包围盒视为同一层）；出现其它方块即拒绝。本实现未加。
     *
     * 卡点 4：同一层在相邻两 tick 内的反向重穿由 {@link #isReversedHorizontalCut} 抑制。
     */
    private static Vec3d tryHorizontalCandidate(ClientPlayerEntity player, ClientWorld world, ProjectionBasis basis,
                                                LayerRaycaster.LayerInterval interval, double directionSign) {
        Vec3d axis = basis.axis();
        double clearance = 0.5 * player.getWidth() * (Math.abs(axis.x) + Math.abs(axis.z)) + EPSILON;
        Vec3d candidate = candidateAtLambda(player, basis, directionSign * (interval.tExit() + clearance));
        if (candidate == null) {
            return null;
        }
        if (candidate.squaredDistanceTo(player.getPos()) < 1.0e-12) {
            return null;
        }
        // 候选脚底高度就是其世界 Y；支撑面必须与它一致（A5 第 6、7 步）。
        if (!isCandidateClear(player, world, candidate, candidate.y)) {
            return null;
        }
        // 卡点 1：触发方向与候选方向不同，候选必须在该方向上留出与触发前瞻相同的余量。
        Vec3d horizontal = new Vec3d(player.getVelocity().x, 0.0, player.getVelocity().z);
        if (horizontal.lengthSquared() < MIN_HORIZONTAL_SPEED_SQ) {
            return null;
        }
        Box lateralBox = player.getBoundingBox()
                .offset(candidate.subtract(player.getPos()))
                .offset(horizontal.normalize().multiply(LATERAL_LOOKAHEAD));
        if (!world.isSpaceEmpty(player, lateralBox)) {
            return null;
        }
        // 卡点 4：抑制同一层在相邻两 tick 内的正、反向往返。
        if (isReversedHorizontalCut(player, interval.blockPos(), directionSign)) {
            return null;
        }
        UnfoldManager.lastHorizontalCutBlock = interval.blockPos();
        UnfoldManager.lastHorizontalCutSign = directionSign;
        return candidate;
    }

    /**
     * 卡点 4：判断这次水平切层是否是“刚切过的同一层、相反方向、且玩家仍在附近”的反向重穿。
     *
     * 既有冷却语义是“传送后置位，玩家落地即解除”（UnfoldModClient 的 teleportCooldown，2025-07-09
     * 提交 15e2d8f 即有），水平切层后玩家仍在地面，冷却下一 tick 就解除，因此正、反向探测
     * 会在相邻两 tick 对同一层来回切。这里不改冷却语义，只记录上一次水平切层的方块与方向。
     *
     * [推断] “附近”取 2.5 格：一次切层后玩家与层中心距离约 1 格上下，2.5 格足以覆盖
     * 相邻两 tick 的往返，又不会长期压制玩家走远之后回到该处时的正常切层。
     */
    private static boolean isReversedHorizontalCut(ClientPlayerEntity player, BlockPos blockPos, double directionSign) {
        BlockPos lastBlock = UnfoldManager.lastHorizontalCutBlock;
        if (lastBlock == null || !lastBlock.equals(blockPos)) {
            return false;
        }
        if (UnfoldManager.lastHorizontalCutSign == directionSign) {
            return false;
        }
        Vec3d center = new Vec3d(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
        return player.getPos().squaredDistanceTo(center) < REVERSE_CUT_RADIUS * REVERSE_CUT_RADIUS;
    }

    /** 层证据形状在方块顶面的世界高度；非满方块按实际形状取顶。 */
    private static double layerTopY(LayerRaycaster.LayerInterval interval) {
        double blockY = interval.blockPos().getY();
        double top = blockY;
        for (Box box : interval.shape().getBoundingBoxes()) {
            top = Math.max(top, blockY + box.maxY);
        }
        return top;
    }

    /** 用玩家实体对应的 ShapeContext 取得层区间列表（规格 A3：候选证据不用 first-hit）。 */
    private static List<LayerRaycaster.LayerInterval> castLayers(ClientWorld world, ClientPlayerEntity player,
                                                                 Vec3d start, Vec3d end) {
        return LayerRaycaster.cast(world, ShapeContext.of(player), start, end);
    }

    /**
     * 规格 A5：用玩家当前 pose 的完整碰撞箱做最终 clearance。
     *
     * <p>第 1–7 步（世界高度范围、世界边界、isSpaceEmpty、方块碰撞、实体碰撞、
     * 支撑存在、支撑顶面 == 脚底 Y）委托给 common 侧唯一实现
     * {@link TeleportTargetValidator#validateEndpoint}，服务端授权用的是同一份代码，
     * 两端判定不会分叉。常量 {@link #SURFACE_TOLERANCE} 与检查顺序都来自那里；
     * 修改本方法或那里时必须同步改另一边。
     *
     * <p>第 8 步（投影不变量 Π 复核）只能留在客户端：它依赖 {@link ProjectionBasis}
     * 与当前展开方向，而服务端没有展开方向，不应据此否决一个位置本身合法的落点。
     *
     * 卡点 3：本方法只证明终点安全，不证明从玩家原位到终点的越层路径。
     * 越层是否允许是玩法判断，本仓库判定“允许”，理由与不采用时的检查做法见
     * {@link #tryHorizontalCandidate} 的方法注释。
     *
     * @param candidate 候选位置（玩家脚底锚点）。
     * @param feetY     层证据给出的支撑面高度。
     * @return 通过全部检查则返回 {@code true}。
     */
    private static boolean isCandidateClear(ClientPlayerEntity player, ClientWorld world, Vec3d candidate, double feetY) {
        // 第 1–7 步：与服务端共用的同一份实现。
        if (TeleportTargetValidator.validateEndpoint(world, player, candidate, feetY) != TeleportTargetValidator.RejectionReason.NONE) {
            return false;
        }
        // 8. 投影不变量复核（客户端独有）。
        ProjectionBasis basis = ProjectionBasis.of(UnfoldManager.currentDirection);
        ProjectionBasis.ScreenPos anchor = basis.project(player.getPos());
        ProjectionBasis.ScreenPos moved = basis.project(candidate);
        double dr = moved.right() - anchor.right();
        double du = moved.up() - anchor.up();
        return dr * dr + du * du <= PROJECTION_TOLERANCE * PROJECTION_TOLERANCE;
    }

    /**
     * 获取垂直模式下用于射线探测的起始点列表。
     * 这些点围绕玩家水平中心的四个方向，略微向外偏移，以确保能探测到周围的方块。
     *
     * @param player 客户端玩家实体。
     * @return 包含四个探测起始点的 {@code List<Vec3d>}。
     */
    private static List<Vec3d> getVerticalProbeOrigins(ClientPlayerEntity player) {
        List<Vec3d> origins = new ArrayList<>();
        Vec3d center = player.getPos(); // 玩家的中心位置。
        // 玩家宽度的一半，加上一个小的偏移量 EPSILON，以确保从碰撞箱外开始探测。
        float hw = player.getWidth() / 2f + (float) EPSILON;
        // 添加玩家碰撞箱四个角落的X/Z投影点作为探测起点。
        origins.add(center.add(hw, 0, 0));
        origins.add(center.add(-hw, 0, 0));
        origins.add(center.add(0, 0, hw));
        origins.add(center.add(0, 0, -hw));
        return origins;
    }

    /**
     * 检查水平模式下玩家跳跃时是否可以瞬移到上方平台。
     * 向上发射一条射线探测。
     *
     * @param player 客户端玩家实体。
     * @param world  客户端世界。
     * @return 如果找到安全目标则返回目标位置 {@code Vec3d}，否则返回 {@code null}。
     */
    private static Vec3d checkHorizontalJump(ClientPlayerEntity player, ClientWorld world) {
        ProjectionBasis basis = ProjectionBasis.of(UnfoldManager.currentDirection);
        Vec3d start = player.getPos();
        // 从玩家当前位置向上发射 MAX_DISTANCE 长的射线。
        Vec3d end = start.add(0, MAX_DISTANCE, 0);
        for (LayerRaycaster.LayerInterval interval : castLayers(world, player, start, end)) {
            Vec3d candidate = tryCandidate(player, world, basis, interval);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 检查水平模式下玩家潜行时是否可以瞬移到后方下方平台。
     * 主要模拟“信仰之跃”或从平台边缘下落。
     *
     * @param player 客户端玩家实体。
     * @param world  客户端世界。
     * @return 如果找到安全目标则返回目标位置 {@code Vec3d}，否则返回 {@code null}。
     */
    private static Vec3d checkHorizontalSneak(ClientPlayerEntity player, ClientWorld world) {
        ProjectionBasis basis = ProjectionBasis.of(UnfoldManager.currentDirection);
        // 获取当前展开模式的“深度”轴方向（通常是与玩家视角相对的方向）。
        Vec3d unfoldAxis = getUnfoldAxisDirection(UnfoldManager.currentDirection);
        Vec3d origin = player.getPos();
        // 射线的方向是展开轴的反方向，模拟向后下落。
        Vec3d rayDir = unfoldAxis.negate();

        // 1. 先向后方发射短射线，检测玩家后方是否有方块（判断是否站在边缘）。
        Vec3d backEnd = origin.add(rayDir.multiply(player.getWidth() * 2));
        List<LayerRaycaster.LayerInterval> backLayers = castLayers(world, player, origin, backEnd);
        // 如果没有层证据，说明后方是空气，不是边缘下落，不触发瞬移。
        if (backLayers.isEmpty()) {
            return null;
        }

        // 计算一个位于最近层之后，略微进入空气的位置，作为第二条射线的起点。
        // 这模拟了玩家从边缘“跌落”到空气中。
        Vec3d edgeAirPos = origin.add(rayDir.multiply(backLayers.get(0).tEntry())).add(rayDir.multiply(EPSILON));

        // 2. 从这个“边缘空气位置”垂直向下发射长射线，逐层寻找候选。
        for (LayerRaycaster.LayerInterval interval : castLayers(world, player, edgeAirPos, edgeAirPos.add(0, -MAX_DISTANCE, 0))) {
            Vec3d candidate = tryCandidate(player, world, basis, interval);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 水平模式地面穿越：沿展开轴 d 的正、反两向取层区间，λ 由区间给出。
     *
     * 依据《Unfold-水平模式秩一答复》§3：射线沿 R(λ)=p_desired+λd 只确定切层参数，
     * 候选由 p_desired+λd 重建，屏幕投影不变量由 ProjectionBasis 硬检查。
     * 水平模式 d⊥Y，因此候选保持同一世界 Y；是否可站立由 A5 的完整碰撞箱判定。
     *
     * 方向与区间语义：
     * - 正向射线 end = p + MAX_DISTANCE·d，tEntry/tExit 沿 +d 度量，候选 λ = +(tExit+clearance)；
     * - 反向射线 end = p - MAX_DISTANCE·d，tEntry/tExit 沿 -d 度量，候选 λ = -(tExit+clearance)。
     *
     * 卡点 2：负方向在同一实现内覆盖，两个方向都只经 {@link #candidateAtLambda} 重建候选，
     * 并同样经过 1e-6 的 Π 硬检查（没有为负 λ 放宽容差）。
     *
     * @param player 客户端玩家实体。
     * @param world  客户端世界。
     * @return 通过 A5 的候选位置；没有则返回 {@code null}。
     */
    private static Vec3d checkHorizontalWalk(ClientPlayerEntity player, ClientWorld world) {
        ProjectionBasis basis = ProjectionBasis.of(UnfoldManager.currentDirection);
        Vec3d origin = player.getPos();
        Vec3d axis = basis.axis();

        // 正向深度：沿 +d。
        Vec3d forwardEnd = origin.add(axis.multiply(MAX_DISTANCE));
        for (LayerRaycaster.LayerInterval interval : castLayers(world, player, origin, forwardEnd)) {
            Vec3d candidate = tryHorizontalCandidate(player, world, basis, interval, 1.0);
            if (candidate != null) {
                return candidate;
            }
        }

        // 反向深度：沿 -d；区间参数是沿 -d 的距离，故 λ 取负。
        Vec3d backwardEnd = origin.subtract(axis.multiply(MAX_DISTANCE));
        for (LayerRaycaster.LayerInterval interval : castLayers(world, player, origin, backwardEnd)) {
            Vec3d candidate = tryHorizontalCandidate(player, world, basis, interval, -1.0);
            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * 判断玩家是否站在方块的边缘（即玩家正下方的方块不是实心方块）。
     *
     * @param player 客户端玩家实体。
     * @param world  客户端世界。
     * @return 如果玩家正下方的方块不是实心，则返回 {@code true}。
     */
    private static boolean isAtEdge(ClientPlayerEntity player, ClientWorld world) {
        return !world.getBlockState(player.getBlockPos().down()).isSolid();
    }

    /**
     * 判断玩家是否即将在任意水平行进方向上发生碰撞。
     *
     * 依据规格 A3 与《Unfold-水平模式秩一答复》：候选线固定为 {@code p_desired + λd}，
     * 与触发碰撞的方向无关。水平模式下玩家的实际移动由
     * {@code strafeAxis = d×Y} 给出（见 UnfoldModClient.handlePlayerMovement），与 d 垂直；
     * 若把触发条件限定为 d 方向，会漏掉该模式主要的横向受阻情形。
     * 因此这里只要求“水平方向即将碰撞”，不要求碰撞方向等于 d。
     *
     * 卡点 1 的二选一：选“让搜索覆盖 strafe 方向”，而不是“触发改沿 d”。
     * 依据是代码事实：水平模式每 tick 都把速度写成 {@code strafeAxis * 0.2}，
     * 速度在 d 上没有分量；沿 d 做碰撞前瞻将永远不触发，水平地面穿越整条路径失效。
     * 而沿 strafe 直接搜索又违反 A3：strafe = ±e_r，沿 e_r 的任何位移都会改变 Π。
     * 因此合规的“覆盖”是：候选仍只沿 d 换深度，但在候选处按触发方向做同样的
     * {@link #LATERAL_LOOKAHEAD} 前瞻（见 {@link #tryHorizontalCandidate}），
     * 只有切层后的位置在该方向上走得动，才算这次触发被真正解开。
     *
     * 代价：触发面变宽，且横向前瞻是终点处的静态检测，不验证从原位到候选之间的横向路径；
     * 候选数量会减少。
     *
     * @param player 客户端玩家实体。
     * @param world  客户端世界。
     * @return 水平预测碰撞箱被占用则返回 {@code true}。
     */
    private static boolean isAboutToCollideHorizontally(ClientPlayerEntity player, ClientWorld world) {
        Vec3d moveVec = player.getVelocity();
        Vec3d horizontal = new Vec3d(moveVec.x, 0.0, moveVec.z);
        // 速度太小视为没有水平移动意图。
        if (horizontal.lengthSquared() < MIN_HORIZONTAL_SPEED_SQ) return false;
        Box futureBox = player.getBoundingBox().offset(horizontal.normalize().multiply(LATERAL_LOOKAHEAD));
        // 规格 A5：碰撞形状必须使用实体对应的 ShapeContext（这里用实体参数重载）。
        return !world.isSpaceEmpty(player, futureBox);
    }

    /**
     * 根据当前的视角方向（{@link ViewDirection}）取得展开模式下的“深度”轴方向向量。
     * 规格 A1：这里只为兼容旧调用保留，实际的 d 由 {@link ProjectionBasis} 统一推导。
     *
     * @param viewDirection 当前的固定视角方向。
     * @return 标准化后的三维向量，表示展开模式的深度轴。
     */
    private static Vec3d getUnfoldAxisDirection(ViewDirection viewDirection) {
        return ProjectionBasis.of(viewDirection).axis();
    }

    /**
     * 【新增】检测玩家在空中进行侧向移动时，是否可以瞬移到视觉上相邻的平台。
     * 这是实现 FEZ 风格“空中跨越”的核心逻辑。
     *
     * @param player 客户端玩家实体。
     * @param world  客户端世界。
     * @return 如果找到安全目标则返回目标位置 {@code Vec3d}，否则返回 {@code null}。
     */
    private static Vec3d checkHorizontalAirStrafe(ClientPlayerEntity player, ClientWorld world) {
        // 1. 获取玩家的水平移动方向（忽略Y轴）。
        Vec3d horizontalVelocity = new Vec3d(player.getVelocity().x, 0, player.getVelocity().z);
        // 如果玩家没有明显的水平移动意图，则不执行瞬移。
        if (horizontalVelocity.lengthSquared() < 0.001) {
            return null;
        }
        Vec3d moveDir = horizontalVelocity.normalize(); // 标准化移动方向。

        // 2. 准备从玩家身体前方，向下发射探测射线。
        float playerWidth = player.getWidth();
        // 射线起点稍微领先于玩家，以确保能探测到前方的平台。
        Vec3d origin = player.getPos().add(moveDir.multiply(playerWidth / 2 + 0.1));
        BlockPos currentStandingBlock = player.getBlockPos().down();

        // 3. 逐层检查垂直向下的区间证据（规格 A3，不取 first-hit）。
        ProjectionBasis basis = ProjectionBasis.of(UnfoldManager.currentDirection);
        for (LayerRaycaster.LayerInterval interval : castLayers(world, player, origin, origin.add(0, -MAX_DISTANCE, 0))) {
            // 检查找到的平台是否与玩家当前站立的平台不同（避免瞬移到同一个方块上）。
            if (interval.blockPos().equals(currentStandingBlock)) {
                continue;
            }
            Vec3d candidate = tryCandidate(player, world, basis, interval);
            if (candidate != null) {
                UnfoldMod.LOGGER.info("Air strafe opportunity found at {}", candidate);
                return candidate;
            }
        }

        return null; // 没有找到合适的瞬移目标。
    }
}