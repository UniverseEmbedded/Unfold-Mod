package pama1234.unfold;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import pama1234.unfold.network.TeleportRequestPayload;

public class UnfoldMod implements ModInitializer {

	@Override
	public void onInitialize() {
		ServerPlayNetworking.registerGlobalReceiver(TeleportRequestPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			Vec3d targetPos = payload.targetPos();

			// 服务端进行安全校验（例如，检查传送距离是否过远）
			if (player.getPos().distanceTo(targetPos) > 100.0) { // 防止作弊
				return;
			}

			// 在服务器的主线程中执行传送
			player.getServer().execute(() -> {
				player.teleport(targetPos.x, targetPos.y, targetPos.z, true);
			});
		});
	}
}