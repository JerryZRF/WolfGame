package cf.jerryzrf.wolfgame.server;

import cf.jerryzrf.wolfgame.server.game.Game;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author JerryZRF
 */
public final class Connect {
    public final static int VERSION = 1;  //版本信息
    public static int port = 20210;       //端口

    /**
     * 等待玩家加入
     */
    public static void waitForPlayerJoin() {
        ServerSocket ss = null;
        try {
            ss = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(2);  //网络错误
        }
        while (Server.PLAYERS.size() < Server.GAME.playerNum) {
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
                Server.PLAYERS.add(player);
                System.out.println("新玩家 " + player.getName() + " 加入");
            } catch (IOException e) {
                e.printStackTrace();
            }
            Game.shout("等待更多玩家加入 (" + Server.PLAYERS.size() + "/" + Server.GAME.playerNum + ")");
        }
        Server.GAME.start();  //开始游戏
    }
}
