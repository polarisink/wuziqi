package com.example.wuziqi;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.Optional;

import static com.almasb.fxgl.dsl.FXGL.addUINode;
import static com.almasb.fxgl.dsl.FXGL.getPrimaryStage;

/**
 * FXGL 应用入口。
 *
 * <p>这个类主要做“组装”：把棋盘模型、AI、棋盘视图和局域网服务连起来。
 * 具体规则和网络细节已经拆到其他类里，主类只处理用户操作和界面状态。</p>
 */
public class WuziqiApp extends GameApplication implements LanService.Listener {

    private static final int HUMAN_PLAYER = BoardState.BLACK;
    private static final int AI_PLAYER = BoardState.WHITE;
    private static final int WINDOW_WIDTH = 900;
    private static final int WINDOW_HEIGHT = 780;
    private static final double BOARD_X = 40;
    private static final double BOARD_Y = 60;

    private final BoardState board = new BoardState();
    private final AiPlayer aiPlayer = new AiPlayer();

    private BoardView boardView;
    private Label statusLabel;
    private Label networkStatusLabel;
    private TextField usernameField;
    private ListView<PeerInfo> peerListView;
    private LanService lanService;
    private NetworkSession networkSession;
    private GameMode gameMode = GameMode.TWO_PLAYER;
    private int currentPlayer = BoardState.BLACK;
    private boolean gameOver;

    /** 配置游戏窗口和 FXGL 基础行为。 */
    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(WINDOW_WIDTH);
        settings.setHeight(WINDOW_HEIGHT);
        settings.setTitle("FXGL 五子棋 - 20x20");
        settings.setVersion("1.0");
        settings.setMainMenuEnabled(false);
        settings.setGameMenuEnabled(false);
        // FXGL 默认会写相对路径 logs/；打包后安装目录可能不可写，所以关闭它。
        settings.setFileSystemWriteAllowed(false);
    }

    /** 创建棋盘视图，并把棋盘点击事件绑定到 placeStone。 */
    @Override
    protected void initGame() {
        // 只设置操作系统窗口图标；不要使用 settings.setAppIcon(...)，避免 FXGL 把图标资源画进游戏界面。
        // FXGL 的 initGame 可能在后台线程执行，窗口图标属于 JavaFX UI 对象，必须切回 UI 线程。
        Platform.runLater(() -> getPrimaryStage().getIcons().setAll(new Image(WuziqiApp.class.getResourceAsStream("/assets/textures/wuziqi-icon.png"))));
        boardView = new BoardView(this::placeStone);
        addUINode(boardView, BOARD_X, BOARD_Y);
    }

    /** 创建右侧控制区，包括模式、用户名、局域网玩家列表等。 */
    @Override
    protected void initUI() {
        addLabel("五子棋", 760, 70, "#1F2937", FontWeight.BOLD, 32);
        addLabel("20 x 20", 766, 112, "#6B7280", FontWeight.SEMI_BOLD, 18);

        statusLabel = addLabel("", 760, 170, "#111827", FontWeight.BOLD, 20);
        statusLabel.setMinWidth(120);

        addLabel("对局模式", 760, 222, "#374151", FontWeight.SEMI_BOLD, 16);
        ComboBox<GameMode> modeSelector = new ComboBox<>();
        modeSelector.getItems().addAll(GameMode.values());
        modeSelector.setValue(gameMode);
        modeSelector.setPrefWidth(120);
        modeSelector.setOnAction(event -> {
            gameMode = modeSelector.getValue();
            restartGame();
        });
        addUINode(modeSelector, 760, 250);

        Button restartButton = new Button("重新开始");
        restartButton.setFont(Font.font("PingFang SC", FontWeight.SEMI_BOLD, 16));
        restartButton.setPrefSize(108, 38);
        restartButton.setOnAction(event -> restartGame());
        addUINode(restartButton, 760, 310);

        addNetworkControls();
        startLanService();
        updateStatus();
    }

    /** 创建局域网相关控件。 */
    private void addNetworkControls() {
        addLabel("用户名", 760, 370, "#374151", FontWeight.SEMI_BOLD, 14);
        usernameField = new TextField(defaultUsername());
        usernameField.setPrefWidth(120);
        addUINode(usernameField, 760, 394);

        addLabel("局域网玩家", 760, 435, "#374151", FontWeight.SEMI_BOLD, 14);
        peerListView = new ListView<>();
        peerListView.setPrefSize(120, 120);
        addUINode(peerListView, 760, 460);

        Button refreshPeersButton = new Button("刷新");
        refreshPeersButton.setFont(Font.font("PingFang SC", FontWeight.SEMI_BOLD, 13));
        refreshPeersButton.setPrefSize(56, 32);
        refreshPeersButton.setOnAction(event -> refreshPeerList());
        addUINode(refreshPeersButton, 760, 590);

        Button inviteButton = new Button("邀请");
        inviteButton.setFont(Font.font("PingFang SC", FontWeight.SEMI_BOLD, 13));
        inviteButton.setPrefSize(56, 32);
        inviteButton.setOnAction(event -> inviteSelectedPeer());
        addUINode(inviteButton, 824, 590);

        networkStatusLabel = addLabel("未联机", 760, 635, "#4B5563", FontWeight.SEMI_BOLD, 13);
        networkStatusLabel.setMinWidth(120);
    }

    /** 创建统一样式的文字标签，减少重复代码。 */
    private Label addLabel(String text, double x, double y, String color, FontWeight weight, double size) {
        Label label = new Label(text);
        label.setTextFill(Color.web(color));
        label.setFont(Font.font("PingFang SC", weight, size));
        addUINode(label, x, y);
        return label;
    }

    /** 启动局域网服务，服务通过 LanService.Listener 回调本类。 */
    private void startLanService() {
        lanService = new LanService(this);
        lanService.start();
    }

    /**
     * 处理本地玩家点击棋盘。
     *
     * <p>普通双人/人机模式和局域网模式的落子限制不同，所以这里先分流。</p>
     */
    private void placeStone(int row, int col) {
        if (isNetworkGame()) {
            placeNetworkStone(row, col);
            return;
        }

        if (gameOver || !board.isEmpty(row, col) || isWaitingForAi()) {
            return;
        }

        applyStone(row, col, currentPlayer);
        finishTurn(row, col, currentPlayer, playerName(currentPlayer) + "胜利");

        if (isAiTurn()) {
            playAiTurn();
        }
    }

    /** 局域网对战时，只允许轮到自己时落子，并把落子发送给对方。 */
    private void placeNetworkStone(int row, int col) {
        NetworkSession session = networkSession;
        if (session == null || gameOver || !board.isEmpty(row, col) || currentPlayer != session.localPlayer()) {
            return;
        }

        applyStone(row, col, session.localPlayer());
        lanService.sendMove(session, row, col);
        finishTurn(row, col, session.localPlayer(), "你胜利");
    }

    /** 收到对方网络落子后，应用到本地棋盘。 */
    private void applyRemoteMove(int row, int col) {
        NetworkSession session = networkSession;
        if (session == null || gameOver || !board.isInside(row, col) || !board.isEmpty(row, col)) {
            return;
        }

        int remotePlayer = session.localPlayer() == BoardState.BLACK ? BoardState.WHITE : BoardState.BLACK;
        applyStone(row, col, remotePlayer);
        finishTurn(row, col, remotePlayer, session.peerName() + "胜利");
    }

    /** 人机模式下让 AI 选择并执行一步。 */
    private void playAiTurn() {
        Move move = aiPlayer.chooseMove(board, gameMode, AI_PLAYER, HUMAN_PLAYER);
        if (move == null || gameOver || !board.isEmpty(move.row(), move.col())) {
            return;
        }

        applyStone(move.row(), move.col(), AI_PLAYER);
        finishTurn(move.row(), move.col(), AI_PLAYER, "电脑胜利");
    }

    /** 同时更新棋盘模型和棋盘视图，避免二者不同步。 */
    private void applyStone(int row, int col, int player) {
        board.place(row, col, player);
        boardView.drawStone(row, col, player);
    }

    /** 每步结束后统一检查胜负、平局，并切换当前玩家。 */
    private void finishTurn(int row, int col, int player, String winText) {
        if (board.hasFiveInRow(row, col, player)) {
            gameOver = true;
            statusLabel.setText(winText);
            return;
        }

        if (board.isFull()) {
            gameOver = true;
            statusLabel.setText("平局");
            return;
        }

        currentPlayer = currentPlayer == BoardState.BLACK ? BoardState.WHITE : BoardState.BLACK;
        updateStatus();
    }

    /** 清空当前对局。联机状态保留，只重置棋盘和回合。 */
    private void restartGame() {
        board.clear();
        boardView.clearStones();
        currentPlayer = BoardState.BLACK;
        gameOver = false;
        updateStatus();
    }

    /** 根据当前模式显示不同状态文案。 */
    private void updateStatus() {
        if (isNetworkGame()) {
            NetworkSession session = networkSession;
            if (!gameOver) {
                statusLabel.setText(currentPlayer == session.localPlayer()
                        ? "轮到你：" + playerName(currentPlayer)
                        : "等待：" + session.peerName());
            }
            return;
        }

        if (isAiTurn()) {
            statusLabel.setText("电脑思考中");
            return;
        }

        statusLabel.setText("轮到：" + playerName(currentPlayer));
    }

    /** AI 回合：当前是 AI 模式、轮到白棋、且游戏未结束。 */
    private boolean isAiTurn() {
        return gameMode.isAiMode() && currentPlayer == AI_PLAYER && !gameOver;
    }

    /** 防止玩家在 AI 回合继续点击落子。 */
    private boolean isWaitingForAi() {
        return gameMode.isAiMode() && currentPlayer == AI_PLAYER;
    }

    /** 只有已经建立 NetworkSession 时，才算真正进入联机对局。 */
    private boolean isNetworkGame() {
        return gameMode == GameMode.LAN && networkSession != null;
    }

    /** 把棋子常量转换成给玩家看的名字。 */
    private String playerName(int player) {
        return player == BoardState.BLACK ? "黑棋" : "白棋";
    }

    /** 主动广播一次并刷新当前缓存的玩家列表。 */
    private void refreshPeerList() {
        if (lanService != null) {
            lanService.broadcastPresence();
            peerListView.getItems().setAll(lanService.currentPeers());
        }
    }

    /** 邀请列表中选中的局域网玩家。 */
    private void inviteSelectedPeer() {
        PeerInfo peer = peerListView.getSelectionModel().getSelectedItem();
        if (peer == null || lanService == null) {
            onStatus("请选择玩家");
            return;
        }

        onStatus("邀请 " + peer.username());
        lanService.invite(peer);
    }

    /** 接受或发起联机对局后，初始化联机棋盘状态。 */
    private void acceptNetworkGame(PeerInfo peer, int localPlayer) {
        networkSession = new NetworkSession(peer.id(), peer.username(), peer.address(), peer.tcpPort(), localPlayer);
        gameMode = GameMode.LAN;
        board.clear();
        boardView.clearStones();
        currentPlayer = BoardState.BLACK;
        gameOver = false;
        onStatus("联机：" + peer.username());
        updateStatus();
    }

    /** LanService 读取用户名时会调用这个方法。 */
    @Override
    public String username() {
        if (usernameField == null || usernameField.getText().isBlank()) {
            return defaultUsername();
        }

        return usernameField.getText().trim().replace("|", " ");
    }

    /** 网络线程收到邀请后，通过这个方法在 UI 上弹确认框。 */
    @Override
    public boolean confirmInvite(PeerInfo peer) {
        final boolean[] accepted = {false};
        final Object lock = new Object();

        // Alert 必须在 JavaFX UI 线程里打开。
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("局域网对战邀请");
            alert.setHeaderText(peer.username() + " 邀请你对战");
            alert.setContentText("接受后你执白棋，对方先手。");
            Optional<ButtonType> result = alert.showAndWait();
            synchronized (lock) {
                accepted[0] = result.isPresent() && result.get() == ButtonType.OK;
                lock.notifyAll();
            }
        });

        // 网络线程等待用户选择，最多等待 30 秒。
        synchronized (lock) {
            try {
                lock.wait(30_000);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }

        return accepted[0];
    }

    /** 在线玩家列表变化。 */
    @Override
    public void onPeersChanged(List<PeerInfo> peers) {
        peerListView.getItems().setAll(peers);
    }

    /** 对方接受邀请后进入联机对局。 */
    @Override
    public void onInviteAccepted(PeerInfo peer, int localPlayer) {
        acceptNetworkGame(peer, localPlayer);
    }

    /** 收到对方落子。 */
    @Override
    public void onRemoteMove(int row, int col) {
        applyRemoteMove(row, col);
    }

    /** 显示网络状态。 */
    @Override
    public void onStatus(String status) {
        if (networkStatusLabel != null) {
            networkStatusLabel.setText(status);
        }
    }

    /** 应用退出时停止网络后台线程。 */
    @Override
    protected void onExit() {
        if (lanService != null) {
            lanService.stop();
        }
    }

    /** 默认用户名取系统登录名。 */
    private String defaultUsername() {
        String user = System.getProperty("user.name", "玩家");
        return user == null || user.isBlank() ? "玩家" : user;
    }

    /** 程序入口：先安装日志，再交给 FXGL 启动。 */
    public static void main(String[] args) {
        AppDiagnostics.install();
        launch(args);
    }
}
