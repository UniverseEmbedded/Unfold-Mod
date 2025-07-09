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
     * 【第五版修正算法 - 回归初心】
     * 算法目标：只修正玩家的“深度”，不干涉其XY移动。
     * 1. 从摄像机发射一条射线，穿过玩家的当前位置。
     * 2. 找到射线与世界的第一个交点。
     * 3. 这个交点就是玩家“应该”在的位置。
     * @param player 玩家实体
     * @return 修正后的安全降落点，如果找不到则返回null。
     */
    public static Vec3d getCorrectedPosition(ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        Camera camera = client.gameRenderer.getCamera();

        if (world == null || !camera.isThirdPerson()) {
            return null;
        }

        // 1. 定义射线的起点和方向
        Vec3d cameraPos = camera.getPos();
        // 射线方向：从摄像机指向玩家模型的中心
        Vec3d rayDirection = player.getPos().add(0, player.getHeight() / 2, 0).subtract(cameraPos).normalize();
        Vec3d rayEnd = cameraPos.add(rayDirection.multiply(MAX_DISTANCE));

        // 2. 发射射线
        RaycastContext context = new RaycastContext(cameraPos, rayEnd, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, player);
        BlockHitResult hitResult = world.raycast(context);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            // 3. 计算并验证安全降落点
            BlockPos landingSurfacePos = hitResult.getBlockPos();
            BlockPos landingAirPos = landingSurfacePos.offset(hitResult.getSide());

            // 检查目标位置是否是一个有效的“表面”
            // 比如，不能传送到墙壁的侧面去（除非那里正好有空间）
            if (isSafeLanding(world, landingAirPos, player.getBoundingBox())) {
                // 将玩家的X和Z坐标，设置为击中点的X和Z坐标
                // Y坐标设置为安全站立的高度
                // 这样可以最大程度保留玩家的平面移动，只修正高度
                return new Vec3d(hitResult.getPos().x, landingAirPos.getY(), hitResult.getPos().z);
            }
        }

        return null;
    }


    // isSafeLanding 方法再次简化，以适应新的逻辑
    private static boolean isSafeLanding(ClientWorld world, BlockPos pos, Box playerBoundingBox) {
        // 玩家脚下的方块必须是实体
        if (world.getBlockState(pos.down()).isAir()) {
            return false;
        }

        // 玩家身体所在的区域必须是空的
        // 创建一个以目标站立点为中心的新碰撞箱
        Box targetBox = playerBoundingBox.offset(Vec3d.ofBottomCenter(pos).subtract(MinecraftClient.getInstance().player.getPos()));
        return world.isSpaceEmpty(targetBox);
    }
}