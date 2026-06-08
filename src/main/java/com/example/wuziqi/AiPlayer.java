package com.example.wuziqi;

import java.util.List;
import java.util.Random;

/**
 * 人机玩家。
 *
 * <p>AI 并不直接修改 UI，只根据 {@link BoardState} 返回一个推荐落子。
 * 真正落子仍由 {@link WuziqiApp} 统一处理。</p>
 */
public final class AiPlayer {

    private final Random random = new Random();

    /** 根据当前模式选择不同强度的算法。 */
    public Move chooseMove(BoardState board, GameMode mode, int aiPlayer, int humanPlayer) {
        return switch (mode) {
            case EASY -> chooseEasyMove(board);
            case MEDIUM -> chooseBestMove(board, aiPlayer, humanPlayer, false);
            case HARD -> chooseBestMove(board, aiPlayer, humanPlayer, true);
            case TWO_PLAYER, LAN -> null;
        };
    }

    /** 简单难度：在候选点里随机挑一个。 */
    private Move chooseEasyMove(BoardState board) {
        List<Move> candidates = board.candidateMoves();
        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * 中等/困难难度：选择评分最高的落点。
     *
     * <p>中等只看当前这一步；困难会额外模拟一次自己落子后的局面，
     * 并扣掉对手下一步可能得到的最好分数。</p>
     */
    private Move chooseBestMove(BoardState board, int aiPlayer, int humanPlayer, boolean useLookahead) {
        List<Move> candidates = board.candidateMoves();
        Move bestMove = null;
        int bestScore = Integer.MIN_VALUE;

        for (Move move : candidates) {
            int score = board.scoreMove(move.row(), move.col(), aiPlayer)
                    + board.scoreMove(move.row(), move.col(), humanPlayer) * 9 / 10;

            if (useLookahead) {
                board.place(move.row(), move.col(), aiPlayer);
                score += board.evaluateFor(aiPlayer) - board.evaluateFor(humanPlayer);
                score -= bestReplyScore(board, humanPlayer) * 7 / 10;
                board.clearCell(move.row(), move.col());
            }

            score += random.nextInt(useLookahead ? 3 : 12);

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        return bestMove;
    }

    /** 估计对手下一步最强反击，用于困难难度的简单防守。 */
    private int bestReplyScore(BoardState board, int player) {
        int bestScore = 0;

        for (Move move : board.candidateMoves()) {
            bestScore = Math.max(bestScore, board.scoreMove(move.row(), move.col(), player));
        }

        return bestScore;
    }
}
