package pama1234.unfold.test.tasks;

import net.minecraft.client.network.ClientPlayerEntity;
import pama1234.unfold.UnfoldMod;
import pama1234.unfold.test.TestRunner;
import pama1234.unfold.test.TestTask;

public class LogTask implements TestTask {
    private final String message;

    public LogTask(String message) {
        this.message = message;
    }

    @Override
    public boolean run(TestRunner runner, ClientPlayerEntity player) {
        return true; // Instantly finished
    }

    @Override
    public void onStart(TestRunner runner, ClientPlayerEntity player) {
        UnfoldMod.LOGGER.info("[TEST LOG] {}", message);
    }

    @Override
    public String getDescription() {
        return "Log: " + message;
    }
}