package pama1234.unfold.logic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text; // 引入Text
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
// import pama1234.unfold.util.ViewDirection; // 暂时不用

import java.util.ArrayList;
import java.util.List;

public class UnfoldTeleportLogic {
    private static final double MAX_DISTANCE = 64.0;
    // 稍微增加试探距离，使其更可能越过边缘
    private static final double STEP_DISTANCE = 0.4;

    public static Vec3d findTeleportTarget(ClientPlayerEntity player) {
        // --- 诊断修复 #1: 暂时移除模式检查，让所有模式都生效 ---
        // ViewDirection currentDirection = UnfoldManager.currentDirection;
        // if (currentDirection != ViewDirection.POS_Y && currentDirection != ViewDirection.NEG_Y) {
        //     player.sendMessage(Text.of("DEBUG: Logic skipped, wrong mode."), false);
        //     return null;
        // }

        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return null;

        Vec3d playerPos = player.getPos();
        Vec3d rotationVec = player.getRotationVector();
        Vec3d moveDirection = new Vec3d(rotationVec.x, 0, rotationVec.z).normalize();

        if (moveDirection.lengthSquared() < 0.001) {
            return null;
        }

        // --- 诊断修复 #2: 简化并调试“即将悬空”逻辑 ---
        Box playerBox = player.getBoundingBox();
        // 计算玩家前方的检测区域
        Box checkBox = playerBox.offset(moveDirection.multiply(STEP_DISTANCE));

        // 如果玩家前方的区域内没有任何实体方块（意味着脚下是空的），则触发
        if (world.isSpaceEmpty(checkBox.offset(0, -0.1, 0))) {
            // 条件满足，可以开始寻找平台了
            player.sendMessage(Text.of("DEBUG: Imminent void detected! Searching for platform..."), false);
            return findLandingPlatform(player, moveDirection);
        } else {
            // player.sendMessage(Text.of("DEBUG: Still on solid ground."), false); // 这个太刷屏，先注释掉
            return null;
        }
    }

    private static Vec3d findLandingPlatform(ClientPlayerEntity player, Vec3d moveDirection) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return null;

        float playerWidth = player.getWidth();
        Vec3d rightVector = moveDirection.crossProduct(new Vec3d(0, 1, 0)).normalize();

        // 射线起点现在从玩家脚底发出，更稳定
        Vec3d baseOrigin = player.getPos().add(moveDirection.multiply(playerWidth / 2 + 0.01));

        List<Vec3d> rayOrigins = new ArrayList<>();
        rayOrigins.add(baseOrigin);
        rayOrigins.add(baseOrigin.add(rightVector.multiply(playerWidth / 2)));
        rayOrigins.add(baseOrigin.subtract(rightVector.multiply(playerWidth / 2)));

        Vec3d rayDirection = new Vec3d(0, -1, 0);

        for (Vec3d origin : rayOrigins) {
            Vec3d rayEnd = origin.add(rayDirection.multiply(MAX_DISTANCE));
            RaycastContext context = new RaycastContext(origin, rayEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
            BlockHitResult hitResult = world.raycast(context);

            if (hitResult.getType() == HitResult.Type.BLOCK) {
                BlockPos landingPos = hitResult.getBlockPos();
                Vec3d targetPos = Vec3d.ofBottomCenter(landingPos.up());
                if (world.isAir(landingPos.up()) && world.isAir(landingPos.up(2))) {
                    player.sendMessage(Text.of("DEBUG: Platform found at " + landingPos.toString()), false);
                    return targetPos;
                }
            }
        }

        player.sendMessage(Text.of("DEBUG: No platform found by any ray."), false);
        return null;
    }
}