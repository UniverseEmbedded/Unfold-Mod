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

import java.util.logging.Logger;

public class UnfoldModClient implements ClientModInitializer {
    public static final Logger LOGGER = Logger.getLogger("Unfold-Debug");

    private static KeyBinding toggleKey;
    private static KeyBinding teleportKey;
    private static Perspective originalPerspective;

    // 【【【 决定性修改：引入状态锁 】】】
    private static boolean isTransitioning = false;
    private static int transitionCooldown = 0; // 冷却计时器

    @Override
    public void onInitializeClient() {
        LOGGER.info("[INIT] Initializing UnfoldModClient...");
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

            // 【状态锁逻辑】处理冷却计时
            if (transitionCooldown > 0) {
                transitionCooldown--;
                if (transitionCooldown == 0) {
                    isTransitioning = false; // 冷却结束，解锁
                    LOGGER.info("[LOCK] Transition lock released.");
                }
            }

            // 【状态锁逻辑】如果正在转换，则不处理按键
            if (toggleKey.wasPressed() && !isTransitioning) {
                LOGGER.info("[KEY-PRESS] Toggle key was pressed! Acquiring lock.");
                isTransitioning = true; // 获得锁
                transitionCooldown = 5; // 设置5个tick的冷却时间，足以让所有状态同步

                UnfoldManager.unfolding = !UnfoldManager.unfolding;
                LOGGER.info("[KEY-PRESS] New unfold state: " + UnfoldManager.unfolding);

                if (client.options != null) {
                    if (UnfoldManager.unfolding) {
                        LOGGER.info("[MODE-ON] Activating unfold mode...");
                        LOGGER.info(String.format("[MODE-ON] Player Y/P before getClosest: %.2f / %.2f", player.getYaw(), player.getPitch()));
                        UnfoldManager.currentDirection = ViewDirection.getClosest(player);
                        LOGGER.info("[MODE-ON] Closest direction found: " + UnfoldManager.currentDirection.name());

                        player.sendMessage(Text.of("Unfold Mode Activated: " + UnfoldManager.currentDirection.name()), false);

                        originalPerspective = client.options.getPerspective();
                        LOGGER.info("[MODE-ON] Original perspective was: " + originalPerspective);
                        if (originalPerspective == Perspective.FIRST_PERSON) {
                            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                            LOGGER.info("[MODE-ON] Switched to THIRD_PERSON_BACK.");
                        }
                    } else {
                        LOGGER.info("[MODE-OFF] Deactivating unfold mode...");
                        player.sendMessage(Text.of("Unfold Mode Deactivated"), false);

                        if (originalPerspective != null) {
                            LOGGER.info("[MODE-OFF] Restoring perspective to: " + originalPerspective);
                            client.options.setPerspective(originalPerspective);
                            originalPerspective = null;
                        } else {
                            LOGGER.warning("[MODE-OFF] originalPerspective was null! Cannot restore.");
                        }
                    }
                } else {
                    LOGGER.warning("[KEY-PRESS] client.options is null!");
                }
            } else if (toggleKey.wasPressed() && isTransitioning) {
                LOGGER.warning("[KEY-PRESS] Key press ignored due to active transition lock.");
            }

            // 传送逻辑保持不变
            if (UnfoldManager.unfolding && teleportKey.wasPressed()) {
                LOGGER.info("[KEY-PRESS] Teleport key was pressed!");
                Vec3d target = UnfoldTeleportLogic.findTeleportLocation();
                if (target != null) {
                    player.sendMessage(Text.of("Teleporting to " + String.format("%.1f, %.1f, %.1f", target.x, target.y, target.z)), false);
                    ClientPlayNetworking.send(new TeleportRequestPayload(target));
                } else {
                    if (player != null) {
                        player.sendMessage(Text.of("No valid teleport location found!"), false);
                    }
                }
            }
        });
        LOGGER.info("[INIT] UnfoldModClient Initialized.");
    }
}