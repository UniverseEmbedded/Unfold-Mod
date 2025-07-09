package pama1234.unfold.logic;

import pama1234.unfold.util.ViewDirection;

public enum UnfoldMode {
    /**
     * 垂直展开模式 (顶视/底视)
     */
    VERTICAL,
    /**
     * 水平展开模式 (FEZ风格)
     */
    HORIZONTAL,
    /**
     * 斜向展开模式 (纪念碑谷风格)
     */
    ISOMETRIC;

    /**
     * 根据当前的视角方向，判断应该进入哪种展开模式。
     */
    public static UnfoldMode from(ViewDirection direction) {
        if (direction == ViewDirection.POS_Y || direction == ViewDirection.NEG_Y) {
            return VERTICAL;
        }
        if (direction.pitch == 0.0f) {
            return HORIZONTAL;
        }
        return ISOMETRIC;
    }
}