package pama1234.unfold.logic;

import net.minecraft.client.MinecraftClient;
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
import pama1234.unfold.util.ViewDirection;

import java.util.ArrayList;
import java.util.List;

public class UnfoldTeleportLogic {

    private static final double MAX_DISTANCE = 64.0;
    private static final double EPSILON = 0.01;

    /**
     * 主入口点，每帧调用一次，检查并执行任何可能的瞬移。
     */
    public static boolean checkAndPerformTeleport(ClientPlayerEntity player) {
        if (!UnfoldManager.unfolding || player.getAbilities().flying) {
            return false;
        }

        ClientWorld world = player.clientWorld;
        UnfoldMode currentMode = UnfoldManager.currentMode;

        Vec3d targetPos = null;

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

        if (targetPos != null) {
            teleportPlayer(player, targetPos);
            return true;
        }
        return false;
    }

    // --- 垂直模式逻辑 ---
    private static Vec3d handleVerticalTeleport(ClientPlayerEntity player, ClientWorld world) {
        if (player.isOnGround()) {
            return null;
        }

        List<Vec3d> origins = getVerticalProbeOrigins(player);
        Direction direction = player.getVelocity().y > 0 ? Direction.UP : Direction.DOWN;

        for (Vec3d origin : origins) {
            RaycastContext context = new RaycastContext(origin, origin.add(Vec3d.of(direction.getVector()).multiply(MAX_DISTANCE)),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
            BlockHitResult hit = world.raycast(context);

            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos targetBlock = hit.getBlockPos();
                if (isSafePlatform(world, targetBlock, direction)) {
                    return new Vec3d(player.getX(), Vec3d.ofBottomCenter(targetBlock.offset(direction)).y, player.getZ());
                }
            }
        }
        return null;
    }

    // --- 水平模式逻辑 ---
    private static Vec3d handleHorizontalTeleport(ClientPlayerEntity player, ClientWorld world) {
        // 【已修复】使用 player.jumping 字段
        if (player.jumping) {
            Vec3d target = checkHorizontalJump(player, world);
            if (target != null) return target;
        }

        if (player.isSneaking() && isAtEdge(player, world)) {
            Vec3d target = checkHorizontalSneak(player, world);
            if (target != null) return target;
        }

        if (player.isOnGround() && isAboutToCollide(player, world)) {
            Vec3d target = checkHorizontalWalk(player, world);
            if (target != null) return target;
        }

        return null;
    }

    // --- 斜向模式逻辑 ---
    private static Vec3d handleIsometricTeleport(ClientPlayerEntity player, ClientWorld world) {
        if (player.isOnGround()) {
            return null;
        }

        Vec3d unfoldAxis = getUnfoldAxisDirection(UnfoldManager.currentDirection);
        List<BlockHitResult> hits = castIsometricProbes(player, world, unfoldAxis);

        BlockHitResult frontHit = hits.get(0);
        BlockHitResult backHit = hits.get(1);
        BlockHitResult leftHit = hits.get(2);
        BlockHitResult rightHit = hits.get(3);

        double currentY = player.getY();

        if (isValidHit(backHit) && isValidHit(rightHit) && backHit.getBlockPos().getY() < currentY && rightHit.getBlockPos().getY() < currentY) {
            return Vec3d.ofBottomCenter(backHit.getBlockPos().up());
        }
        if (isValidHit(backHit) && isValidHit(leftHit) && backHit.getBlockPos().getY() < currentY && leftHit.getBlockPos().getY() < currentY) {
            return Vec3d.ofBottomCenter(backHit.getBlockPos().up());
        }
        if (isValidHit(frontHit) && isValidHit(rightHit) && frontHit.getBlockPos().getY() > currentY && rightHit.getBlockPos().getY() > currentY) {
            return Vec3d.ofBottomCenter(frontHit.getBlockPos().up());
        }
        if (isValidHit(frontHit) && isValidHit(leftHit) && frontHit.getBlockPos().getY() > currentY && leftHit.getBlockPos().getY() > currentY) {
            return Vec3d.ofBottomCenter(frontHit.getBlockPos().up());
        }

        return null;
    }

    // --- 辅助方法和几何定义 ---

    private static void teleportPlayer(ClientPlayerEntity player, Vec3d targetPos) {
        player.setPos(targetPos.x, targetPos.y, targetPos.z);
        player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);
        UnfoldMod.LOGGER.info("Teleported to {}", targetPos);
    }

    private static boolean isSafePlatform(ClientWorld world, BlockPos pos, Direction surface) {
        BlockPos standPos = pos.offset(surface);
        return world.isAir(standPos) && world.isAir(standPos.up());
    }

    private static boolean isValidHit(BlockHitResult hitResult) {
        return hitResult != null && hitResult.getType() == HitResult.Type.BLOCK;
    }

    private static List<Vec3d> getVerticalProbeOrigins(ClientPlayerEntity player) {
        List<Vec3d> origins = new ArrayList<>();
        Vec3d center = player.getPos();
        float hw = player.getWidth() / 2f + (float)EPSILON;
        origins.add(center.add(hw, 0, 0));
        origins.add(center.add(-hw, 0, 0));
        origins.add(center.add(0, 0, hw));
        origins.add(center.add(0, 0, -hw));
        return origins;
    }

    private static Vec3d checkHorizontalJump(ClientPlayerEntity player, ClientWorld world) {
        RaycastContext context = new RaycastContext(player.getPos(), player.getPos().add(0, MAX_DISTANCE, 0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
        BlockHitResult hit = world.raycast(context);
        if (isValidHit(hit) && isSafePlatform(world, hit.getBlockPos(), Direction.UP)) {
            return Vec3d.ofBottomCenter(hit.getBlockPos().up());
        }
        return null;
    }

    private static Vec3d checkHorizontalSneak(ClientPlayerEntity player, ClientWorld world) {
        Vec3d unfoldAxis = getUnfoldAxisDirection(UnfoldManager.currentDirection);
        Vec3d origin = player.getPos();
        Vec3d rayDir = unfoldAxis.negate();

        RaycastContext context = new RaycastContext(origin, origin.add(rayDir.multiply(player.getWidth() * 2)), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
        BlockHitResult hit = world.raycast(context);
        if (hit.getType() != HitResult.Type.BLOCK) return null;

        Vec3d edgeAirPos = hit.getPos().add(rayDir.multiply(EPSILON));

        context = new RaycastContext(edgeAirPos, edgeAirPos.add(0, -MAX_DISTANCE, 0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
        hit = world.raycast(context);
        if (isValidHit(hit) && isSafePlatform(world, hit.getBlockPos(), Direction.UP)) {
            return Vec3d.ofBottomCenter(hit.getBlockPos().up());
        }
        return null;
    }

    private static Vec3d checkHorizontalWalk(ClientPlayerEntity player, ClientWorld world) {
        return null;
    }

    private static boolean isAtEdge(ClientPlayerEntity player, ClientWorld world) {
        return !world.getBlockState(player.getBlockPos().down()).isSolid();
    }

    // 【已修复】传入 world 参数
    private static boolean isAboutToCollide(ClientPlayerEntity player, ClientWorld world) {
        Vec3d moveVec = player.getVelocity();
        if (moveVec.lengthSquared() < 0.001) return false;
        Box futureBox = player.getBoundingBox().offset(moveVec.normalize().multiply(0.2));
        return !world.isSpaceEmpty(futureBox);
    }

    private static List<BlockHitResult> castIsometricProbes(ClientPlayerEntity player, ClientWorld world, Vec3d unfoldAxis) {
        List<BlockHitResult> results = new ArrayList<>();
        Vec3d center = player.getPos();
        float hw = player.getWidth() / 2f - (float)EPSILON;

        Vec3d[] origins = {
                center.add(0, 0, -hw),
                center.add(0, 0, hw),
                center.add(-hw, 0, 0),
                center.add(hw, 0, 0)
        };

        for (Vec3d origin : origins) {
            RaycastContext context = new RaycastContext(origin, origin.add(unfoldAxis.multiply(MAX_DISTANCE)), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
            results.add(world.raycast(context));
        }
        return results;
    }

    private static Vec3d getUnfoldAxisDirection(ViewDirection viewDirection) {
        float yawRad = viewDirection.yaw * (float)(Math.PI / 180.0);
        float pitchRad = viewDirection.pitch * (float)(Math.PI / 180.0);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        return new Vec3d(x, y, z).normalize();
    }
}