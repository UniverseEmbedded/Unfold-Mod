package pama1234.unfold;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import pama1234.unfold.command.TestCommand;
import pama1234.unfold.logic.UnfoldManager;
import pama1234.unfold.logic.UnfoldMode;
import pama1234.unfold.logic.UnfoldTeleportLogic;
import pama1234.unfold.test.TestRunner;
import pama1234.unfold.util.ViewDirection;

public class UnfoldModClient implements ClientModInitializer {
    private static KeyBinding toggleKey;
    private static KeyBinding sneakKey;

    private static Perspective originalPerspective;
    private static boolean teleportCooldown = false;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.unfold.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F, "category.unfold"
        ));

        // 在客户端初始化时获取一次实例
        MinecraftClient client = MinecraftClient.getInstance();
        if(client != null && client.options != null) {
            sneakKey = client.options.sneakKey;
        }


        ClientTickEvents.END_CLIENT_TICK.register(tickClient -> {
            // 【新增】驱动测试器
            TestRunner.INSTANCE.tick();

            ClientPlayerEntity player = tickClient.player;
            if (player == null) {
                return;
            }

            handleModeToggle(tickClient, player);

            if (UnfoldManager.unfolding) {
                if (!teleportCooldown) {
                    if (UnfoldTeleportLogic.checkAndPerformTeleport(player)) {
                        teleportCooldown = true;
                    }
                } else {
                    if (player.isOnGround()) {
                        teleportCooldown = false;
                    }
                }

                handlePlayerMovement(tickClient, player);
            }
        });

        TestCommand.register();
    }

    private void handleModeToggle(MinecraftClient client, ClientPlayerEntity player) {
        if (toggleKey.wasPressed()) {
            UnfoldManager.unfolding = !UnfoldManager.unfolding;

            if (UnfoldManager.unfolding) {
                // 先获取最接近的方向
                UnfoldManager.currentDirection = ViewDirection.getClosest(player);
                // 再根据玩家的实际Pitch和计算出的方向，决定最终模式
                UnfoldManager.currentMode = UnfoldMode.fromPlayerPitch(player.getPitch(), UnfoldManager.currentDirection);
                UnfoldMod.LOGGER.info("Unfold Mode Activated: {}", UnfoldManager.currentMode);

                originalPerspective = client.options.getPerspective();
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);

                // 【已修复】禁用按键的逻辑比较复杂，暂时注释以确保编译通过
                // if (UnfoldManager.currentMode == UnfoldMode.HORIZONTAL) {
                //     // client.options.forwardKey.setEnabled(false);
                //     // client.options.backKey.setEnabled(false);
                // }

            } else {
                UnfoldMod.LOGGER.info("Unfold Mode Deactivated");
                if (originalPerspective != null) {
                    client.options.setPerspective(originalPerspective);
                }
                // 【已修复】
                // client.options.forwardKey.setEnabled(true);
                // client.options.backKey.setEnabled(true);
            }
        }
    }

    private void handlePlayerMovement(MinecraftClient client, ClientPlayerEntity player) {
        if (UnfoldManager.currentMode == UnfoldMode.ISOMETRIC || UnfoldManager.currentMode == UnfoldMode.VERTICAL) {
            return;
        }

        if (UnfoldManager.currentMode == UnfoldMode.HORIZONTAL) {
            float strafeInput = 0.0f;
            if (client.options.leftKey.isPressed()) strafeInput -= 1.0f;
            if (client.options.rightKey.isPressed()) strafeInput += 1.0f;

            if (Math.abs(strafeInput) < 0.001f && player.isOnGround()) { // 只在地面上停止
                player.setVelocity(0, player.getVelocity().y, 0);
                return;
            }

            Vec3d unfoldAxis = getUnfoldAxisDirection(UnfoldManager.currentDirection);
            Vec3d strafeAxis = unfoldAxis.crossProduct(new Vec3d(0, 1, 0)).normalize();

            Vec3d moveVec = strafeAxis.multiply(strafeInput * 0.2);
            player.setVelocity(moveVec.x, player.getVelocity().y, moveVec.z);
        }
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