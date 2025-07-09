package pama1234.unfold.test.tasks;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import pama1234.unfold.test.TestRunner;
import pama1234.unfold.test.TestTask;

public class SimulateMoveTask implements TestTask {
    private final Vec3d velocity;
    private int ticksRemaining;

    public SimulateMoveTask(Vec3d velocity, int durationTicks) {
        this.velocity = velocity;
        this.ticksRemaining = durationTicks;
    }

    @Override
    public boolean run(TestRunner runner, ClientPlayerEntity player) {
        // 持续施加一个速度
        player.setVelocity(velocity.x, player.getVelocity().y, velocity.z);
        ticksRemaining--;
        return ticksRemaining <= 0;
    }

    @Override
    public void onStart(TestRunner runner, ClientPlayerEntity player) {
    }

    @Override
    public String getDescription() {
        return "Simulate Move with velocity " + velocity;
    }
}