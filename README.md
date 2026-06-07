# FXGL 五子棋

一个使用 FXGL 开发的 20x20 桌面五子棋小游戏。

## 功能

- 20x20 棋盘
- 黑白双方轮流落子
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

推送到 `main` / `master` 分支或推送 `v*` 标签后，会自动通过 GitHub Actions 分别在 macOS 和 Windows runner 上使用 `jpackage` 打包：

- `wuziqi-macos-dmg`：macOS `.dmg`
- `wuziqi-windows-exe`：Windows `.exe`

也可以在 Actions 页面手动运行 `Package desktop installers` workflow。
