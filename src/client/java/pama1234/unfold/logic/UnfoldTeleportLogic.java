package pama1234.unfold.logic;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import pama1234.unfold.UnfoldMod;
import pama1234.unfold.event.PlayerTeleportCallback;
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

        // 获取用于垂直方向射线探测的起始点集合。
        // 这些点分布在玩家当前位置的水平面上，略微超出玩家碰撞箱边缘。
        List<Vec3d> origins = getVerticalProbeOrigins(player);
        // 根据玩家的垂直速度方向确定探测方向：向上跳则向上探测，下落则向下探测。
        Direction direction = player.getVelocity().y > 0 ? Direction.UP : Direction.DOWN;

        // 对每个起始点发射射线
        for (Vec3d origin : origins) {
            // 创建射线上下文：从起始点向指定方向发射 MAX_DISTANCE 长的射线。
            // 探测类型为 COLLIDER（实体方块），不处理流体。
            RaycastContext context = new RaycastContext(origin, origin.add(Vec3d.of(direction.getVector()).multiply(MAX_DISTANCE)),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
            // 执行射线检测。
            BlockHitResult hit = world.raycast(context);

            // 如果射线命中了方块，且该方块上方是安全可站立的平台。
            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos targetBlock = hit.getBlockPos();
                if (isSafePlatform(world, targetBlock, direction)) {
                    // 返回目标方块上方中心点的位置作为瞬移目标。
                    // player.getX() 和 player.getZ() 保持不变，只改变Y坐标。
                    return new Vec3d(player.getX(), Vec3d.ofBottomCenter(targetBlock.offset(direction)).y, player.getZ());
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
     * 将玩家的当前位置沿着展开轴投影到目标Y平面上，计算出新的瞬移目标位置。
     * 这确保了玩家在屏幕上的位置保持不变，只改变其深度。
     *
     * @param player       客户端玩家实体。
     * @param targetBlockY 目标方块的Y坐标。
     * @param unfoldAxis   当前的展开轴向量。
     * @return 计算出的、保持屏幕位置不变的瞬移目标点。
     */
    private static Vec3d projectPlayerToTargetY(ClientPlayerEntity player, int targetBlockY, Vec3d unfoldAxis) {
        Vec3d playerPos = player.getPos();
        double currentY = playerPos.y;
        // 目标站立高度 = 目标方块高度 + 1
        double targetY = targetBlockY + 1.0;

        // 展开轴的Y分量
        double unfoldAxisY = unfoldAxis.y;

        // 防止除以零的边缘情况（虽然在斜角模式下几乎不可能发生）
        if (Math.abs(unfoldAxisY) < EPSILON) {
            return new Vec3d(playerPos.x, targetY, playerPos.z);
        }

        // 计算投影所需的距离 't'
        // 公式: targetY = currentY + t * unfoldAxisY
        // -> t = (targetY - currentY) / unfoldAxisY
        double t = (targetY - currentY) / unfoldAxisY;

        // 根据 't' 计算新的 X 和 Z 坐标
        // newPos = currentPos + t * unfoldAxis
        double newX = playerPos.x + t * unfoldAxis.x;
        double newZ = playerPos.z + t * unfoldAxis.z;

        return new Vec3d(newX, targetY, newZ);
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
        // 1. 优先处理玩家原地向上跳跃的场景 (这个逻辑仍然有价值，可以快速处理正上方的平台)
        if (player.getVelocity().y > 0.1) { // 使用一个略大的阈值避免走路时的小跳动触发
            RaycastContext jumpContext = new RaycastContext(
                    player.getPos(),
                    player.getPos().add(0, MAX_DISTANCE, 0),
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player
            );
            BlockHitResult hit = world.raycast(jumpContext);
            if (isValidHit(hit) && hit.getSide() == Direction.UP && isSafePlatform(world, hit.getBlockPos(), Direction.UP)) {
                return Vec3d.ofBottomCenter(hit.getBlockPos().up());
            }
        }


        // 2. 实现统一的单辅助线逻辑
        Vec3d unfoldAxis = getUnfoldAxisDirection(UnfoldManager.currentDirection);
        BlockPos currentStandingBlockPos = player.getBlockPos().down();
        int currentStandingY = world.isAir(currentStandingBlockPos) ? -1 : currentStandingBlockPos.getY();
        float hw = player.getWidth() / 2f;
        float inward_offset = 0.01f;

        // 【最终修正】
        // 基于用户的精确反馈，直接映射正确的几何关系
        float pivotXOffset, pivotZOffset;
        float inwardXOffset, inwardZOffset;

        ViewDirection direction = UnfoldManager.currentDirection;
        switch (direction) {
            case ISO_POS_X_POS_Y:      // 东北视角
            case ISO_POS_X_POS_Y_2:
                // 正确的 "下方" 顶点是 (+X, -Z)
                pivotXOffset = hw;
                pivotZOffset = -hw;
                inwardXOffset = -inward_offset;
                inwardZOffset = inward_offset;
                break;

            case ISO_POS_X_NEG_Y:      // 东南视角
            case ISO_POS_X_NEG_Y_2:
                // 正确的 "下方" 顶点是 (+X, +Z)
                pivotXOffset = hw;
                pivotZOffset = hw;
                inwardXOffset = -inward_offset;
                inwardZOffset = -inward_offset;
                break;

            case ISO_NEG_X_POS_Y:      // 西北视角
            case ISO_NEG_X_POS_Y_2:
                // 正确的 "下方" 顶点是 (-X, -Z)
                pivotXOffset = -hw;
                pivotZOffset = -hw;
                inwardXOffset = inward_offset;
                inwardZOffset = inward_offset;
                break;

            case ISO_NEG_X_NEG_Y:      // 西南视角 (这个是之前唯一正确的)
            case ISO_NEG_X_NEG_Y_2:
                // 正确的 "下方" 顶点是 (-X, +Z)
                pivotXOffset = -hw;
                pivotZOffset = hw;
                inwardXOffset = inward_offset;
                inwardZOffset = -inward_offset;
                break;

            default:
                return null;
        }

        Vec3d pivotPoint = player.getPos().add(pivotXOffset + inwardXOffset, 0, pivotZOffset + inwardZOffset);
        // 【修正结束】


        // 3. 探测高处（从相机方向射向玩家）
        Vec3d highProbeStart = pivotPoint.subtract(unfoldAxis.multiply(MAX_DISTANCE));
        RaycastContext highProbeContext = new RaycastContext(highProbeStart, pivotPoint, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
        BlockHitResult highHit = world.raycast(highProbeContext);

        if (isValidHit(highHit) && highHit.getSide() == Direction.UP) {
            BlockPos targetBlock = highHit.getBlockPos();
            if (targetBlock.getY() > currentStandingY && isSafePlatform(world, targetBlock, Direction.UP)) {
                return projectPlayerToTargetY(player, targetBlock.getY(), unfoldAxis);
            }
        }

        // 4. 探测低处（从玩家方向射向远处）
        Vec3d lowProbeEnd = pivotPoint.add(unfoldAxis.multiply(MAX_DISTANCE));
        RaycastContext lowProbeContext = new RaycastContext(pivotPoint, lowProbeEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
        BlockHitResult lowHit = world.raycast(lowProbeContext);

        if (isValidHit(lowHit) && lowHit.getSide() == Direction.UP) {
            BlockPos targetBlock = lowHit.getBlockPos();
            if (targetBlock.getY() < currentStandingY && isSafePlatform(world, targetBlock, Direction.UP)) {
                return projectPlayerToTargetY(player, targetBlock.getY(), unfoldAxis);
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
     * 判断一个射线检测结果是否有效（即命中了方块）。
     *
     * @param hitResult 射线检测结果。
     * @return 如果结果不为 {@code null} 且类型为 {@link HitResult.Type#BLOCK} 则返回 {@code true}。
     */
    private static boolean isValidHit(BlockHitResult hitResult) {
        return hitResult != null && hitResult.getType() == HitResult.Type.BLOCK;
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
        // 从玩家当前位置向上发射 MAX_DISTANCE 长的射线。
        RaycastContext context = new RaycastContext(player.getPos(), player.getPos().add(0, MAX_DISTANCE, 0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
        BlockHitResult hit = world.raycast(context);
        // 如果命中方块且其上方是安全平台，则返回瞬移目标位置。
        if (isValidHit(hit) && isSafePlatform(world, hit.getBlockPos(), Direction.UP)) {
            return Vec3d.ofBottomCenter(hit.getBlockPos().up());
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
        // 获取当前展开模式的“深度”轴方向（通常是与玩家视角相对的方向）。
        Vec3d unfoldAxis = getUnfoldAxisDirection(UnfoldManager.currentDirection);
        Vec3d origin = player.getPos();
        // 射线的方向是展开轴的反方向，模拟向后下落。
        Vec3d rayDir = unfoldAxis.negate();

        // 1. 先向后方发射短射线，检测玩家后方是否有方块（判断是否站在边缘）。
        RaycastContext context = new RaycastContext(origin, origin.add(rayDir.multiply(player.getWidth() * 2)), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
        BlockHitResult hit = world.raycast(context);
        // 如果没有命中方块，说明后方是空气，不是边缘下落，不触发瞬移。
        if (hit.getType() != HitResult.Type.BLOCK) return null;

        // 计算一个位于命中方块后方，略微进入空气的位置，作为第二条射线的起点。
        // 这模拟了玩家从边缘“跌落”到空气中。
        Vec3d edgeAirPos = hit.getPos().add(rayDir.multiply(EPSILON));

        // 2. 从这个“边缘空气位置”垂直向下发射长射线，寻找下方的平台。
        context = new RaycastContext(edgeAirPos, edgeAirPos.add(0, -MAX_DISTANCE, 0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
        hit = world.raycast(context);
        // 如果命中方块且其上方是安全平台，则返回瞬移目标位置。
        if (isValidHit(hit) && isSafePlatform(world, hit.getBlockPos(), Direction.UP)) {
            return Vec3d.ofBottomCenter(hit.getBlockPos().up());
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
     * 在斜向模式下，从玩家身体周围发射四条射线，沿着展开轴方向探测方块。
     * 这些射线用于识别视觉上重叠的平台，无论是高于还是低于玩家。
     *
     * @param player     客户端玩家实体。
     * @param world      客户端世界。
     * @param unfoldAxis 当前等轴测视图的“深度”方向向量。
     * @return 包含四条射线检测结果的 {@code List<BlockHitResult>}。
     */
    private static List<BlockHitResult> castIsometricProbes(ClientPlayerEntity player, ClientWorld world, Vec3d unfoldAxis) {
        List<BlockHitResult> results = new ArrayList<>();
        Vec3d center = player.getPos(); // 玩家的中心位置。
        // 玩家宽度的一半，减去一个小的偏移量 EPSILON，使射线从碰撞箱内部稍微开始，更精确地探测边缘。
        float hw = player.getWidth() / 2f - (float) EPSILON;

        // 定义四条射线的起始点，位于玩家当前Y平面上，大致对应玩家碰撞箱的四个角。
        Vec3d[] origins = {
                center.add(0, 0, -hw), // 玩家Z轴负方向
                center.add(0, 0, hw),  // 玩家Z轴正方向
                center.add(-hw, 0, 0), // 玩家X轴负方向
                center.add(hw, 0, 0)   // 玩家X轴正方向
        };

        // 对每个起始点发射射线，方向为 unfoldAxis，长度为 MAX_DISTANCE。
        for (Vec3d origin : origins) {
            RaycastContext context = new RaycastContext(origin, origin.add(unfoldAxis.multiply(MAX_DISTANCE)), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
            results.add(world.raycast(context));
        }
        return results;
    }

    /**
     * 根据当前的视角方向（{@link ViewDirection}）计算展开模式下的“深度”轴方向向量。
     * 这个向量指向摄像机所看向的“深处”，考虑了俯仰角和偏航角。
     *
     * @param viewDirection 当前的固定视角方向。
     * @return 标准化后的三维向量，表示展开模式的深度轴。
     */
    private static Vec3d getUnfoldAxisDirection(ViewDirection viewDirection) {
        // 将偏航角和俯仰角转换为弧度。
        float yawRad = viewDirection.yaw * (float) (Math.PI / 180.0);
        float pitchRad = viewDirection.pitch * (float) (Math.PI / 180.0);
        // 根据球坐标系公式计算出三维方向向量。
        // 注意：Minecraft 的偏航角和坐标系与标准有所不同，这里需要适配。
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        // 返回标准化后的向量。
        return new Vec3d(x, y, z).normalize();
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

        // 3. 发射垂直向下的探测射线。
        // 射线从玩家前方略微偏移的位置，纯粹向下探测。
        RaycastContext context = new RaycastContext(
                origin,
                origin.add(0, -MAX_DISTANCE, 0), // 纯粹向下探测，寻找下方平台。
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        );

        BlockHitResult hit = world.raycast(context);

        // 如果射线命中了方块。
        if (isValidHit(hit)) {
            BlockPos targetBlock = hit.getBlockPos();

            // 【已修复】使用最直接可靠的方法：player.getBlockPos().down()
            // 获取玩家当前站立的方块位置（如果玩家在地面上）。
            // 注意：当玩家在空中时，这个值可能代表玩家下方的空气方块，而不是实际站立的方块。
            BlockPos currentStandingBlock = player.getBlockPos().down();

            // 检查找到的平台是否与玩家当前站立的平台不同（避免瞬移到同一个方块上）。
            // 并且，如果玩家正下方没有方块（即玩家确实悬空），此判断也应视为"新平台"。
            boolean isNewPlatform = !targetBlock.equals(currentStandingBlock);

            // 如果目标平台是安全可站立的，并且它是一个新平台（与当前位置不同）。
            if (isSafePlatform(world, targetBlock, Direction.UP) && isNewPlatform) {
                // 瞬移目标是：保持被击中平台的X和Z，但Y值是平台上方。
                Vec3d targetPos = Vec3d.ofBottomCenter(targetBlock.up());
                UnfoldMod.LOGGER.info("Air strafe opportunity found at {}", targetPos);
                return targetPos;
            }
        }

        return null; // 没有找到合适的瞬移目标。
    }
}