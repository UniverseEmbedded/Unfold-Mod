package pama1234.unfold.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端对 {@link TeleportRequestPayload} 的显式回执。
 * accepted=false 对应服务端 256 格距离校验拒绝；accepted=true 表示已执行 player.teleport，
 * 客户端随后还会收到权威位置包（PlayerPositionLookS2CPacket）。
 */
public record TeleportResponsePayload(boolean accepted) implements CustomPayload {
    public static final CustomPayload.Id<TeleportResponsePayload> ID = new CustomPayload.Id<>(Identifier.of("unfold", "teleport_response"));
    public static final PacketCodec<RegistryByteBuf, TeleportResponsePayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeBoolean(value.accepted),
            (buf) -> new TeleportResponsePayload(buf.readBoolean())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
