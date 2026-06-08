package com.example.wuziqi;

import java.net.InetAddress;
import java.time.Instant;

/**
 * 局域网内发现到的玩家。
 *
 * @param id 玩家本次启动生成的唯一 ID
 * @param username 玩家显示名
 * @param address 玩家 IP 地址
 * @param tcpPort 玩家接收邀请和落子的 TCP 端口
 * @param lastSeen 最近一次收到广播的时间
 */
public record PeerInfo(String id, String username, InetAddress address, int tcpPort, Instant lastSeen) {

    /** 超过 8 秒没有收到广播，就认为这个玩家离线了。 */
    public boolean isExpired() {
        return lastSeen.plusSeconds(8).isBefore(Instant.now());
    }

    /** ListView 会调用 toString() 来显示每一项。 */
    @Override
    public String toString() {
        return username + " (" + address.getHostAddress() + ")";
    }
}
