# FXGL 五子棋

一个使用 FXGL 开发的 20x20 桌面五子棋小游戏。

## 功能

- 20x20 棋盘
- 双人对战模式
- 人机对战模式，难度分为简单、中等、困难三级
- 局域网玩家发现和邀请对战
- 横向、纵向、两条斜线五连胜负判断
- 棋盘下满后的平局判断
- 重新开始按钮

## 运行

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvnd javafx:run
```

## 打包

macOS DMG：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./scripts/package-macos-dmg.sh
```

Windows x64 EXE：

```bat
scripts\package-windows-exe.bat
```

`jpackage` 不支持跨平台打包，所以 Windows EXE 需要在 Windows x64 上运行脚本生成。

## GitHub Actions

推送代码后，会自动通过 GitHub Actions 分别在 macOS 和 Windows runner 上使用 `jpackage` 打包：

- `wuziqi-macos-arm64-dmg`：macOS Apple Silicon `.dmg`
- `wuziqi-macos-x64-dmg`：macOS Intel `.dmg`
- `wuziqi-windows-x64-exe`：Windows x64 `.exe`

推送 `v*` 版本标签时，还会自动创建 GitHub Release，并把两个 macOS `.dmg` 和 Windows `.exe` 上传到 Release 附件中。例如：

```bash
git tag v1.0.0
git push origin v1.0.0
```

也可以在 Actions 页面手动运行 `Package desktop installers` workflow。

## 运行日志

Windows 安装包启动时会打开控制台窗口，方便查看启动错误。应用日志和 FXGL 日志会写入：

```text
%LOCALAPPDATA%\Wuziqi\logs\wuziqi.log
```

macOS 日志会写入：

```text
~/Library/Logs/Wuziqi/wuziqi.log
```

## 局域网对战

两台电脑连接到同一个局域网并同时运行游戏后，在右侧输入用户名，点击“刷新”查看在线玩家，选择玩家后点击“邀请”。对方接受后，邀请方执黑先手，被邀请方执白后手。

如果看不到对方，请确认系统防火墙允许 Wuziqi 访问局域网。
