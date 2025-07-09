package pama1234.unfold;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import pama1234.unfold.logic.UnfoldManager;
import pama1234.unfold.logic.UnfoldTeleportLogic;
import pama1234.unfold.util.ViewDirection;

public class UnfoldModClient implements ClientModInitializer {
    private static KeyBinding toggleKey;
    private static Perspective originalPerspective;
    private static int toggleCooldown = 0;
    private static int teleportCooldown = 0;

    // 记录原始的玩家朝向，用于恢复
    private static float originalPlayerYaw = 0.0f;
    private static boolean hasStoredOriginalYaw = false;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.unfold.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F, "category.unfold"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = client.player;
            if (player == null) {
                return;
            }

            // 模式切换逻辑
            handleModeToggle(client, player);

            // 展开模式下的核心逻辑
            if (UnfoldManager.unfolding) {
                handleUnfoldMode(client, player);
            }
        });
    }

    /**
     * 处理模式切换逻辑
     */
    private void handleModeToggle(MinecraftClient client, ClientPlayerEntity player) {
        if (toggleCooldown > 0) toggleCooldown--;

        if (toggleKey.wasPressed() && toggleCooldown == 0) {
            toggleCooldown = 10;
            UnfoldManager.unfolding = !UnfoldManager.unfolding;

            if (client.options != null) {
                if (UnfoldManager.unfolding) {
                    // 进入展开模式
                    UnfoldManager.currentDirection = ViewDirection.getClosest(player);
                    UnfoldMod.LOGGER.info("Unfold Mode Activated: {}", UnfoldManager.currentDirection.name());


                    // 保存原始视角并切换到第三人称
                    originalPerspective = client.options.getPerspective();
                    client.options.setPerspective(Perspective.THIRD_PERSON_BACK);

                    // 保存原始玩家朝向
                    originalPlayerYaw = player.getYaw();
                    hasStoredOriginalYaw = true;

                } else {
                    // 退出展开模式
                    UnfoldMod.LOGGER.info("Unfold Mode Deactivated");

                    // 恢复原始视角
                    if (originalPerspective != null) {
                        client.options.setPerspective(originalPerspective);
                        originalPerspective = null;
                    }

                    // 恢复原始玩家朝向
                    if (hasStoredOriginalYaw) {
                        player.setYaw(originalPlayerYaw);
                        hasStoredOriginalYaw = false;
                    }
                }
            }
        }
    }

    /**
     * 处理展开模式下的核心逻辑
     */
    private void handleUnfoldMode(MinecraftClient client, ClientPlayerEntity player) {
        // 【已修复】统一处理玩家的移动和朝向
        handlePlayerMovementAndOrientation(client, player);

        // 处理传送逻辑
        handleTeleportLogic(player);
    }

    /**
     * 【新方法】处理玩家的移动和朝向，将两者解耦
     */
    private void handlePlayerMovementAndOrientation(MinecraftClient client, ClientPlayerEntity player) {
        // --- 1. 锁定角色朝向 ---
        // 目标朝向应与摄像机朝向一致
        float targetYaw = UnfoldManager.currentDirection.yaw;
        // 平滑地将当前朝向插值到目标朝向，以避免突兀的旋转
        float yawDiff = MathHelper.wrapDegrees(targetYaw - player.getYaw());
        float newYaw = player.getYaw() + yawDiff * 0.4f; // 使用40%的插值率，可以调整这个值以获得不同手感
        player.setYaw(newYaw);

        // --- 2. 手动控制移动速度 ---
        // 根据WASD输入计算世界坐标系下的期望移动方向
        Vec3d moveDirection = calculateWorldMoveDirection(client);

        // 保留当前的垂直速度（重力、跳跃）
        double yVelocity = player.getVelocity().y;

        // 定义基础移动速度
        double moveSpeed = 0.2; // 与普通行走速度相似

        // 计算新的水平速度向量
        Vec3d newHorizontalVelocity = moveDirection.multiply(moveSpeed);

        // 设置最终速度
        player.setVelocity(newHorizontalVelocity.x, yVelocity, newHorizontalVelocity.z);
    }

    /**
     * 根据展开轴重映射计算世界坐标系中的移动方向
     */
    private Vec3d calculateWorldMoveDirection(MinecraftClient client) {
        // 获取移动输入
        boolean forward = client.options.forwardKey.isPressed();
        boolean backward = client.options.backKey.isPressed();
        boolean left = client.options.leftKey.isPressed();
        boolean right = client.options.rightKey.isPressed();

        // 计算输入向量
        float forwardInput = 0.0f;
        float strafeInput = 0.0f;

        if (forward) forwardInput += 1.0f;
        if (backward) forwardInput -= 1.0f;
        if (right) strafeInput += 1.0f;
        if (left) strafeInput -= 1.0f;

        if (Math.abs(forwardInput) < 0.001f && Math.abs(strafeInput) < 0.001f) {
            return Vec3d.ZERO;
        }

        // 获取展开轴方向
        ViewDirection viewDirection = UnfoldManager.currentDirection;
        Vec3d unfoldAxis = getUnfoldAxisDirection(viewDirection);

        // 计算垂直于展开轴的右方向（水平平移方向）
        // 注意：这里我们只关心水平面上的移动，所以用(0,1,0)做叉乘
        Vec3d rightAxis = unfoldAxis.crossProduct(new Vec3d(0, 1, 0)).normalize();

        // 特殊处理垂直视角
        if (Math.abs(unfoldAxis.y) > 0.9) {
            // 在俯视/仰视模式下，WASD映射为在XZ平面上移动
            // W/S 对应 Z轴, A/D 对应 X轴
            Vec3d forwardAxis = new Vec3d(0, 0, viewDirection == ViewDirection.POS_Y ? 1 : -1);
            rightAxis = new Vec3d(-1, 0, 0); // 固定为X轴负方向
            unfoldAxis = forwardAxis;
        }

        // 组合移动向量：W/S控制“深入/浅出”，A/D控制“左右平移”
        Vec3d result = unfoldAxis.multiply(forwardInput).add(rightAxis.multiply(strafeInput));

        // 返回归一化的方向向量
        return result.lengthSquared() > 0.001 ? result.normalize() : Vec3d.ZERO;
    }

    /**
     * 处理传送逻辑
     */
    private void handleTeleportLogic(ClientPlayerEntity player) {
        if (teleportCooldown > 0) {
            teleportCooldown--;
            return;
        }

        // 飞行模式豁免
        if (player.getAbilities().flying) {
            return;
        }

        // 只有当玩家有移动输入时才尝试传送
        if (player.hasMovementInput()) {
            Vec3d targetPos = UnfoldTeleportLogic.findTeleportTarget(player);
            if (targetPos != null) {
                // 执行传送
                player.setPos(targetPos.x, targetPos.y, targetPos.z);
                // 传送后清除垂直方向的速度，避免落地伤害或二次弹跳
                player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);

                teleportCooldown = 5; // 设置冷却时间

                UnfoldMod.LOGGER.info("Teleport successful. Player moved to {}. Cooldown set.", targetPos);
            }
        }
    }

    /**
     * 根据视角方向计算展开轴方向
     */
    private Vec3d getUnfoldAxisDirection(ViewDirection viewDirection) {
        float yawRad = viewDirection.yaw * (float)(Math.PI / 180.0);
        float pitchRad = viewDirection.pitch * (float)(Math.PI / 180.0);

        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);

        return new Vec3d(x, y, z).normalize();
    }
}