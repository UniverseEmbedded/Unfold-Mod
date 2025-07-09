package pama1234.unfold;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
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
                handleUnfoldMode(player);
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
    private void handleUnfoldMode(ClientPlayerEntity player) {
        // 强制统一视觉与操控：锁定玩家朝向到移动方向
        enforcePlayerOrientationLock(player);

        // 处理传送逻辑
        handleTeleportLogic(player);
    }

    /**
     * 强制统一视觉与操控：玩家的角色模型必须被强制转向当前移动方向
     */
    private void enforcePlayerOrientationLock(ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();

        // 获取当前的移动输入
        boolean forward = client.options.forwardKey.isPressed();
        boolean backward = client.options.backKey.isPressed();
        boolean left = client.options.leftKey.isPressed();
        boolean right = client.options.rightKey.isPressed();

        if (!forward && !backward && !left && !right) {
            return; // 没有移动输入，不需要调整朝向
        }

        // 计算基于展开轴重映射后的世界移动方向
        Vec3d worldMoveDirection = calculateWorldMoveDirection(forward, backward, left, right);

        if (worldMoveDirection.lengthSquared() > 0.001) {
            // 计算玩家应该面向的角度
            float targetYaw = (float)(Math.atan2(-worldMoveDirection.x, worldMoveDirection.z) * 180.0 / Math.PI);

            // 平滑插值到目标角度，避免突然跳转
            float currentYaw = player.getYaw();
            float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
            float newYaw = currentYaw + yawDiff * 0.3f; // 30%的插值率

            player.setYaw(newYaw);
        }
    }

    /**
     * 根据展开轴重映射计算世界坐标系中的移动方向
     */
    private Vec3d calculateWorldMoveDirection(boolean forward, boolean backward, boolean left, boolean right) {
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

        // 计算垂直于展开轴的右方向
        Vec3d rightAxis = unfoldAxis.crossProduct(new Vec3d(0, 1, 0)).normalize();

        // 特殊处理垂直视角
        if (Math.abs(unfoldAxis.y) > 0.9) {
            Vec3d forwardAxis = new Vec3d(0, 0, viewDirection == ViewDirection.POS_Y ? 1 : -1);
            rightAxis = forwardAxis.crossProduct(new Vec3d(0, 1, 0)).normalize();
            unfoldAxis = forwardAxis;
        }

        // 组合移动向量
        Vec3d result = unfoldAxis.multiply(forwardInput).add(rightAxis.multiply(strafeInput));

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

        // 飞行模式豁免：如果玩家在创造模式飞行，完全跳过传送逻辑
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

                teleportCooldown = 5; // 设置冷却时间，防止连续传送

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