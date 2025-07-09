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
    private static KeyBinding teleportKey;
    private static Perspective originalPerspective;

    private static boolean isTransitioning = false;
    private static int transitionCooldown = 0;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.unfold.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F, "category.unfold"
        ));
        teleportKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.unfold.teleport", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.unfold"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = client.player;
            if (player == null) {
                return;
            }

            if (transitionCooldown > 0) {
                transitionCooldown--;
                if (transitionCooldown == 0) {
                    isTransitioning = false;
                }
            }

            if (toggleKey.wasPressed() && !isTransitioning) {
                isTransitioning = true;
                transitionCooldown = 5; // 0.25秒防抖冷却

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

            if (UnfoldManager.unfolding && teleportKey.wasPressed()) {
                Vec3d target = UnfoldTeleportLogic.findTeleportLocation();
                if (target != null) {
                    ClientPlayNetworking.send(new TeleportRequestPayload(target));
                } else {
                    player.sendMessage(Text.of("No valid teleport location found!"), false);
                }
            }
        });
    }
}