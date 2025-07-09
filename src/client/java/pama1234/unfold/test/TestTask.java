package pama1234.unfold.test;

import net.minecraft.client.network.ClientPlayerEntity;

/**
 * 代表一个可执行的测试步骤
 */
public interface TestTask {
    /**
     * 执行任务。
     * @param runner The TestRunner instance.
     * @param player The player entity.
     * @return true if the task is finished, false if it needs more ticks.
     */
    boolean run(TestRunner runner, ClientPlayerEntity player);

    /**
     * 任务开始时调用，用于初始化。
     */
    void onStart(TestRunner runner, ClientPlayerEntity player);

    /**
     * 任务的描述，用于报告。
     */
    String getDescription();
}