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
import pama1234.unfold.util.ViewDirection;

import java.util.ArrayList;
import java.util.List;

public class UnfoldTeleportLogic {
    private static final double MAX_DISTANCE = 64.0;
    private static final double STEP_DISTANCE = 0.3;

    public static Vec3d findTeleportTarget(ClientPlayerEntity player) {
        ViewDirection currentDirection = UnfoldManager.currentDirection;
        if (currentDirection != ViewDirection.POS_Y && currentDirection != ViewDirection.NEG_Y) {
            return null;
        }

        // --- 修复 #1: 使用 MinecraftClient.getInstance().world ---
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return null;

        Vec3d playerPos = player.getPos();

        // --- 修复 #2: 手动将Y分量置零以获取水平移动方向 ---
        Vec3d rotationVec = player.getRotationVector();
        Vec3d moveDirection = new Vec3d(rotationVec.x, 0, rotationVec.z).normalize();

        if (moveDirection.lengthSquared() < 0.001) { // 避免零向量
            return null;
        }

        Vec3d nextStepPos = playerPos.add(moveDirection.multiply(STEP_DISTANCE));
        BlockPos nextStepBlockPos = BlockPos.ofFloored(nextStepPos);

        if (!world.getBlockState(nextStepBlockPos.down()).isAir()) {
            return null;
        }
        if (world.getBlockState(player.getBlockPos().down()).isAir()) {
            return null;
        }

        return findLandingPlatform(player, moveDirection);
    }

    private static Vec3d findLandingPlatform(ClientPlayerEntity player, Vec3d moveDirection) {
        // --- 修复 #1 (再次应用): 使用 MinecraftClient.getInstance().world ---
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return null;

        Box boundingBox = player.getBoundingBox();
        float playerWidth = player.getWidth();

        Vec3d rightVector = moveDirection.crossProduct(new Vec3d(0, 1, 0)).normalize();

        List<Vec3d> rayOrigins = new ArrayList<>();
        Vec3d centerOrigin = player.getPos().add(moveDirection.multiply(playerWidth / 2 + 0.01));
        rayOrigins.add(centerOrigin);
        rayOrigins.add(centerOrigin.add(rightVector.multiply(playerWidth / 2)));
        rayOrigins.add(centerOrigin.subtract(rightVector.multiply(playerWidth / 2)));

        Vec3d rayDirection = new Vec3d(0, -1, 0);

        for (Vec3d origin : rayOrigins) {
            Vec3d rayEnd = origin.add(rayDirection.multiply(MAX_DISTANCE));
            RaycastContext context = new RaycastContext(origin, rayEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
            BlockHitResult hitResult = world.raycast(context);

            if (hitResult.getType() == HitResult.Type.BLOCK) {
                BlockPos landingPos = hitResult.getBlockPos();
                Vec3d targetPos = Vec3d.ofBottomCenter(landingPos.up());
                if (world.isAir(landingPos.up()) && world.isAir(landingPos.up(2))) {
                    return targetPos;
                }
            }
        }
        return null;
    }
}