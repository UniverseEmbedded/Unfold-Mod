package pama1234.unfold.test.tasks;

import net.minecraft.client.network.ClientPlayerEntity;
import pama1234.unfold.test.TestRunner;
import pama1234.unfold.test.TestTask;

public class SimulateJumpTask implements TestTask {
    @Override
    public boolean run(TestRunner runner, ClientPlayerEntity player) {
        return true;
    }

    @Override
    public void onStart(TestRunner runner, ClientPlayerEntity player) {
        player.jump();
    }

    @Override
    public String getDescription() {
        return "Simulate Jump";
    }
}