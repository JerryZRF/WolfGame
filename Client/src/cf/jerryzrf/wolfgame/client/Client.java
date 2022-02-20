package cf.jerryzrf.wolfgame.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

import static java.lang.System.exit;

/**
 * @author JerryZRF
 */
public final class Client {
    public static final Scanner SCANNER = new Scanner(System.in);
    public static final String VERSION = "0.1.0-alpha.1";
    private static String name = "";
    static BufferedReader reader;
    static PrintWriter writer;

    public static void main(String[] args) {
        System.out.println("-----------------------------------------------");
        System.out.println("欢迎游玩 狼人杀-WolfGame");
        System.out.println("作者 JerryZRF");
        System.out.println("版本 " + VERSION);
        System.out.println("-----------------------------------------------");
        for (int i = 0; i < args.length - 1; i++) {
            if ("--name".equalsIgnoreCase(args[i])) {
                name = args[i + 1];
            }
        }
        if ("".equalsIgnoreCase(name)) {
            System.out.println("你的昵称：");
            name = SCANNER.nextLine();
        }
        Connect.init(args);
        reader = Connect.getReader();
        writer = Connect.getWriter();
        new Thread(() -> {
            while (true) {
                commandHandle(SCANNER.nextLine());
            }
        }).start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            writer.println("quit");
            try {
                Connect.getSocket().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
        new Thread(Client::getCommand).start();

    }

    public static String getName() {
        return name;
    }

    /**
     * 向服务器说话
     *
     * @param message 信息
     */
    public static void say(String message) {
        writer.println("say" + message);
    }

    /**
     * 处理控制台的指令
     *
     * @param command 指令
     */
    public static void commandHandle(String command) {
        if (!command.startsWith("/")) {
            say(command);
        } else {
            writer.println(command.replaceFirst("/", ""));
        }
    }

    /**
     * 获取服务器信息
     */
    private static void getCommand() {
        while (true) {
            try {
                String command = reader.readLine();
                if (command == null) {
                    continue;
                }
                if ("say".equalsIgnoreCase(command)) {
                    System.out.print("[" + reader.readLine() + "]");
                    System.out.println(reader.readLine());
                } else if ("stop".equalsIgnoreCase(command)) {
                    System.out.println("服务器关闭");
                    exit(1);
                } else if ("die".equalsIgnoreCase(command)) {
                    new Thread(Client::die).start();
                }
            } catch (IOException e) {
                if (e.getMessage() != null && "Connection reset".equalsIgnoreCase(e.getMessage())) {
                    System.out.println("服务器关闭");
                } else {
                    e.printStackTrace();
                }
                exit(2);
            }
        }
    }

    private static void die() {
        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }
}
