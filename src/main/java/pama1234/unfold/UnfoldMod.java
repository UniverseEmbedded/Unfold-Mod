package pama1234.unfold;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import pama1234.unfold.network.TeleportRequestPayload;

public class UnfoldMod implements ModInitializer {

	@Override
	public void onInitialize() {
		// **新增：在这里注册Payload类型**
		// 这会告诉服务端，存在一种ID为 "unfold:teleport_request" 的数据包，
		// 并且它应该使用 TeleportRequestPayload.CODEC 来解码。
		PayloadTypeRegistry.playC2S().register(TeleportRequestPayload.ID, TeleportRequestPayload.CODEC);

		// 现在可以安全地注册接收器了
		ServerPlayNetworking.registerGlobalReceiver(TeleportRequestPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			Vec3d targetPos = payload.targetPos();

			// 服务端进行安全校验（例如，检查传送距离是否过远）
			if (player.getPos().distanceTo(targetPos) > 100.0) { // 防止作弊
				return;
			}

			// 在服务器的主线程中执行传送
			player.getServer().execute(() -> {
				player.teleport(targetPos.x, targetPos.y, targetPos.z, false);
			});
		});
	}
}