package cf.jerryzrf.wolfgame.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Connect {
    public final static int version = 1;  //版本信息
    public static int port = 20210;       //端口
    /**
     * 等待玩家加入
     */
    public static void waitForPlayerJoin(){
        ServerSocket ss = null;
        try {
            ss = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(2);  //网络错误
        }
        while (Server.players.size() < Server.game.playerNum) {
            try {
                Socket socket = ss.accept();
                Player player = new Player();
                int wrong = player.init(socket);
                if (wrong == 1) {
                    player.send("非法的名字");
                    continue;
                } else if (wrong == 2) {
                    player.send("版本不同");
                } else {
                    player.send("ok");
                }
                Server.players.add(player);
                System.out.println("新玩家 " + player.getName() + " 加入");
            } catch (IOException e) {
                e.printStackTrace();
            }
            Server.shout("法官", "等待更多玩家加入 (" + Server.players.size() + "/" + Server.game.playerNum + ")");
        }
        Server.game.start();  //开始游戏
    }
}
