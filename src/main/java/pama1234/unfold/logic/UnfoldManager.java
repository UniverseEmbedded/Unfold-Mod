package pama1234.unfold.logic;

import pama1234.unfold.util.ViewDirection;

public class UnfoldManager {
    public static boolean unfolding = false;
    public static ViewDirection currentDirection = ViewDirection.ISO_POS_X_POS_Y;
    /**
     * 【新增】用于存储当前所处的展开模式
     */
    public static UnfoldMode currentMode = UnfoldMode.ISOMETRIC;
}