package pama1234.unfold.logic;

import net.minecraft.block.ShapeContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import pama1234.unfold.UnfoldMod;
import pama1234.unfold.event.PlayerTeleportCallback;
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
    // 规格 A3：展开轴与世界 Y 轴接近垂直的数值判定；此时沿 d 无法改变高度。
    private static final double LAMBDA_MIN_AXIS_Y = 1.0e-6;

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

        // 3. 左右移动穿越 (地面)：此方法当前实现为空，不执行任何瞬移逻辑。
        if (player.isOnGround() && isAboutToCollide(player, world)) {
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
     * @return 满足投影不变量的候选；d⊥Y（无解）或数值超差时返回 {@code null}。
     */
    private static Vec3d candidateOnLayer(ClientPlayerEntity player, ProjectionBasis basis, double feetY) {
        Vec3d desired = player.getPos();
        double axisY = basis.axis().y;
        if (Math.abs(axisY) < LAMBDA_MIN_AXIS_Y) {
            // 规格 A2：水平模式下 d⊥Y，沿展开轴无法改变高度，层内不存在候选。
            return null;
        }
        double lambda = (feetY - desired.y) / axisY;
        Vec3d candidate = basis.reconstruct(desired, lambda);

        // 规格 A3：投影不变量是硬约束；这里的容差只用于数值比较，不作为允许的位移量。
        ProjectionBasis.ScreenPos anchor = basis.project(desired);
        ProjectionBasis.ScreenPos moved = basis.project(candidate);
        double dr = moved.right() - anchor.right();
        double du = moved.up() - anchor.up();
        if (dr * dr + du * du > PROJECTION_TOLERANCE * PROJECTION_TOLERANCE) {
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
     * 同时会清除玩家的垂直速度，并触发瞬移事件回调。
     *
     * @param player    要瞬移的玩家实体。
     * @param targetPos 瞬移的目标位置。
     */
    // --- 辅助方法和几何定义 ---
    private static void teleportPlayer(ClientPlayerEntity player, Vec3d targetPos) {
        Vec3d fromPos = player.getPos(); // 获取瞬移前的位置，用于事件回调。

        // 设置玩家的新位置。
        player.setPos(targetPos.x, targetPos.y, targetPos.z);
        // 清除玩家的垂直速度，防止瞬移后继续下落或上升。
        player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);
        UnfoldMod.LOGGER.info("Teleported to {}", targetPos);

        // 【新增】触发瞬移事件，通知监听器玩家已瞬移。
        PlayerTeleportCallback.EVENT.invoker().onTeleport(UnfoldManager.currentMode, fromPos, targetPos);

        // 这里可以添加一个短暂的冷却时间，防止瞬移在短时间内连续多次触发，造成体验问题。
    }

    /**
     * 判断一个方块上方是否是安全可站立的平台。
     * 安全平台要求方块本身存在，且其上方两格都是空气（玩家可以站立和拥有头部空间）。
     *
     * @param world   当前世界。
     * @param pos     要检查的方块位置。
     * @param surface 射线命中的方块表面方向（通常是 {@link Direction#UP}）。
     * @return 如果是安全平台则返回 {@code true}，否则返回 {@code false}。
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
        return candidate;
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
     * 检查水平模式下玩家在地面行走时是否可以瞬移穿越障碍。
     * 当前此方法未实现任何瞬移逻辑，始终返回 {@code null}。
     *
     * @param player 客户端玩家实体。
     * @param world  客户端世界。
     * @return 始终返回 {@code null}。
     */
    private static Vec3d checkHorizontalWalk(ClientPlayerEntity player, ClientWorld world) {
        return null; // 功能待实现
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
     * 【已修复】传入 world 参数
     * 判断玩家是否即将与前方的方块发生碰撞。
     * 通过预测玩家移动一小段距离后的碰撞箱来检查。
     *
     * @param player 客户端玩家实体。
     * @param world  客户端世界。
     * @return 如果玩家即将与方块碰撞，则返回 {@code true}。
     */
    private static boolean isAboutToCollide(ClientPlayerEntity player, ClientWorld world) {
        Vec3d moveVec = player.getVelocity(); // 玩家当前的移动速度向量。
        // 如果速度非常小，认为玩家没有移动意图，不进行碰撞预测。
        if (moveVec.lengthSquared() < 0.001) return false;
        // 计算玩家的预测碰撞箱：当前碰撞箱沿着移动方向稍微偏移0.2单位。
        Box futureBox = player.getBoundingBox().offset(moveVec.normalize().multiply(0.2));
        // 检查预测碰撞箱内是否有实体方块（即是否会发生碰撞）。
        return !world.isSpaceEmpty(futureBox);
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