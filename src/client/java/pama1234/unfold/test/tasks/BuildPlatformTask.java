package pama1234.unfold.test.tasks;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import pama1234.unfold.test.TestRunner;
import pama1234.unfold.test.TestTask;

public class BuildPlatformTask implements TestTask {
    private final BlockPos from;
    private final BlockPos to;
    private final BlockState blockState;

    public BuildPlatformTask(BlockPos center, BlockState state, Vec3i size) {
        this.from = center.add(-size.getX()/2, -size.getY()/2, -size.getZ()/2);
        this.to = center.add(size.getX()/2, size.getY()/2, size.getZ()/2);
        this.blockState = state;
    }

    @Override
    public boolean run(TestRunner runner, ClientPlayerEntity player) {
        return true;
    }

    @Override
    public void onStart(TestRunner runner, ClientPlayerEntity player) {
        String blockId = Registries.BLOCK.getId(blockState.getBlock()).toString();
        String command = String.format("/fill %d %d %d %d %d %d %s",
                from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(), blockId);
        MinecraftClient.getInstance().getNetworkHandler().sendChatCommand(command.substring(1));
    }

    @Override
    public String getDescription() {
        return "Build Platform";
    }
}