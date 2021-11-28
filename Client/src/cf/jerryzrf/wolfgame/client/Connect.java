package cf.jerryzrf.wolfgame.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Connect {
    private static String serverIP = "localhost";  //服务器IP
    private static int serverPort = 20210;         //服务器端口
    private static Socket socket;                  //服务器连接
    private static BufferedReader reader;
    private static PrintWriter writer;
    private final static int version = 1;          //版本信息

    /***
     * 初始化和服务器的连接
     * @param args 启动参数
     */
    public static void init(String[] args) {
        if (args.length == 0) {
            System.out.print("请输入服务器IP：");
            serverIP = Client.sc.nextLine();
            System.out.print("请输入服务器端口：");
            serverPort = Client.sc.nextInt();
        } else {
            for (int i = 0; i < args.length - 1; i++) {
                if (args[i].equalsIgnoreCase("--server")) {
                    serverIP = args[i + 1];
                } else if (args[i].equalsIgnoreCase("--port")) {
                    serverIP = args[i + 1];
                } else if (args[i].equalsIgnoreCase("-d")) {
                    break;
                }
            }
        }
        try {
            socket = new Socket(serverIP, serverPort);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            writer.println(Client.getName());
            writer.println(version);
            String wrong = reader.readLine();
            if (!wrong.equalsIgnoreCase("ok")) {
                System.out.println("连接失败，" + wrong);
                System.exit(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
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