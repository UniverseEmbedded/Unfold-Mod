package pama1234.unfold.test.tasks;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import pama1234.unfold.test.TestRunner;
import pama1234.unfold.test.TestTask;

public class SetPlayerPosTask implements TestTask {
    private final Vec3d position;

    public SetPlayerPosTask(Vec3d position) {
        this.position = position;
    }

    @Override
    public boolean run(TestRunner runner, ClientPlayerEntity player) {
        return true;
    }

    @Override
    public void onStart(TestRunner runner, ClientPlayerEntity player) {
        player.setPos(position.x, position.y, position.z);
    }

    @Override
    public String getDescription() {
        return "Set Player Position";
    }
}