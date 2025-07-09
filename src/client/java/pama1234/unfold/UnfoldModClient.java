package pama1234.unfold;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
// import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking; // 暂时不用
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import pama1234.unfold.logic.UnfoldManager;
import pama1234.unfold.logic.UnfoldTeleportLogic;
// import pama1234.unfold.network.TeleportRequestPayload; // 暂时不用
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
                toggleCooldown = 10;
                UnfoldManager.unfolding = !UnfoldManager.unfolding;
                if (client.options != null) {
                    if (UnfoldManager.unfolding) {
                        UnfoldManager.currentDirection = ViewDirection.getClosest(player);
                        player.sendMessage(Text.of("Unfold Mode Activated: " + UnfoldManager.currentDirection.name()), false);
                        originalPerspective = client.options.getPerspective();
                        client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                    } else {
                        player.sendMessage(Text.of("Unfold Mode Deactivated"), false);
                        if (originalPerspective != null) {
                            client.options.setPerspective(originalPerspective);
                            originalPerspective = null;
                        }
                    }
                }
            }

            if (teleportCooldown > 0) teleportCooldown--;

            // --- 修复 #3: 使用 player.hasMovementInput() ---
            if (UnfoldManager.unfolding && teleportCooldown == 0 && player.hasMovementInput()) {
                Vec3d targetPos = UnfoldTeleportLogic.findTeleportTarget(player);

                if (targetPos != null) {
                    player.setPos(targetPos.x, targetPos.y, targetPos.z);
                    teleportCooldown = 5;
                }
            }
        });
    }
}