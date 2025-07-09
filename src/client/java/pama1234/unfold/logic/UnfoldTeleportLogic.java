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
import org.joml.Vector3f;

public class UnfoldTeleportLogic {
    private static final double MAX_DISTANCE = 256.0;

    /**
     * 【终极修正版核心算法】
     * 1. 获取玩家在屏幕空间中的位置。
     * 2. 将这个屏幕点反向投影回3D世界，形成一条新的射线。
     * 3. 这条新射线与世界的第一个交点，就是玩家应该在的位置。
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

        // --- 核心算法重写 ---

        // 1. 获取摄像机视角的关键向量
        // 摄像机的位置是射线的真正起点
        Vec3d cameraPos = camera.getPos();
        // 摄像机的“上”方向
        Vector3f upVec = new Vector3f(0, 1, 0);
        camera.getRotation().transform(upVec);
        // 摄像机的“右”方向 (通过叉乘计算)
        Vec3d forwardVec = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        Vec3d rightVec = forwardVec.crossProduct(new Vec3d(upVec.x, upVec.y, upVec.z));

        // 2. 计算玩家模型相对于摄像机中心的屏幕空间偏移
        // 这是模拟玩家通过WASD在屏幕上移动的关键
        // 我们需要将玩家的移动速度(velocity)投影到摄像机的right和up向量上
        double dx = player.getVelocity().dotProduct(rightVec);
        double dy = player.getVelocity().dotProduct(new Vec3d(upVec.x, upVec.y, upVec.z));

        // 3. 计算出射线应该“穿过”的3D世界点
        // 这个点位于摄像机前方一个固定距离的平面上，并根据玩家的移动进行了偏移
        // 这就将2D的移动意图，转化为了3D的瞄准点
        Vec3d aimPoint = cameraPos.add(forwardVec) // 在摄像机正前方1米处
                .add(rightVec.multiply(dx * 50)) // 根据水平速度偏移
                .add(new Vec3d(upVec.x, upVec.y, upVec.z).multiply(dy * 50)); // 根据垂直速度偏移

        // 4. 从摄像机位置，向计算出的瞄准点发射射线
        Vec3d rayStart = cameraPos;
        // 射线方向是从摄像机指向瞄准点
        Vec3d rayDirection = aimPoint.subtract(rayStart).normalize();
        Vec3d rayEnd = rayStart.add(rayDirection.multiply(MAX_DISTANCE));

        RaycastContext context = new RaycastContext(rayStart, rayEnd, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, player);
        BlockHitResult hitResult = world.raycast(context);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            // 5. 计算安全的降落点
            BlockPos targetBlockPos = hitResult.getBlockPos().offset(hitResult.getSide());
            if (isSafeLanding(world, targetBlockPos, player.getBoundingBox())) {
                return Vec3d.ofBottomCenter(targetBlockPos);
            }
        }
        return null;
    }

    // isSafeLanding 方法保持不变
    private static boolean isSafeLanding(ClientWorld world, BlockPos pos, Box playerBoundingBox) {
        if (world.getBlockState(pos.down()).isAir()) {
            return false;
        }
        if (!world.isAir(pos) || !world.isAir(pos.up())) {
            return false;
        }
        Box targetBox = playerBoundingBox.offset(Vec3d.ofBottomCenter(pos).subtract(MinecraftClient.getInstance().player.getPos()));
        return world.isSpaceEmpty(targetBox);
    }
}