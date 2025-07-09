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
import net.minecraft.util.math.BlockPos;
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

            // --- 模式切换逻辑 (保持不变) ---
            if (toggleCooldown > 0) toggleCooldown--;
            if (toggleKey.wasPressed() && toggleCooldown == 0) {
                toggleCooldown = 10;
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

            // --- 全新的、极简的被动深度修正逻辑 ---
            if (UnfoldManager.unfolding) {
                Vec3d correctedPos = UnfoldTeleportLogic.getCorrectedPosition(player);

                if (correctedPos != null) {
                    // 如果计算出的正确位置和当前位置有显著差异，就传送
                    // 使用 0.1 的平方作为阈值，避免因浮点数精度问题反复传送
                    if (player.getPos().squaredDistanceTo(correctedPos) > 0.01) {
                        // 我们不再发送网络包，直接在客户端设置位置！
                        // 这会带来最流畅的体验，因为没有网络延迟。
                        // 服务器端移动将在下一步实现，现在先保证客户端手感。
                        player.setPos(correctedPos.x, correctedPos.y, correctedPos.z);
                    }
                }
            }
        });
    }
}