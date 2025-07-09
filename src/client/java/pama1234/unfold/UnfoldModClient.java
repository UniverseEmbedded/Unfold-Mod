package pama1234.unfold;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
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

	@Override
	public void onInitializeClient() {
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.unfold.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F, "category.unfold"
		));
		teleportKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.unfold.teleport", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.unfold"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// 切换Ortho模式
			if (toggleKey.wasPressed()) {
				UnfoldManager.unfolding = !UnfoldManager.unfolding;
				if (client.player != null) {
					if (UnfoldManager.unfolding) {
						UnfoldManager.currentDirection = ViewDirection.getClosest(client.player);
						UnfoldManager.transitionStartTick = System.currentTimeMillis() / 50;
						client.player.sendMessage(Text.of("Unfold Mode Activated: " + UnfoldManager.currentDirection.name()), false);
					} else {
						client.player.sendMessage(Text.of("Unfold Mode Deactivated"), false);
					}
				}
			}

			// 执行传送
			if (UnfoldManager.unfolding && teleportKey.wasPressed()) {
				Vec3d target = UnfoldTeleportLogic.findTeleportLocation();
				if (target != null) {
					if (client.player != null) {
						client.player.sendMessage(Text.of("Teleporting to " + String.format("%.1f, %.1f, %.1f", target.x, target.y, target.z)), false);
					}
					ClientPlayNetworking.send(new TeleportRequestPayload(target));
				} else {
					if (client.player != null) {
						client.player.sendMessage(Text.of("No valid teleport location found!"), false);
					}
				}
			}
		});
	}
}