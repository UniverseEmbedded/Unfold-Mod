package pama1234.unfold.test.tasks;

import net.minecraft.client.network.ClientPlayerEntity;
import pama1234.unfold.test.TestRunner;
import pama1234.unfold.test.TestTask;

public class WaitTask implements TestTask {
    private int ticksRemaining;

    public WaitTask(int ticks) {
        this.ticksRemaining = ticks;
    }

    @Override
    public boolean run(TestRunner runner, ClientPlayerEntity player) {
        ticksRemaining--;
        return ticksRemaining <= 0;
    }

    @Override
    public void onStart(TestRunner runner, ClientPlayerEntity player) {
    }

    @Override
    public String getDescription() {
        return "Wait " + ticksRemaining + " ticks";
    }
}