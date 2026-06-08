package com.example.wuziqi;

import java.net.InetAddress;

/**
 * 一场局域网对局的连接信息。
 *
 * @param peerId 对方玩家的唯一 ID
 * @param peerName 对方用户名
 * @param peerAddress 对方 IP 地址
 * @param peerPort 对方 TCP 端口
 * @param localPlayer 本机执黑还是执白
 */
public record NetworkSession(String peerId, String peerName, InetAddress peerAddress, int peerPort, int localPlayer) {
}
