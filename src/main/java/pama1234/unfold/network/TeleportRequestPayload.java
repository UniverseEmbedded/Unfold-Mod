package pama1234.unfold.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public record TeleportRequestPayload(Vec3d targetPos) implements CustomPayload {
    public static final CustomPayload.Id<TeleportRequestPayload> ID = new CustomPayload.Id<>(Identifier.of("unfold", "teleport_request"));
    public static final PacketCodec<RegistryByteBuf, TeleportRequestPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeDouble(value.targetPos.x);
                buf.writeDouble(value.targetPos.y);
                buf.writeDouble(value.targetPos.z);
            },
            (buf) -> new TeleportRequestPayload(new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}