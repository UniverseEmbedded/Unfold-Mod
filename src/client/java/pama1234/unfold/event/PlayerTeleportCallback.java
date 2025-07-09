package pama1234.unfold.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.util.math.Vec3d;
import pama1234.unfold.logic.UnfoldMode;

public interface PlayerTeleportCallback {
    Event<PlayerTeleportCallback> EVENT = EventFactory.createArrayBacked(PlayerTeleportCallback.class,
            (listeners) -> (mode, from, to) -> {
                for (PlayerTeleportCallback listener : listeners) {
                    listener.onTeleport(mode, from, to);
                }
            });

    void onTeleport(UnfoldMode mode, Vec3d from, Vec3d to);
}