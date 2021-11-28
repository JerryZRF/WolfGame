package cf.jerryzrf.wolfgame.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

import static java.lang.System.exit;

public class Client {
    public static final Scanner sc = new Scanner(System.in);
    public static final String version = "0.1.0-alpha.1";
    private static String name = "";
    static BufferedReader reader;
    static PrintWriter writer;

    public static void main(String[] args) {
        System.out.println("-----------------------------------------------");
        System.out.println("欢迎游玩 狼人杀-WolfGame");
        System.out.println("作者 JerryZRF");
        System.out.println("版本 " + version);
        System.out.println("-----------------------------------------------");
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equalsIgnoreCase("--name")) {
                name = args[i + 1];
            }
        }
        if (name.equalsIgnoreCase("")) {
            System.out.println("你的昵称：");
            name = sc.nextLine();
        }
        Connect.init(args);
        reader = Connect.getReader();
        writer = Connect.getWriter();
        new Thread(() -> {
            while (true) {
                commandHandle(sc.nextLine());
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
                if (command.equalsIgnoreCase("say")) {
                    System.out.print("[" + reader.readLine() + "]");
                    System.out.println(reader.readLine());
                } else if (command.equalsIgnoreCase("stop")) {
                    System.out.println("服务器关闭");
                    exit(0);
                }
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().equalsIgnoreCase("Connection reset")) {
                    System.out.println("服务器关闭");
                } else {
                    e.printStackTrace();
                }
                exit(2);
            }
        }
    }
}
