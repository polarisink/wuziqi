package com.example.wuziqi;

import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/**
 * 棋盘视图：负责画棋盘、画棋子、把鼠标坐标转换成棋盘坐标。
 *
 * <p>它不保存规则状态，真正的棋盘数据在 {@link BoardState} 里。</p>
 */
public final class BoardView extends Parent {

    /** 点击棋盘后的回调接口。 */
    public interface StoneClickHandler {
        void onStoneClicked(int row, int col);
    }

    /** 棋盘在屏幕上的像素宽高。 */
    public static final double PIXEL_SIZE = 680;

    private final Pane stoneLayer = new Pane();
    /** 20 条线之间有 19 个间隔，所以用 SIZE - 1。 */
    private final double cellSize = PIXEL_SIZE / (BoardState.SIZE - 1);

    public BoardView(StoneClickHandler clickHandler) {
        Pane boardLayer = new Pane();
        Rectangle background = new Rectangle(PIXEL_SIZE, PIXEL_SIZE);
        background.setArcWidth(10);
        background.setArcHeight(10);
        background.setFill(Color.web("#E6B85C"));
        background.setStroke(Color.web("#8B5E1D"));
        background.setStrokeWidth(2);
        boardLayer.getChildren().add(background);

        // 先画横线和竖线，边框线稍微粗一点。
        for (int i = 0; i < BoardState.SIZE; i++) {
            double offset = i * cellSize;

            Line horizontal = new Line(0, offset, PIXEL_SIZE, offset);
            horizontal.setStroke(Color.web("#3F2F1A"));
            horizontal.setStrokeWidth(i == 0 || i == BoardState.SIZE - 1 ? 2 : 1);

            Line vertical = new Line(offset, 0, offset, PIXEL_SIZE);
            vertical.setStroke(Color.web("#3F2F1A"));
            vertical.setStrokeWidth(i == 0 || i == BoardState.SIZE - 1 ? 2 : 1);

            boardLayer.getChildren().addAll(horizontal, vertical);
        }

        // 星位只是视觉辅助，不参与胜负规则。
        boardLayer.getChildren().addAll(
                starPoint(4, 4),
                starPoint(4, 15),
                starPoint(10, 10),
                starPoint(15, 4),
                starPoint(15, 15)
        );

        getChildren().addAll(boardLayer, stoneLayer);
        setCursor(Cursor.HAND);
        setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }

            Point2D point = new Point2D(event.getX(), event.getY());
            // round 表示点击离哪个交叉点最近，就落在哪个交叉点。
            int col = (int) Math.round(point.getX() / cellSize);
            int row = (int) Math.round(point.getY() / cellSize);

            if (row >= 0 && row < BoardState.SIZE && col >= 0 && col < BoardState.SIZE) {
                clickHandler.onStoneClicked(row, col);
            }
        });
    }

    /** 在棋盘上画一颗棋子。 */
    public void drawStone(int row, int col, int player) {
        Circle stone = new Circle(col * cellSize, row * cellSize, cellSize * 0.42);
        stone.setFill(player == BoardState.BLACK ? Color.web("#111827") : Color.web("#F9FAFB"));
        stone.setStroke(player == BoardState.BLACK ? Color.web("#030712") : Color.web("#9CA3AF"));
        stone.setStrokeWidth(2);
        stoneLayer.getChildren().add(stone);
    }

    /** 清空所有棋子图形，用于重新开始。 */
    public void clearStones() {
        stoneLayer.getChildren().clear();
    }

    /** 创建棋盘上的星位小黑点。 */
    private Circle starPoint(int row, int col) {
        Circle point = new Circle(col * cellSize, row * cellSize, 4);
        point.setFill(Color.web("#3F2F1A"));
        return point;
    }
}
