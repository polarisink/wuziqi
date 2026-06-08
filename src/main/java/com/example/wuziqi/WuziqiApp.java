package com.example.wuziqi;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static com.almasb.fxgl.dsl.FXGL.addUINode;

public class WuziqiApp extends GameApplication {

    private static final int BOARD_SIZE = 20;
    private static final int EMPTY = 0;
    private static final int BLACK = 1;
    private static final int WHITE = 2;

    private static final int WINDOW_WIDTH = 900;
    private static final int WINDOW_HEIGHT = 780;
    private static final double BOARD_PIXEL_SIZE = 680;
    private static final double BOARD_X = 40;
    private static final double BOARD_Y = 60;

    private final int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private BoardView boardView;
    private Label statusLabel;
    private int currentPlayer = BLACK;
    private boolean gameOver;

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(WINDOW_WIDTH);
        settings.setHeight(WINDOW_HEIGHT);
        settings.setTitle("FXGL 五子棋 - 20x20");
        settings.setVersion("1.0");
        settings.setMainMenuEnabled(false);
        settings.setGameMenuEnabled(false);
    }

    @Override
    protected void initGame() {
        boardView = new BoardView();
        addUINode(boardView, BOARD_X, BOARD_Y);
    }

    @Override
    protected void initUI() {
        Label title = new Label("五子棋");
        title.setTextFill(Color.web("#1F2937"));
        title.setFont(Font.font("PingFang SC", FontWeight.BOLD, 32));
        addUINode(title, 760, 70);

        Label boardInfo = new Label("20 x 20");
        boardInfo.setTextFill(Color.web("#6B7280"));
        boardInfo.setFont(Font.font("PingFang SC", FontWeight.SEMI_BOLD, 18));
        addUINode(boardInfo, 766, 112);

        statusLabel = new Label();
        statusLabel.setTextFill(Color.web("#111827"));
        statusLabel.setFont(Font.font("PingFang SC", FontWeight.BOLD, 20));
        statusLabel.setMinWidth(120);
        addUINode(statusLabel, 760, 170);

        Button restartButton = new Button("重新开始");
        restartButton.setFont(Font.font("PingFang SC", FontWeight.SEMI_BOLD, 16));
        restartButton.setPrefSize(108, 38);
        restartButton.setOnAction(event -> restartGame());
        addUINode(restartButton, 760, 230);

        updateStatus();
    }

    private void placeStone(int row, int col) {
        if (gameOver || board[row][col] != EMPTY) {
            return;
        }

        board[row][col] = currentPlayer;
        boardView.drawStone(row, col, currentPlayer);

        if (hasFiveInRow(row, col, currentPlayer)) {
            gameOver = true;
            statusLabel.setText(playerName(currentPlayer) + "胜利");
            return;
        }

        if (isBoardFull()) {
            gameOver = true;
            statusLabel.setText("平局");
            return;
        }

        currentPlayer = currentPlayer == BLACK ? WHITE : BLACK;
        updateStatus();
    }

    private boolean hasFiveInRow(int row, int col, int player) {
        int[][] directions = {
                {1, 0},
                {0, 1},
                {1, 1},
                {1, -1}
        };

        for (int[] direction : directions) {
            int count = 1
                    + countStones(row, col, direction[0], direction[1], player)
                    + countStones(row, col, -direction[0], -direction[1], player);

            if (count >= 5) {
                return true;
            }
        }

        return false;
    }

    private int countStones(int row, int col, int rowStep, int colStep, int player) {
        int count = 0;
        int nextRow = row + rowStep;
        int nextCol = col + colStep;

        while (isInsideBoard(nextRow, nextCol) && board[nextRow][nextCol] == player) {
            count++;
            nextRow += rowStep;
            nextCol += colStep;
        }

        return count;
    }

    private boolean isInsideBoard(int row, int col) {
        return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
    }

    private boolean isBoardFull() {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (board[row][col] == EMPTY) {
                    return false;
                }
            }
        }

        return true;
    }

    private void restartGame() {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                board[row][col] = EMPTY;
            }
        }

        currentPlayer = BLACK;
        gameOver = false;
        boardView.clearStones();
        updateStatus();
    }

    private void updateStatus() {
        statusLabel.setText("轮到：" + playerName(currentPlayer));
    }

    private String playerName(int player) {
        return player == BLACK ? "黑棋" : "白棋";
    }

    public static void main(String[] args) {
        installDiagnostics();
        launch(args);
    }

    private static void installDiagnostics() {
        try {
            Path logDir = resolveLogDir();
            Files.createDirectories(logDir);

            PrintStream logStream = new PrintStream(
                    Files.newOutputStream(logDir.resolve("wuziqi.log")),
                    true,
                    StandardCharsets.UTF_8
            );

            System.setOut(new PrintStream(new TeeOutputStream(System.out, logStream), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new TeeOutputStream(System.err, logStream), true, StandardCharsets.UTF_8));
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                System.err.println("Uncaught exception on thread: " + thread.getName());
                throwable.printStackTrace(System.err);
            });

            System.out.println("Wuziqi starting at " + LocalDateTime.now());
            System.out.println("Java: " + System.getProperty("java.version"));
            System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
            System.out.println("Log file: " + logDir.resolve("wuziqi.log"));
        } catch (IOException | RuntimeException error) {
            error.printStackTrace(System.err);
        }
    }

    private static Path resolveLogDir() {
        String osName = System.getProperty("os.name", "").toLowerCase();

        if (osName.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "Wuziqi", "logs");
            }
        }

        if (osName.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Logs", "Wuziqi");
        }

        return Path.of(System.getProperty("user.home"), ".wuziqi", "logs");
    }

    private static final class TeeOutputStream extends OutputStream {

        private final OutputStream first;
        private final OutputStream second;

        private TeeOutputStream(OutputStream first, OutputStream second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void write(int value) throws IOException {
            first.write(value);
            second.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            first.write(bytes, offset, length);
            second.write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            first.flush();
            second.flush();
        }

        @Override
        public void close() throws IOException {
            first.close();
            second.close();
        }
    }

    private final class BoardView extends Parent {

        private final Pane stoneLayer = new Pane();
        private final double cellSize = BOARD_PIXEL_SIZE / (BOARD_SIZE - 1);

        private BoardView() {
            Pane boardLayer = new Pane();
            Rectangle background = new Rectangle(BOARD_PIXEL_SIZE, BOARD_PIXEL_SIZE);
            background.setArcWidth(10);
            background.setArcHeight(10);
            background.setFill(Color.web("#E6B85C"));
            background.setStroke(Color.web("#8B5E1D"));
            background.setStrokeWidth(2);
            boardLayer.getChildren().add(background);

            for (int i = 0; i < BOARD_SIZE; i++) {
                double offset = i * cellSize;

                Line horizontal = new Line(0, offset, BOARD_PIXEL_SIZE, offset);
                horizontal.setStroke(Color.web("#3F2F1A"));
                horizontal.setStrokeWidth(i == 0 || i == BOARD_SIZE - 1 ? 2 : 1);

                Line vertical = new Line(offset, 0, offset, BOARD_PIXEL_SIZE);
                vertical.setStroke(Color.web("#3F2F1A"));
                vertical.setStrokeWidth(i == 0 || i == BOARD_SIZE - 1 ? 2 : 1);

                boardLayer.getChildren().addAll(horizontal, vertical);
            }

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
                int col = (int) Math.round(point.getX() / cellSize);
                int row = (int) Math.round(point.getY() / cellSize);

                if (isInsideBoard(row, col)) {
                    placeStone(row, col);
                }
            });
        }

        private Circle starPoint(int row, int col) {
            Circle point = new Circle(col * cellSize, row * cellSize, 4);
            point.setFill(Color.web("#3F2F1A"));
            return point;
        }

        private void drawStone(int row, int col, int player) {
            Circle stone = new Circle(col * cellSize, row * cellSize, cellSize * 0.42);
            stone.setFill(player == BLACK ? Color.web("#111827") : Color.web("#F9FAFB"));
            stone.setStroke(player == BLACK ? Color.web("#030712") : Color.web("#9CA3AF"));
            stone.setStrokeWidth(2);
            stoneLayer.getChildren().add(stone);
        }

        private void clearStones() {
            stoneLayer.getChildren().clear();
        }
    }
}
