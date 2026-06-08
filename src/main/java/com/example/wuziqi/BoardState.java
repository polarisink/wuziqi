package com.example.wuziqi;

import java.util.ArrayList;
import java.util.List;

/**
 * 纯粹的棋盘模型：只保存棋子、判断胜负、为 AI 提供评分。
 *
 * <p>这个类不依赖 JavaFX，也不关心 UI 或网络。这样做的好处是：
 * 规则代码可以单独测试，界面怎么改都不影响胜负判断。</p>
 */
public final class BoardState {

    /** 棋盘是 20 x 20。 */
    public static final int SIZE = 20;
    /** 0 表示空位。 */
    public static final int EMPTY = 0;
    /** 1 表示黑棋。 */
    public static final int BLACK = 1;
    /** 2 表示白棋。 */
    public static final int WHITE = 2;

    /**
     * 五子棋只需要检查 4 个方向：
     * 竖向、横向、主对角线、副对角线。
     *
     * <p>反方向会在 {@link #hasFiveInRow(int, int, int)} 里用负步长一起检查。</p>
     */
    private static final int[][] DIRECTIONS = {
            {1, 0},
            {0, 1},
            {1, 1},
            {1, -1}
    };

    private final int[][] board = new int[SIZE][SIZE];

    /** 读取某个位置的棋子。row 是行，col 是列。 */
    public int get(int row, int col) {
        return board[row][col];
    }

    /** 判断某个位置是否还没有棋子。 */
    public boolean isEmpty(int row, int col) {
        return board[row][col] == EMPTY;
    }

    /** 在指定位置落子。调用方负责先确认这里是空位。 */
    public void place(int row, int col, int player) {
        board[row][col] = player;
    }

    /** 清空某一个格子，主要给 AI 试算落子时回滚用。 */
    public void clearCell(int row, int col) {
        board[row][col] = EMPTY;
    }

    /** 清空整张棋盘，用于重新开始。 */
    public void clear() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                board[row][col] = EMPTY;
            }
        }
    }

    /** 判断坐标是否还在棋盘内，防止数组越界。 */
    public boolean isInside(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    /** 棋盘没有空位时就是平局。 */
    public boolean isFull() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (board[row][col] == EMPTY) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 判断刚落下的一颗棋子是否形成五连。
     *
     * <p>只检查“刚落子位置”经过的 4 条线，而不是扫描全盘，所以效率更高。</p>
     */
    public boolean hasFiveInRow(int row, int col, int player) {
        for (int[] direction : DIRECTIONS) {
            int count = 1
                    + countStones(row, col, direction[0], direction[1], player)
                    + countStones(row, col, -direction[0], -direction[1], player);

            if (count >= 5) {
                return true;
            }
        }

        return false;
    }

    /**
     * 给 AI 找候选落点。
     *
     * <p>五子棋棋盘很大，如果 AI 每次遍历所有空位会比较慢。
     * 这里优先只考虑已有棋子附近 2 格以内的位置，因为远离所有棋子的落点通常没有意义。</p>
     */
    public List<Move> candidateMoves() {
        List<Move> candidates = new ArrayList<>();
        boolean hasStone = false;

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (board[row][col] != EMPTY) {
                    hasStone = true;
                    continue;
                }

                if (hasNeighbor(row, col, 2)) {
                    candidates.add(new Move(row, col));
                }
            }
        }

        if (!hasStone) {
            candidates.add(new Move(SIZE / 2, SIZE / 2));
        } else if (candidates.isEmpty()) {
            for (int row = 0; row < SIZE; row++) {
                for (int col = 0; col < SIZE; col++) {
                    if (board[row][col] == EMPTY) {
                        candidates.add(new Move(row, col));
                    }
                }
            }
        }

        return candidates;
    }

    /**
     * 评估某个玩家如果落在 row/col 能得到多少分。
     *
     * <p>这里会临时把棋子放上去，算完再清掉。这个技巧常用于棋类 AI。</p>
     */
    public int scoreMove(int row, int col, int player) {
        if (board[row][col] != EMPTY) {
            return 0;
        }

        board[row][col] = player;
        int score = 0;

        for (int[] direction : DIRECTIONS) {
            int count = 1
                    + countStones(row, col, direction[0], direction[1], player)
                    + countStones(row, col, -direction[0], -direction[1], player);
            int openEnds = countOpenEnds(row, col, direction[0], direction[1], player);
            score += scoreLine(count, openEnds);
        }

        board[row][col] = EMPTY;
        return score;
    }

    /** 粗略评估整个局面对某个玩家的价值，困难 AI 会用它做一层预判。 */
    public int evaluateFor(int player) {
        int score = 0;

        for (Move move : candidateMoves()) {
            score += scoreMove(move.row(), move.col(), player) / 8;
        }

        return score;
    }

    /** 沿着一个方向数连续同色棋子。 */
    private int countStones(int row, int col, int rowStep, int colStep, int player) {
        int count = 0;
        int nextRow = row + rowStep;
        int nextCol = col + colStep;

        while (isInside(nextRow, nextCol) && board[nextRow][nextCol] == player) {
            count++;
            nextRow += rowStep;
            nextCol += colStep;
        }

        return count;
    }

    /** 判断某个空位附近是否有棋子，用来过滤无意义的远处落点。 */
    private boolean hasNeighbor(int row, int col, int distance) {
        for (int rowOffset = -distance; rowOffset <= distance; rowOffset++) {
            for (int colOffset = -distance; colOffset <= distance; colOffset++) {
                if (rowOffset == 0 && colOffset == 0) {
                    continue;
                }

                int nextRow = row + rowOffset;
                int nextCol = col + colOffset;
                if (isInside(nextRow, nextCol) && board[nextRow][nextCol] != EMPTY) {
                    return true;
                }
            }
        }

        return false;
    }

    /** 统计一条连续棋形两端有几个开放端。开放端越多，棋形威胁越大。 */
    private int countOpenEnds(int row, int col, int rowStep, int colStep, int player) {
        return isOpenEnd(row, col, rowStep, colStep, player)
                + isOpenEnd(row, col, -rowStep, -colStep, player);
    }

    /** 检查某个方向连续棋子结束后，下一格是不是空位。 */
    private int isOpenEnd(int row, int col, int rowStep, int colStep, int player) {
        int nextRow = row + rowStep;
        int nextCol = col + colStep;

        while (isInside(nextRow, nextCol) && board[nextRow][nextCol] == player) {
            nextRow += rowStep;
            nextCol += colStep;
        }

        return isInside(nextRow, nextCol) && board[nextRow][nextCol] == EMPTY ? 1 : 0;
    }

    /**
     * 给棋形打分。
     *
     * <p>分值不是精确数学，只是经验权重：活四比冲四强，活三比单边三强。</p>
     */
    private int scoreLine(int count, int openEnds) {
        if (count >= 5) {
            return 1_000_000;
        }
        if (count == 4 && openEnds == 2) {
            return 100_000;
        }
        if (count == 4 && openEnds == 1) {
            return 15_000;
        }
        if (count == 3 && openEnds == 2) {
            return 8_000;
        }
        if (count == 3 && openEnds == 1) {
            return 1_000;
        }
        if (count == 2 && openEnds == 2) {
            return 450;
        }
        if (count == 2 && openEnds == 1) {
            return 80;
        }
        return Math.max(1, count * 10);
    }
}
