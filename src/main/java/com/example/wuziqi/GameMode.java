package com.example.wuziqi;

/** 游戏模式。ComboBox 会直接调用 toString() 显示中文名称。 */
public enum GameMode {
    TWO_PLAYER("双人对战"),
    LAN("局域网对战"),
    EASY("人机：简单"),
    MEDIUM("人机：中等"),
    HARD("人机：困难");

    private final String label;

    GameMode(String label) {
        this.label = label;
    }

    /** 判断当前模式是否需要 AI 接管白棋。 */
    public boolean isAiMode() {
        return this == EASY || this == MEDIUM || this == HARD;
    }

    @Override
    public String toString() {
        return label;
    }
}
