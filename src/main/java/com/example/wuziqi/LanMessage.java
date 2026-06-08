package com.example.wuziqi;

import java.util.Optional;

/**
 * 局域网协议消息对象。
 *
 * <p>当前协议仍然是简单文本：字段之间用 | 分隔。
 * 把格式集中在这个类里，可以避免 LanService 到处手写字符串拼接和 split。</p>
 */
public record LanMessage(
        Type type,
        int version,
        String peerId,
        String username,
        int port,
        int row,
        int col,
        String error
) {

    public enum Type {
        HELLO,
        INVITE,
        ACCEPT,
        DECLINE,
        MOVE,
        OK,
        ERROR
    }

    private static final String PREFIX = "WUZIQI";
    private static final int VERSION = 1;
    private static final int NO_NUMBER = -1;

    public static int protocolVersion() {
        return VERSION;
    }

    public static LanMessage hello(String peerId, String username, int port) {
        return new LanMessage(Type.HELLO, VERSION, peerId, clean(username), port, NO_NUMBER, NO_NUMBER, "");
    }

    public static LanMessage invite(String peerId, String username, int port) {
        return new LanMessage(Type.INVITE, VERSION, peerId, clean(username), port, NO_NUMBER, NO_NUMBER, "");
    }

    public static LanMessage accept(String peerId, String username) {
        return new LanMessage(Type.ACCEPT, VERSION, peerId, clean(username), NO_NUMBER, NO_NUMBER, NO_NUMBER, "");
    }

    public static LanMessage decline(String peerId) {
        return new LanMessage(Type.DECLINE, VERSION, peerId, "", NO_NUMBER, NO_NUMBER, NO_NUMBER, "");
    }

    public static LanMessage move(String peerId, int row, int col) {
        return new LanMessage(Type.MOVE, VERSION, peerId, "", NO_NUMBER, row, col, "");
    }

    public static LanMessage ok() {
        return new LanMessage(Type.OK, VERSION, "", "", NO_NUMBER, NO_NUMBER, NO_NUMBER, "");
    }

    public static LanMessage error(String error) {
        return new LanMessage(Type.ERROR, VERSION, "", "", NO_NUMBER, NO_NUMBER, NO_NUMBER, clean(error));
    }

    /**
     * 把对象序列化成网络上传输的文本。
     *
     * <p>HELLO 格式就是你提到的：
     * WUZIQI|HELLO|版本|本机ID|用户名|TCP端口。</p>
     */
    public String encode() {
        return switch (type) {
            case HELLO -> String.join("|", PREFIX, "HELLO", String.valueOf(version), peerId, clean(username), String.valueOf(port));
            case INVITE -> String.join("|", PREFIX, "INVITE", String.valueOf(version), peerId, clean(username), String.valueOf(port));
            case ACCEPT -> String.join("|", PREFIX, "ACCEPT", String.valueOf(version), peerId, clean(username));
            case DECLINE -> String.join("|", PREFIX, "DECLINE", String.valueOf(version), peerId);
            case MOVE -> String.join("|", PREFIX, "MOVE", String.valueOf(version), peerId, String.valueOf(row), String.valueOf(col));
            case OK -> String.join("|", PREFIX, "OK", String.valueOf(version));
            case ERROR -> String.join("|", PREFIX, "ERROR", String.valueOf(version), clean(error));
        };
    }

    /**
     * 把网络文本解析成对象。解析失败返回 Optional.empty()，
     * 这样调用方可以直接忽略无关广播或坏消息。
     */
    public static Optional<LanMessage> decode(String raw) {
        String[] parts = raw.trim().split("\\|", -1);
        if (parts.length < 3 || !PREFIX.equals(parts[0])) {
            return Optional.empty();
        }

        Type type;
        int version;
        try {
            type = Type.valueOf(parts[1]);
            version = Integer.parseInt(parts[2]);
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }

        try {
            return switch (type) {
                case HELLO, INVITE -> parsePeerWithPort(type, version, parts);
                case ACCEPT -> parseAccept(version, parts);
                case DECLINE -> parseDecline(version, parts);
                case MOVE -> parseMove(version, parts);
                case OK -> Optional.of(ok());
                case ERROR -> Optional.of(error(parts.length >= 4 ? parts[3] : "unknown"));
            };
        } catch (NumberFormatException error) {
            return Optional.empty();
        }
    }

    private static Optional<LanMessage> parsePeerWithPort(Type type, int version, String[] parts) {
        if (parts.length < 6) {
            return Optional.empty();
        }

        return Optional.of(new LanMessage(
                type,
                version,
                parts[3],
                parts[4],
                Integer.parseInt(parts[5]),
                NO_NUMBER,
                NO_NUMBER,
                ""
        ));
    }

    private static Optional<LanMessage> parseAccept(int version, String[] parts) {
        if (parts.length < 5) {
            return Optional.empty();
        }

        return Optional.of(new LanMessage(Type.ACCEPT, version, parts[3], parts[4], NO_NUMBER, NO_NUMBER, NO_NUMBER, ""));
    }

    private static Optional<LanMessage> parseDecline(int version, String[] parts) {
        if (parts.length < 4) {
            return Optional.empty();
        }

        return Optional.of(new LanMessage(Type.DECLINE, version, parts[3], "", NO_NUMBER, NO_NUMBER, NO_NUMBER, ""));
    }

    private static Optional<LanMessage> parseMove(int version, String[] parts) {
        if (parts.length < 6) {
            return Optional.empty();
        }

        return Optional.of(new LanMessage(
                Type.MOVE,
                version,
                parts[3],
                "",
                NO_NUMBER,
                Integer.parseInt(parts[4]),
                Integer.parseInt(parts[5]),
                ""
        ));
    }

    /** 协议用 | 分隔字段，所以字段内容里不能保留 |。 */
    private static String clean(String value) {
        return value == null ? "" : value.replace("|", " ");
    }
}
