package com.example.wuziqi;

import com.almasb.fxgl.logging.ConsoleOutput;
import com.almasb.fxgl.logging.FileOutput;
import com.almasb.fxgl.logging.Logger;
import com.almasb.fxgl.logging.LoggerConfig;
import com.almasb.fxgl.logging.LoggerLevel;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * 应用启动诊断。
 *
 * <p>jpackage 打包后的应用经常会“点了没反应”，因为图形应用没有控制台。
 * 这个类把标准输出、错误输出和未捕获异常都写入用户目录，方便排查问题。</p>
 */
public final class AppDiagnostics {

    /** 工具类不需要被实例化。 */
    private AppDiagnostics() {
    }

    /** 安装日志、异常处理和 FXGL 日志输出。 */
    public static void install() {
        try {
            Path appDataDir = resolveAppDataDir();
            Files.createDirectories(appDataDir);
            // FXGL 一些服务会使用相对路径；指到用户目录比指到安装目录更安全。
            System.setProperty("user.dir", appDataDir.toString());

            Path logDir = resolveLogDir(appDataDir);
            Files.createDirectories(logDir);
            configureFxglLogging(logDir);

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
            System.out.println("App data dir: " + appDataDir);
            System.out.println("FXGL user.dir: " + System.getProperty("user.dir"));
            System.out.println("Log file: " + logDir.resolve("wuziqi.log"));
        } catch (IOException | RuntimeException error) {
            // 如果日志系统本身初始化失败，至少把错误打到原始 stderr。
            error.printStackTrace(System.err);
        }
    }

    /** 让 FXGL 自己的日志也写入用户目录，而不是默认的相对 logs/ 目录。 */
    private static void configureFxglLogging(Path logDir) {
        Logger.removeAllOutputs();
        Logger.configure(new LoggerConfig());
        Logger.addOutput(new ConsoleOutput(), LoggerLevel.DEBUG);
        Logger.addOutput(new FileOutput("FXGL", logDir.toString()), LoggerLevel.DEBUG);
    }

    /** 不同操作系统的应用数据目录习惯不同。 */
    private static Path resolveAppDataDir() {
        String osName = System.getProperty("os.name", "").toLowerCase();

        if (osName.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "Wuziqi");
            }
        }

        if (osName.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Application Support", "Wuziqi");
        }

        return Path.of(System.getProperty("user.home"), ".wuziqi");
    }

    /** macOS 通常把日志放在 Library/Logs，Windows 放在 LOCALAPPDATA。 */
    private static Path resolveLogDir(Path appDataDir) {
        String osName = System.getProperty("os.name", "").toLowerCase();

        if (osName.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Logs", "Wuziqi");
        }

        return appDataDir.resolve("logs");
    }

    /**
     * 一个输出流写两份：一份保留原控制台输出，一份写日志文件。
     */
    private static final class TeeOutputStream extends OutputStream {

        private final OutputStream first;
        private final OutputStream second;

        private TeeOutputStream(OutputStream first, OutputStream second) {
            this.first = first;
            this.second = second;
        }

        /** 单字节写入。PrintStream 可能会调用这个方法。 */
        @Override
        public void write(int value) throws IOException {
            first.write(value);
            second.write(value);
        }

        /** 批量写入，比逐字节更高效。 */
        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            first.write(bytes, offset, length);
            second.write(bytes, offset, length);
        }

        /** 确保两个目标都把缓冲区内容刷出去。 */
        @Override
        public void flush() throws IOException {
            first.flush();
            second.flush();
        }

        /** 关闭两个底层输出流。 */
        @Override
        public void close() throws IOException {
            first.close();
            second.close();
        }
    }
}
