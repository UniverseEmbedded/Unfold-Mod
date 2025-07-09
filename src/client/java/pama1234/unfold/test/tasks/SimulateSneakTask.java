package pama1234.unfold.test.tasks;

import net.minecraft.client.network.ClientPlayerEntity;
import pama1234.unfold.test.TestRunner;
import pama1234.unfold.test.TestTask;

public class SimulateSneakTask implements TestTask {
    private int ticksRemaining;
    private boolean wasSneaking;

    public SimulateSneakTask(int durationTicks) {
        this.ticksRemaining = durationTicks;
        this.wasSneaking = false;
    }

    @Override
    public boolean run(TestRunner runner, ClientPlayerEntity player) {
        // 强制玩家处于潜行状态
        player.setSneaking(true);

        ticksRemaining--;
        return ticksRemaining <= 0;
    }

    @Override
    public void onStart(TestRunner runner, ClientPlayerEntity player) {
        // 记录玩家原始的潜行状态
        wasSneaking = player.isSneaking();
    }

    @Override
    public String getDescription() {
        return "Simulate Sneak for " + ticksRemaining + " ticks";
    }
}