package cf.jerryzrf.wolfgame.server;

import cf.jerryzrf.wolfgame.server.game.Game;
import cf.jerryzrf.wolfgame.server.game.GameStatus;
import cf.jerryzrf.wolfgame.server.game.Identity;

import java.io.IOException;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

/**
 * @author JerryZRF
 */
public final class Server {
    public static final String VERSION = "0.1.0-alpha.2";
    public static final Set<Player> PLAYERS = new HashSet<>();
    public static final Scanner SCANNER = new Scanner(System.in);
    public static final Game GAME = new Game();
    private static Thread gameThread = new Thread(Connect::waitForPlayerJoin);

    public static void main(String[] args) {
        argsHandle(args);  //处理参数
        System.out.println("服务器启动，开放" + Connect.port + "端口");
        System.out.println("作者 JerryZRF");
        System.out.println("版本 " + VERSION);
        System.out.println("加载插件中...");
        //关闭服务器时
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                PLAYERS.forEach(p -> {
                    try {
                        p.send("stop");
                        p.getSocket().close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                })));
        //接受指令
        new Thread(() -> {
            while (true) {
                commandHandle(SCANNER.nextLine());
            }
        }).start();
        GAME.status = GameStatus.Waiting;
        System.out.println("等待玩家加入中...");
        //等待玩家加入
        gameThread.start();
    }

    public static void shout(String name, String message) {
        PLAYERS.forEach(p -> p.say(name, message));
        System.out.println("[" + name + "]" + message);
    }

    /**
     * 处理来自玩家的信息
     *
     * @param player  发送玩家
     * @param message 信息
     */
    public static void messageHandler(Player player, String message) {
        if (message.startsWith("say")) {
            if (GAME.status == GameStatus.Waiting || GAME.speaker == player) {
                shout(player.getName(), message.replaceFirst("say", ""));
            }
        } else if ("quit".equalsIgnoreCase(message)) {
            PLAYERS.remove(player);
            if (GAME.status != GameStatus.Waiting) {
                GAME.inGamePlayers.remove(player);
            }
            try {
                player.getSocket().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (GAME.status == GameStatus.Waiting) {
                Game.shout("玩家" + player.getName() + "离开了服务器，现在有" + PLAYERS.size() + "名玩家");
            } else {
                Game.shout("玩家" + player.getName() + "离开了服务器");
            }
        } else if (message.startsWith("kill ")) {
            Player p = getPlayer(message.replaceFirst("kill ", ""));
            if (p == null && !"null".equalsIgnoreCase(message.replaceFirst("kill ", ""))) {
                player.say("法官", "玩家不存在！");
                return;
            }
            if (GAME.playerIdentity.get(player) == Identity.Wolf) {
                if (GAME.status != GameStatus.Wolf) {
                    player.say("法官", "你现在不能这么做！");
                }
                GAME.killPlayer.put(player, p);
                GAME.say2PlayersByIdentity(Identity.Wolf, player.getName() + "选择杀" + p.getName());
                GAME.wolfKillPlayer();
            } else if (GAME.playerIdentity.get(player) == Identity.Witch) {
                if (GAME.status != GameStatus.Witch) {
                    player.say("法官", "你现在不能这么做！");
                    return;
                }
                GAME.witchKillPlayer(p);
            } else if (GAME.playerIdentity.get(player) == Identity.Hunter) {
                GAME.hunterKillPlayer(p);
            }
        } else if ("save yes".equalsIgnoreCase(message)) {
            if (GAME.status != GameStatus.Witch) {
                player.say("法官", "你现在不能这么做！");
                return;
            }
            GAME.savePlayer(true);
        } else if ("save no".equalsIgnoreCase(message)) {
            if (GAME.status != GameStatus.Witch) {
                player.say("法官", "你现在不能这么做！");
                return;
            }
            GAME.savePlayer(false);
        } else if (message.startsWith("check ")) {
            if (GAME.status != GameStatus.Prophet) {
                player.say("法官", "你现在不能这么做！");
                return;
            }
            if (GAME.playerIdentity.get(player) == Identity.Prophet) {
                GAME.check(getPlayer(message.replaceFirst("check ", "")));
            }
        } else if (message.startsWith("protect ")) {
            if (GAME.status != GameStatus.Guard) {
                player.say("法官", "你现在不能这么做！");
                return;
            }
            if (GAME.playerIdentity.get(player) == Identity.Guard) {
                GAME.protect(getPlayer(message.replaceFirst("protect ", "")));
            }
        } else if (message.startsWith("police ")) {
            if (GAME.status != GameStatus.Police) {
                player.say("法官", "你现在不能这么做！");
                return;
            }
            GAME.voted.add(player);
            if ("yes".equalsIgnoreCase(message.replaceFirst("police ", ""))) {
                Game.shout(player.getName() + "竞选警长");
                GAME.prePolice.add(player);
                GAME.votedPolice.add(player);
            } else {
                if (GAME.votedPolice.contains(player)) {
                    Game.shout(player.getName() + "退出竞选警长，且不可以投票");
                    GAME.prePolice.remove(player);
                }
            }
        } else if (message.startsWith("vote police ")) {
            if (GAME.status != GameStatus.PoliceVote) {
                player.say("法官", "你现在不能这么做！");
                return;
            }
            if ("vote police null".equalsIgnoreCase(message)) {
                GAME.voted.add(player);
                return;
            }
            Player p = getPlayer(message.replaceFirst("vote police ", ""));
            if (p == null) {
                player.say("法官", "玩家不存在！");
                return;
            }
            GAME.voted.add(player);
            if (GAME.prePolice.contains(p)) {
                if (!GAME.votedPolice.contains(player)) {
                    if (GAME.vote2Player.get(player) != null) {
                        GAME.vote.put(GAME.vote2Player.get(player), GAME.vote.get(GAME.vote2Player.get(player)) - 1);
                        Game.shout("玩家" + player.getName() + "改投了" + p.getName());
                    } else {
                        Game.shout("玩家" + player.getName() + "投了" + p.getName());
                    }
                    GAME.vote.put(p, GAME.vote.get(p) + 1);
                    GAME.vote2Player.put(player, p);
                } else {
                    player.say("法官", "上警的玩家不能投票！");
                }
            } else {
                player.say("法官", "该玩家未竞选法官！");
            }
        } else if ("go".equalsIgnoreCase(message)) {
            if (GAME.speaker == player) {
                GAME.go = true;
            }
        } else if (message.startsWith("vote ")) {
            if ("vote null".equalsIgnoreCase(message)) {
                GAME.voted.add(player);
                return;
            }
            Player p = getPlayer(message.replaceFirst("vote ", ""));
            if (p == null) {
                player.say("法官", "玩家不存在！");
                return;
            }
            GAME.voted.add(player);
            if (GAME.status != GameStatus.Vote) {
                player.say("法官", "你现在不能这么做！");
                return;
            }
            if (GAME.vote2Player.get(player) != null) {
                if (player == GAME.police.get(0)) {
                    GAME.vote.put(GAME.vote2Player.get(player), GAME.vote.get(GAME.vote2Player.get(player)) - 1.5);
                } else {
                    GAME.vote.put(GAME.vote2Player.get(player), GAME.vote.get(GAME.vote2Player.get(player)) - 1);
                }
                Game.shout("玩家" + player.getName() + "改投了" + p.getName());
            } else {
                Game.shout("玩家" + player.getName() + "投了" + p.getName());
            }
            if (player == GAME.police.get(0)) {
                GAME.vote.put(p, GAME.vote.get(p) + 1.5);
            } else {
                GAME.vote.put(p, GAME.vote.get(p) + 1);
            }
            GAME.vote2Player.put(player, p);
        } else if (message.startsWith("give ")) {
            Player p = getPlayer(message.replaceFirst("give ", ""));
            if (p == null) {
                player.say("法官", "玩家不存在！");
                return;
            }
            GAME.police.clear();
            GAME.police.add(p);
            GAME.givePolice = true;
        } else if ("boom".equalsIgnoreCase(message)) {
            if (GAME.status != GameStatus.Police && GAME.status != GameStatus.PoliceSpeaking && GAME.status != GameStatus.PoliceVote && GAME.status != GameStatus.Vote) {
                player.say("法官", "你现在不能这么做！");
                return;
            }
            if (GAME.playerIdentity.get(player) != Identity.Wolf) {
                player.say("法官", "只有狼人才可以这么做！");
                return;
            }
            gameThread.interrupt();
            gameThread = new Thread(() -> {
                Game.shout("玩家" + player.getName() + "自爆");
                GAME.playerDie(player, true);
                Game.shout("进入黑夜");
                while (true) {
                    GAME.game(false);
                }
            });
            gameThread.start();
        } else if (message.startsWith("team ")) {
            if (GAME.playerIdentity.get(player) == Identity.Wolf) {
                GAME.getPlayerByIdentity(GAME.playerIdentity.get(player)).
                        forEach(p -> p.say("[团队]" + player.getName(), message.replaceFirst("team ", "")));
            } else {
                player.say("法官", "只有狼人才可以这么做！");
            }
        } else if ("players".equalsIgnoreCase(message)) {
            final String[] playerList = {""};
            GAME.inGamePlayers.forEach(p -> playerList[0] += (p.getName() + "  "));
            player.say("法官", "游戏内玩家有：" + playerList[0]);
        } else if ("me".equalsIgnoreCase(message)) {
            player.say("法官", "你是" + Identity.getName(GAME.playerIdentity.get(player)));
            if (GAME.playerIdentity.get(player) == Identity.Wolf) {
                final String[] playerList = {""};
                GAME.getPlayerByIdentity(Identity.Wolf).forEach(p -> playerList[0] += (p.getName() + "  "));
                player.say("法官", "你的队友是：" + playerList[0]);
            }
        }
    }

    /**
     * 处理控制台指令
     *
     * @param command 指令
     */
    private static void commandHandle(String command) {
        if (command.startsWith("/say ")) {
            Game.shout(command.replaceFirst("/say ", ""));
        }
    }

    /**
     * 处理参数
     *
     * @param args 参数
     */
    private static void argsHandle(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--players".equalsIgnoreCase(args[i])) {
                GAME.playerNum = Integer.parseInt(args[i + 1]);
            } else if ("--port".equalsIgnoreCase(args[i])) {
                Connect.port = Integer.parseInt(args[i + 1]);
            }
        }
        if (GAME.playerNum < 4) {
            System.out.println("玩家至少为4人");
            System.exit(0);
        }
    }

    /**
     * 通过昵称获取玩家
     *
     * @param name 玩家昵称
     * @return 玩家
     */
    public static Player getPlayer(String name) {
        final Player[] player = {null};
        GAME.inGamePlayers.forEach(p -> {
            if (p.getName().equalsIgnoreCase(name)) {
                player[0] = p;
            }
        });
        return player[0];
    }
}