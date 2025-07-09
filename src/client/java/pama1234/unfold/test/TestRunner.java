package pama1234.unfold.test;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import pama1234.unfold.UnfoldMod;
import pama1234.unfold.test.tasks.SetupSceneTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class TestRunner {
    public static final TestRunner INSTANCE = new TestRunner();
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Queue<TestTask> taskQueue = new LinkedList<>();
    private final TestReport report = new TestReport();
    private boolean isRunning = false;
    private TestTask currentTask = null;
    private String currentTestName = "";

    private TestRunner() {}

    public void startTest(String testName) {
        if (isRunning) {
            client.player.sendMessage(Text.literal("A test is already running."), false);
            return;
        }

        this.taskQueue.clear();

        // 环境设置任务
        taskQueue.add(new SetupSceneTask());

        if ("all".equalsIgnoreCase(testName)) {
            // 在这里添加所有测试用例的构造逻辑
            // buildHorizontalJumpUpTest();
            // ...
        } else {
            // 根据名称构建单个测试
            buildTestByName(testName);
        }

        if (taskQueue.isEmpty()) {
            client.player.sendMessage(Text.literal("Test case not found: " + testName), false);
            return;
        }

        this.isRunning = true;
        this.currentTask = null;
        UnfoldMod.LOGGER.info("Starting test sequence for: {}", testName);
    }

    public void tick() {
        if (!isRunning) return;

        ClientPlayerEntity player = client.player;
        if (player == null) {
            stopTest();
            return;
        }

        if (currentTask == null) {
            if (taskQueue.isEmpty()) {
                stopTest();
                return;
            }
            currentTask = taskQueue.poll();
            currentTestName = currentTask.getDescription(); // 使用任务描述作为测试名
            UnfoldMod.LOGGER.info("Running task: {}", currentTestName);
            currentTask.onStart(this, player);
        }

        if (currentTask.run(this, player)) {
            // 任务完成
            report.record(currentTestName, true, "Completed.");
            currentTask = null;
        }
    }

    private void stopTest() {
        isRunning = false;
        currentTask = null;
        report.generate();
        UnfoldMod.LOGGER.info("Test sequence finished.");
        if (client.player != null) {
            client.player.sendMessage(Text.literal("Test sequence finished. See console for report."), false);
        }
    }

    // --- 测试用例构造 ---

    private void buildTestByName(String name) {
        // if ("horizontal_jump_up".equals(name)) buildHorizontalJumpUpTest();
        // ...
    }

    // 示例
    // private void buildHorizontalJumpUpTest() {
    //     taskQueue.add(new SetPlayerStateTask(...));
    //     taskQueue.add(new SimulateJumpTask());
    //     taskQueue.add(new WaitTask(20));
    //     taskQueue.add(new AssertPositionTask(...));
    // }

    public static List<String> getTestNames() {
        return new ArrayList<>(Arrays.asList("horizontal_jump_up", "horizontal_sneak_down"));
    }
}