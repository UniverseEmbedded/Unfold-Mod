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
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import pama1234.unfold.logic.UnfoldManager;
import pama1234.unfold.logic.UnfoldMode;
import pama1234.unfold.logic.UnfoldTeleportLogic;
import pama1234.unfold.util.ViewDirection;

public class UnfoldModClient implements ClientModInitializer {
    private static KeyBinding toggleKey;
    // 移除 sneakKey，因为它没有被使用
    // private static KeyBinding sneakKey;

    private static Perspective originalPerspective;
    private static boolean teleportCooldown = false;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.unfold.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.unfold"
        ));

        // 此处获取 sneakKey 的逻辑可以移除，因为它未在后续代码中使用
        // MinecraftClient client = MinecraftClient.getInstance();
        // if(client != null && client.options != null) {
        //     sneakKey = client.options.sneakKey;
        // }

        ClientTickEvents.END_CLIENT_TICK.register(tickClient -> {

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

                // 【注意】原有的 handlePlayerMovement 似乎只处理水平模式，此处保持不变
                handlePlayerMovement(tickClient, player);
            }
        });

    }


    /**
     * 处理模式切换的逻辑。
     * 修正：在进入展开模式时，在聊天栏打印纯中文的模式和视角方向。
     *
     * @param client 客户端实例。
     * @param player 客户端玩家实体。
     */
    private void handleModeToggle(MinecraftClient client, ClientPlayerEntity player) {
        if (toggleKey.wasPressed()) {
            UnfoldManager.unfolding = !UnfoldManager.unfolding;

            if (UnfoldManager.unfolding) {
                // 进入展开模式的逻辑
                UnfoldManager.currentDirection = ViewDirection.getClosest(player);
                // 假设您使用了我之前建议的修改，优先判断垂直模式
                UnfoldManager.currentMode = UnfoldMode.fromPlayerPitch(player.getPitch(), UnfoldManager.currentDirection);

                // 【修改开始】
                // 现在完全使用中文名称
                String modeName;
                switch (UnfoldManager.currentMode) {
                    case VERTICAL:
                        modeName = "垂直模式";
                        break;
                    case HORIZONTAL:
                        modeName = "水平模式";
                        break;
                    case ISOMETRIC:
                        modeName = "斜角模式";
                        break;
                    default:
                        modeName = "未知模式";
                        break;
                }

                // 从 ViewDirection 枚举中获取中文名
                String directionName = UnfoldManager.currentDirection.chineseName;

                // 构建完整的消息文本
                String message = String.format("已进入: %s (视角: %s)", modeName, directionName);

                // 发送消息到玩家的聊天栏
                player.sendMessage(Text.literal(message), false);
                // 【修改结束】

                UnfoldMod.LOGGER.info("Unfold Mode Activated: {} (Direction: {})", UnfoldManager.currentMode, UnfoldManager.currentDirection.name());

                originalPerspective = client.options.getPerspective();
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);

            } else {
                // 退出展开模式的逻辑
                UnfoldMod.LOGGER.info("Unfold Mode Deactivated");
                if (originalPerspective != null) {
                    client.options.setPerspective(originalPerspective);
                }
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

            if (Math.abs(strafeInput) < 0.001f && player.isOnGround()) {
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
        float yawRad = viewDirection.yaw * (float) (Math.PI / 180.0);
        float pitchRad = viewDirection.pitch * (float) (Math.PI / 180.0);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        return new Vec3d(x, y, z).normalize();
    }
}