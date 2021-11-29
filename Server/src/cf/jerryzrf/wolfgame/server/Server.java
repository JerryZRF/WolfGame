package cf.jerryzrf.wolfgame.server;

import java.io.IOException;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class Server {
    public static final String version = "0.1.0-alpha.1";
    public static final Set<Player> players = new HashSet<>();
    public static final Scanner sc = new Scanner(System.in);
    public static final Game game = new Game();
    private static Thread gameThread = new Thread(Connect::waitForPlayerJoin);

    public static void main(String[] args) {
        argsHandle(args);  //处理参数
        System.out.println("服务器启动，开放" + Connect.port + "端口");
        System.out.println("作者 JerryZRF");
        System.out.println("版本 " + version);
        //关闭服务器时关闭连接
        Runtime.getRuntime().addShutdownHook(new Thread(() -> players.forEach(p -> {
            try {
                p.send("stop");
                p.getSocket().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        })));
        //接受指令
        new Thread(() -> {
            while(true) {
                commandHandle(sc.nextLine());
            }
        }).start();
        System.out.println("等待玩家加入中...");
        //等待玩家加入
        gameThread.start();
    }

    public static void shout(String name, String message) {
        players.forEach(p -> p.say(name, message));
        System.out.println("[" + name + "]" + message);
    }

    /**
     * 处理来自玩家的信息
     *
     * @param player 发送玩家
     * @param message 信息
     */
    public static void messageHandler(Player player, String message) {
        if (message.startsWith("say")) {
            if (game.status == GameStatus.Waiting || game.speaker == player) {
                shout(player.getName(), message.replaceFirst("say", ""));
            }
        } else if (message.equalsIgnoreCase("quit")) {
            players.remove(player);
            try {
                player.getSocket().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            shout("法官", "玩家" + player.getName() + "退出游戏，现在有" + players.size() + "名玩家");
        } else if (message.startsWith("kill ")) {
            Player p = getPlayer(message.replaceFirst("kill ", ""));
            if (p == null) {
                player.say("法官", "玩家不存在！");
                return;
            }
            if (game.playerIdentity.get(player) == Identity.Wolf && game.status == GameStatus.Wolf) {
                game.killPlayer.put(player, p);
                game.say2PlayersByIdentity(Identity.Wolf, player.getName() + "选择杀" + p.getName());
                game.wolfKillPlayer();
            } else if (game.playerIdentity.get(player) == Identity.Witch && game.status == GameStatus.Witch) {
                if (message.equalsIgnoreCase("kill null")) {
                    game.witchKillPlayer(null);
                } else {
                    game.witchKillPlayer(p);
                }
            } else if (game.playerIdentity.get(player) == Identity.Hunter) {
                game.hunterKillPlayer(p);
            }
        } else if (message.equalsIgnoreCase("save yes")) {
            game.savePlayer(true);
        } else if (message.equalsIgnoreCase("save no")) {
            game.savePlayer(false);
        } else if (message.startsWith("check ")) {
            if (game.status != GameStatus.Prophet) {
                return;
            }
            if (game.playerIdentity.get(player) == Identity.Prophet) {
                game.check(getPlayer(message.replaceFirst("check ", "")));
            }
        } else if (message.startsWith("protect ")) {
            if (game.status != GameStatus.Guard) {
                return;
            }
            if (game.playerIdentity.get(player) == Identity.Guard) {
                game.protect(getPlayer(message.replaceFirst("protect ", "")));
            }
        } else if (message.startsWith("police ")) {
            if (game.status == GameStatus.Police) {
                game.voted.add(player);
                if (message.replaceFirst("police ", "").equalsIgnoreCase("yes")) {
                    Server.shout("法官", player.getName() + "竞选警长");
                    game.prePolice.add(player);
                    game.votedPolice.add(player);
                } else {
                    if (game.votedPolice.contains(player)) {
                        Server.shout("法官", player.getName() + "退出竞选警长，且不可以投票");
                        game.prePolice.remove(player);
                    }
                }
            } else {
                player.say("法官", "你不能在发言时竞选");
            }
        } else if (message.startsWith("vote police ")) {
            if (message.equalsIgnoreCase("vote police null")) {
                game.voted.add(player);
                return;
            }
            Player p = getPlayer(message.replaceFirst("vote police ", ""));
            if (p == null) {
                player.say("法官", "玩家不存在！");
                return;
            }
            game.voted.add(player);
            if (game.prePolice.contains(p)) {
                if (!game.votedPolice.contains(player)) {
                    if (game.vote2Player.get(player) != null) {
                        game.vote.put(game.vote2Player.get(player), game.vote.get(game.vote2Player.get(player)) - 1);
                        Server.shout("法官", "玩家" + player.getName() + "改投了" + p.getName());
                    } else {
                        Server.shout("法官", "玩家" + player.getName() + "投了" + p.getName());
                    }
                    game.vote.put(p, game.vote.get(p) + 1);
                    game.vote2Player.put(player, p);
                } else {
                    player.say("法官", "上警的玩家不能投票");
                }
            } else {
                player.say("法官", "该玩家未竞选法官！");
            }
        } else if (message.equalsIgnoreCase("go")) {
            if (game.speaker == player) {
                game.go = true;
            }
        } else if (message.startsWith("vote ")) {
            if (message.equalsIgnoreCase("vote null")) {
                game.voted.add(player);
                return;
            }
            Player p = getPlayer(message.replaceFirst("vote ", ""));
            if (p == null) {
                player.say("法官", "玩家不存在！");
                return;
            }
            game.voted.add(player);
            if (game.status == GameStatus.Vote) {
                if (game.vote2Player.get(player) != null) {
                    if (player == game.police.get(0)) {
                        game.vote.put(game.vote2Player.get(player), game.vote.get(game.vote2Player.get(player)) - 1.5);
                    } else {
                        game.vote.put(game.vote2Player.get(player), game.vote.get(game.vote2Player.get(player)) - 1);
                    }
                    Server.shout("法官", "玩家" + player.getName() + "改投了" + p.getName());
                } else {
                    Server.shout("法官", "玩家" + player.getName() + "投了" + p.getName());
                }
                if (player == game.police.get(0)) {
                    game.vote.put(p, game.vote.get(p) + 1.5);
                } else {
                    game.vote.put(p, game.vote.get(p) + 1);
                }
                game.vote2Player.put(player, p);
            }
        } else if (message.startsWith("give ")) {
            Player p = getPlayer(message.replaceFirst("give ", ""));
            if (p == null) {
                player.say("法官", "玩家不存在！");
                return;
            }
            game.police.clear();
            game.police.add(p);
            game.givePolice = true;
        } else if (message.equalsIgnoreCase("boom")) {
            if (game.status != GameStatus.Police && game.status != GameStatus.PoliceSpeaking && game.status != GameStatus.PoliceVote && game.status != GameStatus.Vote) {
                return;
            }
            if (game.playerIdentity.get(player) != Identity.Wolf) {
                return;
            }
            gameThread.interrupt();
            gameThread = new Thread(() -> {
                Server.shout("法官", "玩家" + player.getName() + "自爆");
                game.playerDie(player, true);
                Server.shout("法官", "进入黑夜");
                while (true) {
                    game.game(false);
                }
            });
            gameThread.start();
        } else if (message.startsWith("team ")) {
            game.getPlayerByIdentity(game.playerIdentity.get(player)).
                    forEach(p -> p.say(player.getName(), message.replaceFirst("team ", "")));
        }
    }

    /**
     * 处理控制台指令
     *
     * @param command 指令
     */
    public static void commandHandle(String command) {
        if (command.startsWith("/say ")) {
            shout("法官", command.replaceFirst("/say ", ""));
        }
    }

    /**
     * 处理参数
     *
     * @param args 参数
     */
    private static void argsHandle(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equalsIgnoreCase("-d")) {
                break;
            } else if (args[i].equalsIgnoreCase("--players")) {
                game.playerNum = Integer.parseInt(args[i + 1]);
            } else if (args[i].equalsIgnoreCase("--port")) {
                Connect.port = Integer.parseInt(args[i + 1]);
            }
        }
        if (game.playerNum < 4) {
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
        game.inGamePlayers.forEach(p -> {
            if (p.getName().equalsIgnoreCase(name)) {
                player[0] = p;
            }
        });
        return player[0];
    }
}