package cf.jerryzrf.wolfgame.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;


public class Player {
    private String name;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    /**
     * 初始化玩家
     * @param socket 玩家连接
     * @return 错误值
     * 0 无错误
     * 1 名字非法
     * 2 版本不同
     */
    int init(Socket socket) {
        this.socket = socket;
        try {
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            name = reader.readLine();
            AtomicBoolean nameOk = new AtomicBoolean(true);
            Server.players.forEach(p -> {
                if (p.getName().equalsIgnoreCase(name)) {
                    nameOk.set(false);
                }
            });  //重复
            if (name.contains(" ")) {
                nameOk.set(false);
            }  //包含空格
            if (!nameOk.get()) {
                return 1;
            }
            if (Integer.parseInt(reader.readLine()) != Connect.version) {
                return 2;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        receiveMessage();
        return 0;
    }

    /**
     * 向客户端直接发送信息
     * @param data 信息
     */
    public void send(String data) {
        writer.println(data);
    }

    /**
     * 向玩家说话
     * @param name 说话者
     * @param message 消息
     */
    public void say(String name, String message) {
        send("say");
        send(name);
        send(message);
    }

    /**
     * 接收客户端的信息
     */
    public void receiveMessage() {
        new Thread(() -> {
            while(true) {
                try {
                    Server.messageHandler(this, reader.readLine());
                } catch (IOException e) {
                    if (e.getMessage() != null) {
                        if (e.getMessage().equalsIgnoreCase("Connection reset") || e.getMessage().equalsIgnoreCase("Socket closed")) {
                            Server.game.playerExit(this);
                        }
                        return;
                    }
                    e.printStackTrace();
                    return;
                }
            }
        }).start();
    }

    public String getName() {
        return name;
    }

    public Socket getSocket() {
        return socket;
    }
}
