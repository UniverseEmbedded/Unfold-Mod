package pama1234.unfold.test.tasks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import pama1234.unfold.logic.UnfoldManager;
import pama1234.unfold.logic.UnfoldMode;
import pama1234.unfold.test.TestRunner;
import pama1234.unfold.test.TestTask;
import pama1234.unfold.util.ViewDirection;

public class EnterUnfoldModeTask implements TestTask {
    private final ViewDirection direction;

    public EnterUnfoldModeTask(ViewDirection direction) {
        this.direction = direction;
    }

    @Override
    public boolean run(TestRunner runner, ClientPlayerEntity player) {
        return true;
    }

    @Override
    public void onStart(TestRunner runner, ClientPlayerEntity player) {
        UnfoldManager.unfolding = true;
        UnfoldManager.currentDirection = direction;
        UnfoldManager.currentMode = UnfoldMode.from(direction);
        MinecraftClient.getInstance().options.setPerspective(Perspective.THIRD_PERSON_BACK);
        player.setYaw(direction.yaw);
        player.setPitch(direction.pitch);
    }

    @Override
    public String getDescription() {
        return "Enter Unfold Mode";
    }
}