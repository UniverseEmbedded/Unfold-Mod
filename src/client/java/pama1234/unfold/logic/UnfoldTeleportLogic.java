package pama1234.unfold.logic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class UnfoldTeleportLogic {
    private static final double MAX_DISTANCE = 64.0;

    public static Vec3d findTeleportLocation() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            return null;
        }

        // 1. 获取Ortho视角下的观察方向向量
        Vec3d viewVector = Vec3d.fromPolar(UnfoldManager.currentDirection.pitch, UnfoldManager.currentDirection.yaw);
        Vec3d startPos = player.getEyePos();
        Vec3d endPos = startPos.add(viewVector.multiply(MAX_DISTANCE));

        // 2. 进行射线检测，找到玩家“看向”的方块
        RaycastContext context = new RaycastContext(startPos, endPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
        BlockHitResult hitResult = world.raycast(context);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            // 3. 目标位置是击中方块前的一个方块
            BlockPos targetBlockPos = hitResult.getBlockPos().offset(hitResult.getSide().getOpposite());

            // 4. 检查目标位置是否安全（脚下有方块，身体是空气）
            if (isSafeLanding(world, targetBlockPos, player.getBoundingBox())) {
                return Vec3d.ofBottomCenter(targetBlockPos);
            }
        }
        return null;
    }

    private static boolean isSafeLanding(ClientWorld world, BlockPos pos, Box playerBoundingBox) {
        // 脚下必须是实体方块
        if (world.getBlockState(pos.down()).isAir()) {
            return false;
        }
        // 玩家所站的位置和头顶必须是空气
        if (!world.isAir(pos) || !world.isAir(pos.up())) {
            return false;
        }
        // 检查传送后的碰撞箱是否会与其他方块冲突
        return world.isSpaceEmpty(playerBoundingBox.offset(Vec3d.ofBottomCenter(pos).subtract(MinecraftClient.getInstance().player.getPos())));
    }
}