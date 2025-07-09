package pama1234.unfold.test;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import pama1234.unfold.UnfoldMod;
import pama1234.unfold.test.tasks.*;
import pama1234.unfold.util.ViewDirection;

import java.util.*;

public class TestRunner {
    public static final TestRunner INSTANCE = new TestRunner();
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Queue<TestTask> taskQueue = new LinkedList<>();
    private final TestReport report = new TestReport();

    private boolean isRunning = false;
    private TestTask currentTask = null;
    private String currentTestCaseName = "";
    private Queue<String> testNamesToRun = new LinkedList<>();


    private TestRunner() {}

    public void startTest(String testName) {
        if (isRunning) {
            client.player.sendMessage(Text.literal("A test is already running."), false);
            return;
        }

        if ("all".equalsIgnoreCase(testName)) {
            this.testNamesToRun = new LinkedList<>(getTestNames());
        } else {
            this.testNamesToRun = new LinkedList<>(Collections.singletonList(testName));
        }

        this.isRunning = true;
        this.currentTask = null;
        UnfoldMod.LOGGER.info("====== Starting Test Sequence ======");

        // 启动第一个测试
        buildNextTestCase();
    }

    public void tick() {
        if (!isRunning) return;

        ClientPlayerEntity player = client.player;
        if (player == null) {
            stopTest(true); // 意外终止
            return;
        }

        if (currentTask == null) {
            if (taskQueue.isEmpty()) {
                // 当前大用例已完成
                if (!currentTestCaseName.isEmpty()) {
                    report.record(currentTestCaseName, true, "All steps passed.");
                    currentTestCaseName = "";
                }
                // 准备跑下一个大用例
                buildNextTestCase();
                return; // 等待下一帧开始新任务
            }

            currentTask = taskQueue.poll();
            UnfoldMod.LOGGER.info("Running task: {}", currentTask.getDescription());
            currentTask.onStart(this, player);
        }

        try {
            if (currentTask.run(this, player)) {
                currentTask = null; // 任务完成，下一帧取新任务
            }
        } catch (AssertionError e) {
            report.record(currentTestCaseName, false, "Failed at step [" + currentTask.getDescription() + "]: " + e.getMessage());
            taskQueue.clear(); // 清空剩余任务
            currentTask = null;
        }
    }

    private void buildNextTestCase() {
        if (testNamesToRun.isEmpty()) {
            stopTest(false);
            return;
        }
        String nextTestName = testNamesToRun.poll();
        this.currentTestCaseName = nextTestName;

        // 环境清理总是第一个任务
        taskQueue.add(new SetupSceneTask());

        // 根据名称构建测试任务队列
        buildTestByName(nextTestName);
    }

    private void stopTest(boolean aborted) {
        isRunning = false;
        currentTask = null;
        taskQueue.clear();
        testNamesToRun.clear();

        if (aborted) {
            UnfoldMod.LOGGER.error("====== Test Sequence Aborted! ======");
        } else {
            UnfoldMod.LOGGER.info("====== Test Sequence Finished ======");
        }

        report.generate();

        if (client.player != null) {
            client.player.sendMessage(Text.literal("Test sequence finished. See console/report file for results."), false);
        }
    }

    // --- 测试用例构造 ---

    public static List<String> getTestNames() {
        return new ArrayList<>(Collections.singletonList("horizontal_jump_up"));
    }

    private void buildTestByName(String name) {
        if ("horizontal_jump_up".equals(name)) {
            buildHorizontalJumpUpTest();
        } else {
            UnfoldMod.LOGGER.warn("Test case not found: {}", name);
            report.record(name, false, "Test case not found.");
        }
    }

    private void buildHorizontalJumpUpTest() {
        taskQueue.add(new LogTask("--- Starting Horizontal Jump Up Test (View +X) ---"));

        // 1. 场景搭建
        BlockPos center = new BlockPos(0, 64, 0);
        taskQueue.add(new ClearSceneTask(center, 30));
        taskQueue.add(new BuildPlatformTask(center, Blocks.STONE.getDefaultState(), new Vec3i(5, 1, 5)));
        taskQueue.add(new BuildPlatformTask(center.up(4), Blocks.GOLD_BLOCK.getDefaultState(), new Vec3i(5, 1, 5)));

        // 2. 玩家状态设置
        taskQueue.add(new SetPlayerPosTask(new Vec3d(0.5, 65.0, 0.5)));
        taskQueue.add(new EnterUnfoldModeTask(ViewDirection.POS_X));

        // 3. 动作与等待
        taskQueue.add(new SimulateJumpTask());
        taskQueue.add(new WaitTask(20)); // 等待跳跃和瞬移完成

        // 4. 最终结果断言
        taskQueue.add(new AssertPositionTask(center.up(5), 1.0));
    }
}