package cf.jerryzrf.wolfgame.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * @author JerryZRF
 */
public final class Connect {
    private static String serverIP = "localhost";  //服务器IP
    private static int serverPort = 20210;         //服务器端口
    private static Socket socket;                  //服务器连接
    private static BufferedReader reader;
    private static PrintWriter writer;
    private final static int VERSION = 1;          //版本信息

    /***
     * 初始化和服务器的连接
     * @param args 启动参数
     */
    public static void init(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--server".equalsIgnoreCase(args[i])) {
                serverIP = args[i + 1];
            } else if ("--port".equalsIgnoreCase(args[i])) {
                serverPort = Integer.parseInt(args[i + 1]);
            }
        }

        try {
            socket = new Socket(serverIP, serverPort);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            writer.println(Client.getName());
            writer.println(VERSION);
            String wrong = reader.readLine();
            if (!"ok".equalsIgnoreCase(wrong)) {
                System.out.println("连接失败，" + wrong);
                System.exit(0);
            }
        } catch (IOException e) {
            System.out.println("无法连接到服务器，原因：" + e.getMessage());
            System.exit(2);  //网络错误
        }
        System.out.println("已连接至服务器");
    }


    public static BufferedReader getReader() {
        return reader;
    }

    public static PrintWriter getWriter() {
        return writer;
    }

    public static Socket getSocket() {
        return socket;
    }
}