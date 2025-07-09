package pama1234.unfold.logic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class UnfoldTeleportLogic {
    private static final double MAX_DISTANCE = 256.0;

    /**
     * 【全新核心算法】
     * 持续计算玩家在当前Ortho视角下理论上应该在的正确位置。
     * 这个算法与玩家的移动意图解耦，只关心“视线遮挡”。
     * @return 修正后的安全降落点，如果找不到则返回null。
     */
    public static Vec3d findCorrectedPosition() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        Camera camera = client.gameRenderer.getCamera();

        if (player == null || world == null || !camera.isThirdPerson()) {
            return null;
        }

        // 1. 获取“展开线”的起点（摄像机位置）和方向（Ortho视角方向）
        Vec3d cameraPos = camera.getPos();
        Vec3d viewVector = Vec3d.fromPolar(UnfoldManager.currentDirection.pitch, UnfoldManager.currentDirection.yaw);

        // 2. 将玩家的当前位置投影到与摄像机垂直的平面上，以确定射线检测的精确起点。
        // 这是实现FEZ/纪念碑谷式遮挡逻辑的关键步骤。
        // 向量 a = player.getPos() -> camera.getPos()
        // 向量 b = viewVector (单位向量)
        // a 在 b 上的投影长度 = a.dot(b)
        // 投影向量 = b * 投影长度
        // 我们需要找到玩家在“展开线”上的垂直投影点，然后从这个点开始发射射线。
        Vec3d playerToCamera = cameraPos.subtract(player.getPos());
        double projectionLength = playerToCamera.dotProduct(viewVector);
        Vec3d projectedPlayerStart = cameraPos.subtract(viewVector.multiply(projectionLength));

        // 3. 从这个精确的投影点开始，沿着展开线方向进行射线检测
        Vec3d rayStart = projectedPlayerStart;
        Vec3d rayEnd = rayStart.add(viewVector.multiply(MAX_DISTANCE));

        RaycastContext context = new RaycastContext(rayStart, rayEnd, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, player);
        BlockHitResult hitResult = world.raycast(context);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            // 4. 计算出理论上正确的、安全的降落点
            BlockPos targetBlockPos = hitResult.getBlockPos().offset(hitResult.getSide());
            if (isSafeLanding(world, targetBlockPos, player.getBoundingBox())) {
                return Vec3d.ofBottomCenter(targetBlockPos);
            }
        }
        return null;
    }

    /**
     * 检查目标位置对玩家来说是否安全。
     * @param world 客户端世界
     * @param pos 目标方块坐标（玩家脚将要站立的空气方块）
     * @param playerBoundingBox 玩家的碰撞箱
     * @return 如果安全则返回true
     */
    private static boolean isSafeLanding(ClientWorld world, BlockPos pos, Box playerBoundingBox) {
        // 脚下必须是实体方块，不能是空气
        if (world.getBlockState(pos.down()).isAir()) {
            return false;
        }
        // 玩家所站的位置(脚)和头顶必须是空气
        if (!world.isAir(pos) || !world.isAir(pos.up())) {
            return false;
        }
        // 使用更健壮的方式检查玩家整个碰撞箱是否会与环境冲突
        Box targetBox = playerBoundingBox.offset(Vec3d.ofBottomCenter(pos).subtract(MinecraftClient.getInstance().player.getPos()));
        return world.isSpaceEmpty(targetBox);
    }
}