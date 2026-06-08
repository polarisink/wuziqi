package com.example.wuziqi;

import javafx.application.Platform;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 局域网联机服务。
 *
 * <p>这个类做两件事：</p>
 * <ul>
 *     <li>用 UDP 广播发现同一个局域网里的玩家。</li>
 *     <li>用 TCP 发送邀请和落子消息。</li>
 * </ul>
 *
 * <p>它通过 {@link Listener} 回调把结果交给 UI 层，避免网络代码直接操作界面。</p>
 */
public final class LanService {

    /**
     * 网络服务回调接口。
     *
     * <p>JavaFX 的 UI 必须在 JavaFX Application Thread 上更新，
     * 所以 {@link LanService} 内部会用 {@link Platform#runLater(Runnable)} 切回 UI 线程。</p>
     */
    public interface Listener {
        /** 当前本机用户名。 */
        String username();

        /** 有人邀请本机时，询问 UI 是否接受。 */
        boolean confirmInvite(PeerInfo peer);

        /** 在线玩家列表变化时通知 UI 刷新列表。 */
        void onPeersChanged(List<PeerInfo> peers);

        /** 邀请被接受后创建联机对局。 */
        void onInviteAccepted(PeerInfo peer, int localPlayer);

        /** 收到对方落子消息。 */
        void onRemoteMove(int row, int col);

        /** 显示网络状态，比如“搜索中”“邀请未接受”。 */
        void onStatus(String status);
    }

    /** 所有客户端都监听同一个 UDP 端口，用来发现彼此。 */
    private static final int DISCOVERY_PORT = 47655;
    private static final int BLACK = BoardState.BLACK;
    private static final int WHITE = BoardState.WHITE;

    private final Listener listener;
    /** 每次启动生成一个随机 ID，用来区分玩家，也避免收到自己的广播后显示自己。 */
    private final String localPeerId = UUID.randomUUID().toString();
    /**
     * 网络收发不能阻塞 UI 线程，所以全部放进后台执行器。
     *
     * <p>这里使用 JDK 25 的虚拟线程：代码仍然可以写成简单的阻塞 Socket 风格，
     * 但每个连接/任务不会占用一个昂贵的平台线程。对这种小型局域网游戏很合适。</p>
     */
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("wuziqi-lan-", 0).factory()
    );
    private final AtomicBoolean running = new AtomicBoolean();
    private final Map<String, PeerInfo> peers = new HashMap<>();

    private ServerSocket serverSocket;
    private int tcpPort;

    public LanService(Listener listener) {
        this.listener = listener;
    }

    /** 启动 TCP 服务端、UDP 发现监听和定时广播。 */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            // 端口传 0 表示让系统自动分配一个可用 TCP 端口。
            serverSocket = new ServerSocket(0);
            tcpPort = serverSocket.getLocalPort();
        } catch (IOException error) {
            notifyStatus("联机不可用");
            error.printStackTrace(System.err);
            return;
        }

        executor.submit(this::acceptLoop);
        executor.submit(this::discoveryLoop);
        executor.submit(this::broadcastLoop);
        notifyStatus("搜索中");
    }

    /** 停止后台线程和网络端口。 */
    public void stop() {
        running.set(false);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        executor.shutdownNow();
    }

    /**
     * 发送“我在线”的 UDP 广播。
     */
    public void broadcastPresence() {
        if (!running.get() || tcpPort <= 0) {
            return;
        }

        byte[] data = LanMessage.hello(localPeerId, username(), tcpPort).encode().getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.send(new DatagramPacket(data, data.length, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT));
        } catch (IOException error) {
            error.printStackTrace(System.err);
        }
    }

    /** 返回当前缓存的在线玩家列表。 */
    public List<PeerInfo> currentPeers() {
        synchronized (peers) {
            return new ArrayList<>(peers.values());
        }
    }

    /** 邀请某个玩家。邀请方执黑棋，所以接受后 localPlayer 是 BLACK。 */
    public void invite(PeerInfo peer) {
        executor.submit(() -> {
            Optional<LanMessage> response = sendMessage(peer.address(), peer.tcpPort(), LanMessage.invite(localPeerId, username(), tcpPort));
            if (response.isPresent() && response.get().type() == LanMessage.Type.ACCEPT) {
                Platform.runLater(() -> listener.onInviteAccepted(peer, BLACK));
                return;
            }

            notifyStatus("邀请未接受");
        });
    }

    /** 把本机落子发送给对方。 */
    public void sendMove(NetworkSession session, int row, int col) {
        executor.submit(() -> sendMessage(
                session.peerAddress(),
                session.peerPort(),
                LanMessage.move(localPeerId, row, col)
        ));
    }

    /** 定时广播存在感，让别人能在列表里看到本机。 */
    private void broadcastLoop() {
        while (running.get()) {
            broadcastPresence();
            sleep(2500);
        }
    }

    /** 持续监听 UDP 广播，发现其他玩家。 */
    private void discoveryLoop() {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            // 允许同一台机器上多个实例复用发现端口，方便本地调试。
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(DISCOVERY_PORT));
            socket.setSoTimeout(1000);

            byte[] buffer = new byte[1024];
            while (running.get()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    handlePresence(new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8), packet.getAddress());
                } catch (SocketTimeoutException ignored) {
                    prunePeers();
                }
            }
        } catch (SocketException error) {
            notifyStatus("发现端口被占用");
            error.printStackTrace(System.err);
        } catch (IOException error) {
            if (running.get()) {
                error.printStackTrace(System.err);
            }
        }
    }

    /** 解析 UDP 发现消息，并把玩家加入在线列表。 */
    private void handlePresence(String message, InetAddress address) {
        Optional<LanMessage> decoded = LanMessage.decode(message);
        if (decoded.isEmpty()) {
            return;
        }

        LanMessage hello = decoded.get();
        if (hello.type() != LanMessage.Type.HELLO || hello.version() != LanMessage.protocolVersion()) {
            return;
        }

        // 忽略自己发出的广播。
        if (localPeerId.equals(hello.peerId())) {
            return;
        }

        synchronized (peers) {
            peers.put(hello.peerId(), new PeerInfo(hello.peerId(), hello.username(), address, hello.port(), Instant.now()));
        }

        notifyPeersChanged();
    }

    /** 清理长时间没有广播的玩家。 */
    private void prunePeers() {
        boolean changed;
        synchronized (peers) {
            changed = peers.values().removeIf(PeerInfo::isExpired);
        }

        if (changed) {
            notifyPeersChanged();
        }
    }

    /** TCP 服务端主循环：收到连接后交给线程池处理。 */
    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                executor.submit(() -> handleClient(socket));
            } catch (IOException error) {
                if (running.get()) {
                    error.printStackTrace(System.err);
                }
            }
        }
    }

    /** 处理一次 TCP 请求，读入请求文本，写回响应文本。 */
    private void handleClient(Socket socket) {
        try (socket;
             var input = socket.getInputStream();
             var output = socket.getOutputStream()) {
            String message = new String(input.readNBytes(512), StandardCharsets.UTF_8).trim();
            LanMessage response = handleTcpMessage(message, socket.getInetAddress());
            output.write(response.encode().getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException error) {
            error.printStackTrace(System.err);
        }
    }

    /** 根据 TCP 消息类型分发：邀请或落子。 */
    private LanMessage handleTcpMessage(String message, InetAddress address) {
        Optional<LanMessage> decoded = LanMessage.decode(message);
        if (decoded.isEmpty()) {
            return LanMessage.error("bad-message");
        }

        LanMessage lanMessage = decoded.get();
        if (lanMessage.version() != LanMessage.protocolVersion()) {
            return LanMessage.error("bad-version");
        }

        return switch (lanMessage.type()) {
            case INVITE -> handleInvite(lanMessage, address);
            case MOVE -> {
                Platform.runLater(() -> listener.onRemoteMove(lanMessage.row(), lanMessage.col()));
                yield LanMessage.ok();
            }
            default -> LanMessage.error("unknown-message");
        };
    }

    /** 处理别人发来的邀请。被邀请方执白棋。 */
    private LanMessage handleInvite(LanMessage invite, InetAddress address) {
        PeerInfo peer = findPeer(invite.peerId())
                .orElseGet(() -> new PeerInfo(invite.peerId(), invite.username(), address, invite.port(), Instant.now()));

        if (listener.confirmInvite(peer)) {
            Platform.runLater(() -> listener.onInviteAccepted(peer, WHITE));
            return LanMessage.accept(localPeerId, username());
        }

        return LanMessage.decline(localPeerId);
    }

    /** 根据玩家 ID 从缓存里查找玩家。 */
    private Optional<PeerInfo> findPeer(String peerId) {
        synchronized (peers) {
            return Optional.ofNullable(peers.get(peerId));
        }
    }

    /** 发送一条短 TCP 消息，并读取对方响应。 */
    private Optional<LanMessage> sendMessage(InetAddress address, int port, LanMessage message) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), 3000);
            socket.setSoTimeout(30_000);
            socket.getOutputStream().write(message.encode().getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            socket.shutdownOutput();
            return LanMessage.decode(new String(socket.getInputStream().readNBytes(512), StandardCharsets.UTF_8));
        } catch (IOException error) {
            error.printStackTrace(System.err);
            return Optional.empty();
        }
    }

    /** 通知 UI 刷新在线玩家列表。 */
    private void notifyPeersChanged() {
        Platform.runLater(() -> listener.onPeersChanged(currentPeers()));
    }

    /** 通知 UI 更新网络状态。 */
    private void notifyStatus(String status) {
        Platform.runLater(() -> listener.onStatus(status));
    }

    private String username() {
        return listener.username();
    }

    /** 包装 sleep，保留中断标记是后台线程的好习惯。 */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }
}
