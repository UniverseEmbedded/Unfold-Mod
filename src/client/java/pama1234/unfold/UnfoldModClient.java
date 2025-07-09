package pama1234.unfold;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import pama1234.unfold.logic.UnfoldManager;
import pama1234.unfold.logic.UnfoldTeleportLogic;
import pama1234.unfold.network.TeleportRequestPayload;
import pama1234.unfold.util.ViewDirection;

public class UnfoldModClient implements ClientModInitializer {

    private static KeyBinding toggleKey;
    private static Perspective originalPerspective;
    private static int toggleCooldown = 0;
    private static int teleportCooldown = 0;

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

            // --- 模式切换逻辑 ---
            if (toggleCooldown > 0) toggleCooldown--;
            if (toggleKey.wasPressed() && toggleCooldown == 0) {
                toggleCooldown = 5;
                UnfoldManager.unfolding = !UnfoldManager.unfolding;
                if (client.options != null) {
                    if (UnfoldManager.unfolding) {
                        UnfoldManager.currentDirection = ViewDirection.getClosest(player);
                        player.sendMessage(Text.of("Unfold Mode Activated: " + UnfoldManager.currentDirection.name()), false);
                        originalPerspective = client.options.getPerspective();
                        if (originalPerspective == Perspective.FIRST_PERSON) {
                            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                        }
                        // 【新】进入模式时，停止玩家的任何移动，避免残留速度影响计算
                        player.setVelocity(Vec3d.ZERO);
                        player.velocityModified = true;

                    } else {
                        player.sendMessage(Text.of("Unfold Mode Deactivated"), false);
                        if (originalPerspective != null) {
                            client.options.setPerspective(originalPerspective);
                            originalPerspective = null;
                        }
                    }
                }
            }

            // --- 被动位置修正逻辑 ---
            if (teleportCooldown > 0) teleportCooldown--;

            if (UnfoldManager.unfolding && teleportCooldown == 0) {
                // 【新】在传送前，阻止玩家因为重力等原因下落，让他在屏幕上“静止”
                // 只有WASD的输入速度会影响计算
                if (!player.getAbilities().flying) {
                    player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);
                    player.velocityModified = true;
                }

                Vec3d correctedPos = UnfoldTeleportLogic.findCorrectedPosition();

                if (correctedPos != null) {
                    if (player.getPos().squaredDistanceTo(correctedPos) > 0.01) {
                        ClientPlayNetworking.send(new TeleportRequestPayload(correctedPos));
                        teleportCooldown = 2;
                        // 【新】传送后，再次清空速度，等待下一次WASD输入
                        player.setVelocity(Vec3d.ZERO);
                        player.velocityModified = true;
                    }
                }
            }
        });
    }
}