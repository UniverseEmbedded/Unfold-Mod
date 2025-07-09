package pama1234.unfold.logic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import pama1234.unfold.util.ViewDirection;

public class UnfoldTeleportLogic {
    private static final double MAX_DISTANCE = 64.0;
    private static final double PROJECTION_OFFSET = 8.0; // 投影点偏移距离

    /**
     * 核心传送目标查找函数
     * 实现"条件触发式边缘位移"算法
     */
    public static Vec3d findTeleportTarget(ClientPlayerEntity player) {
        // 检查是否在展开模式
        if (!UnfoldManager.unfolding) {
            return null;
        }

        // 飞行模式豁免：如果玩家在飞行，完全禁用传送逻辑
        if (player.getAbilities().flying) {
            return null;
        }

        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return null;

        // 精确的触发条件检查
        if (!shouldTriggerTeleport(player, world)) {
            return null;
        }

        // 获取玩家的移动输入并转换为期望移动方向
        Vec3d desiredMoveDirection = getDesiredMoveDirection(player);
        if (desiredMoveDirection.lengthSquared() < 0.001) {
            return null; // 没有移动输入
        }

        player.sendMessage(Text.of("DEBUG: Teleport triggered! Searching for target..."), false);

        // 执行基于展开轴的位置投影搜索
        return findLandingPlatformWithProjection(player, desiredMoveDirection);
    }

    /**
     * 精确的触发条件检查
     * 只有当玩家即将或已经离开物理支撑时才触发
     */
    private static boolean shouldTriggerTeleport(ClientPlayerEntity player, ClientWorld world) {
        // 条件1：玩家的Y轴速度不为零（正在跳跃或下落）
        boolean isMovingVertically = Math.abs(player.getVelocity().y) > 0.001;

        // 条件2：玩家脚下直接接触的方块是空气
        BlockPos playerFeetPos = player.getBlockPos();
        boolean isOnAir = world.isAir(playerFeetPos.down());

        return isMovingVertically || isOnAir;
    }

    /**
     * 根据当前展开轴获取期望移动方向
     * 实现WASD重映射：W/S控制展开轴方向，A/D控制垂直方向
     */
    private static Vec3d getDesiredMoveDirection(ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();

        // 获取移动输入
        boolean forward = client.options.forwardKey.isPressed();
        boolean backward = client.options.backKey.isPressed();
        boolean left = client.options.leftKey.isPressed();
        boolean right = client.options.rightKey.isPressed();

        // 计算输入向量（在玩家本地坐标系中）
        float forwardInput = 0.0f;
        float strafeInput = 0.0f;

        if (forward) forwardInput += 1.0f;
        if (backward) forwardInput -= 1.0f;
        if (right) strafeInput += 1.0f;
        if (left) strafeInput -= 1.0f;

        if (Math.abs(forwardInput) < 0.001f && Math.abs(strafeInput) < 0.001f) {
            return Vec3d.ZERO;
        }

        // 根据当前展开方向计算世界坐标系中的移动向量
        return convertInputToWorldDirection(forwardInput, strafeInput, UnfoldManager.currentDirection);
    }

    /**
     * 将玩家输入转换为世界坐标系中的移动方向
     * 基于展开轴重映射移动方向
     */
    private static Vec3d convertInputToWorldDirection(float forward, float strafe, ViewDirection viewDirection) {
        // 计算展开轴方向（从摄像机指向场景的方向）
        Vec3d unfoldAxis = getUnfoldAxisDirection(viewDirection);

        // 计算垂直于展开轴的右方向
        Vec3d rightAxis = unfoldAxis.crossProduct(new Vec3d(0, 1, 0)).normalize();

        // 如果展开轴是垂直的，需要特殊处理
        if (Math.abs(unfoldAxis.y) > 0.9) {
            // 对于俯视/仰视角度，使用世界坐标系的Z轴作为前进方向
            Vec3d forwardAxis = new Vec3d(0, 0, viewDirection == ViewDirection.POS_Y ? 1 : -1);
            rightAxis = forwardAxis.crossProduct(new Vec3d(0, 1, 0)).normalize();
            unfoldAxis = forwardAxis;
        }

        // 组合移动向量：forward控制展开轴方向，strafe控制垂直方向
        Vec3d result = unfoldAxis.multiply(forward).add(rightAxis.multiply(strafe));

        return result.lengthSquared() > 0.001 ? result.normalize() : Vec3d.ZERO;
    }

    /**
     * 根据视角方向计算展开轴方向
     */
    private static Vec3d getUnfoldAxisDirection(ViewDirection viewDirection) {
        float yawRad = viewDirection.yaw * (float)(Math.PI / 180.0);
        float pitchRad = viewDirection.pitch * (float)(Math.PI / 180.0);

        // 计算视角方向向量
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);

        return new Vec3d(x, y, z).normalize();
    }

    /**
     * 基于展开轴的期望位置投影搜索
     * 实现核心的"平行于展开轴的期望位置投影射线"算法
     */
    private static Vec3d findLandingPlatformWithProjection(ClientPlayerEntity player, Vec3d moveDirection) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return null;

        Vec3d playerPos = player.getPos();

        // 第一步：计算期望落点（玩家想要到达的位置）
        Vec3d desiredPosition = playerPos.add(moveDirection.multiply(2.0)); // 向前预测2格距离

        // 第二步：获取展开轴方向
        Vec3d unfoldAxis = getUnfoldAxisDirection(UnfoldManager.currentDirection);

        // 第三步：计算投影起点（在摄像机平面上的投影点）
        Vec3d projectionOrigin = calculateProjectionPoint(desiredPosition, unfoldAxis);

        // 第四步：发射平行于展开轴的探测射线
        return castRayAlongUnfoldAxis(player, projectionOrigin, unfoldAxis, world);
    }

    /**
     * 计算期望位置在摄像机平面上的投影点
     */
    private static Vec3d calculateProjectionPoint(Vec3d desiredPosition, Vec3d unfoldAxis) {
        // 沿展开轴向后偏移，创建一个更开阔的搜索起点
        return desiredPosition.subtract(unfoldAxis.multiply(PROJECTION_OFFSET));
    }

    /**
     * 沿展开轴发射射线寻找着陆平台
     */
    private static Vec3d castRayAlongUnfoldAxis(ClientPlayerEntity player, Vec3d origin, Vec3d unfoldAxis, ClientWorld world) {
        // 射线终点
        Vec3d rayEnd = origin.add(unfoldAxis.multiply(MAX_DISTANCE));

        // 执行射线投射
        RaycastContext context = new RaycastContext(
                origin,
                rayEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        );

        BlockHitResult hitResult = world.raycast(context);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos landingPos = hitResult.getBlockPos();

            // 检查着陆点是否安全（上方有足够空间）
            if (world.isAir(landingPos.up()) && world.isAir(landingPos.up(2))) {
                Vec3d targetPos = Vec3d.ofBottomCenter(landingPos.up());
                player.sendMessage(Text.of("DEBUG: Platform found at " + landingPos.toString()), false);
                return targetPos;
            } else {
                player.sendMessage(Text.of("DEBUG: Platform found but unsafe (blocked above)"), false);
            }
        }

        player.sendMessage(Text.of("DEBUG: No platform found along unfold axis"), false);
        return null;
    }
}