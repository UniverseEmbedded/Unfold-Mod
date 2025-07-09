package pama1234.unfold;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
import net.minecraft.client.network.ClientPlayerEntity;

public class UnfoldModClient implements ClientModInitializer {

    private static KeyBinding toggleKey;
    // G键已移除: private static KeyBinding teleportKey;
    private static Perspective originalPerspective;

    private static int toggleCooldown = 0;
    private static int teleportCooldown = 0; // 新增一个传送冷却，防止因位置修正抖动导致反复传送

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.unfold.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F, "category.unfold"
        ));
        // teleportKey 的注册已移除

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = client.player;
            if (player == null) {
                return;
            }

            // --- 模式切换逻辑 ---
            if (toggleCooldown > 0) {
                toggleCooldown--;
            }
            if (toggleKey.wasPressed() && toggleCooldown == 0) {
                toggleCooldown = 5; // 0.25秒防抖冷却

                UnfoldManager.unfolding = !UnfoldManager.unfolding;

                if (client.options != null) {
                    if (UnfoldManager.unfolding) {
                        UnfoldManager.currentDirection = ViewDirection.getClosest(player);
                        player.sendMessage(Text.of("Unfold Mode Activated: " + UnfoldManager.currentDirection.name()), false);

                        originalPerspective = client.options.getPerspective();
                        if (originalPerspective == Perspective.FIRST_PERSON) {
                            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                        }
                    } else {
                        player.sendMessage(Text.of("Unfold Mode Deactivated"), false);

                        if (originalPerspective != null) {
                            client.options.setPerspective(originalPerspective);
                            originalPerspective = null;
                        }
                    }
                }
            }

            // --- 全新的被动位置修正逻辑 ---
            if (teleportCooldown > 0) {
                teleportCooldown--;
            }

            if (UnfoldManager.unfolding && teleportCooldown == 0) {
                Vec3d correctedPos = UnfoldTeleportLogic.findCorrectedPosition();

                if (correctedPos != null) {
                    // 检查计算出的正确位置是否与当前位置足够不同，以避免不必要的传送
                    // 使用 squaredDistanceTo 比 distanceTo 性能更好
                    if (player.getPos().squaredDistanceTo(correctedPos) > 0.01) { // 阈值可以调整
                        ClientPlayNetworking.send(new TeleportRequestPayload(correctedPos));
                        // 传送后设置一个短暂的冷却，防止因网络延迟或计算抖动导致反复传送
                        teleportCooldown = 2; // 2 ticks (0.1秒)
                    }
                }
            }
        });
    }
}