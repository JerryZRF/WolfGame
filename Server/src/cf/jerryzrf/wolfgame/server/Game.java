package cf.jerryzrf.wolfgame.server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class Game {
    public int playerNum = 4;  //应有的玩家数量
    public final Map<Player, Identity> playerIdentity = new HashMap<>();  //玩家阵容
    public final Map<Player, Player> killPlayer = new HashMap<>();  //狼人要杀的玩家
    public final Set<Player> voted = Collections.synchronizedSet(new HashSet<>());  //已经投票的玩家
    private final List<Player> diePlayer = Collections.synchronizedList(new ArrayList<>());  //当晚死亡的玩家
    private boolean poison = true;     //女巫是否有毒药
    private boolean antidote = true;   //女巫是否有解药
    private volatile Boolean save = null;       //本回合女巫是否救人
    private volatile boolean kill = false;      //本回合女巫是否杀人
    private volatile boolean check = false;     //本回合预言家是否查人
    private volatile boolean protect = false;   //本回合守卫是否保护人
    private volatile Boolean hunter = null;     //猎人是否带走玩家
    public volatile boolean givePolice = false;//是否交接警徽
    private Player protectPlayer = null;        //上回合守卫保护的人 & 守卫保护的玩家
    public GameStatus status = GameStatus.Waiting;  //游戏状态
    public Set<Player> prePolice = Collections.synchronizedSet(new HashSet<>());  //待竞选警长
    public Set<Player> votedPolice = new HashSet<>();//竞选过警长
    public final Map<Player, Double> vote = new ConcurrentHashMap<>();  //票数
    public final List<Player> police = new ArrayList<>();  //警长
    public Player speaker = null;  //发言人
    public boolean go = false;     //是否跳过发言
    public final Map<Player, Player> vote2Player = new HashMap<>();
    public Set<Player> inGamePlayers; //还活着的玩家

    public void start() {
        /* 预准备阶段 */
        police.add(null);
        inGamePlayers = new HashSet<>(Server.players);
        Server.shout("法官", "-----------------------------------------");
        Server.shout("法官", "正在分配身份");
        {
            int wolf = 1;  //狼人数量
            List<Player> noRolePlayers = new ArrayList<>(Server.players);
            Random random = new Random();
            int num = random.nextInt(noRolePlayers.size());
            Player p = noRolePlayers.get(num);
            playerIdentity.put(p, Identity.Prophet);  //预言家
            noRolePlayers.remove(num);
            num = random.nextInt(noRolePlayers.size());
            p = noRolePlayers.get(num);
            playerIdentity.put(p, Identity.Witch);   //女巫
            noRolePlayers.remove(num);
            if (playerNum >= 6) {
                wolf++;
            }
            if (playerNum >= 8) {
                wolf++;
            }
            if (playerNum >= 11) {
                wolf++;
            }
            if (playerNum >= 8) {
                num = random.nextInt(noRolePlayers.size());
                p = noRolePlayers.get(num);
                playerIdentity.put(p, Identity.Hunter);   //猎人
                noRolePlayers.remove(num);
            }
            if (playerNum >= 11) {
                num = random.nextInt(noRolePlayers.size());
                p = noRolePlayers.get(num);
                playerIdentity.put(p, Identity.Guard);   //守卫
                noRolePlayers.remove(num);
            }
            for (int i = 0; i < wolf; i++) {
                num = random.nextInt(noRolePlayers.size());
                p = noRolePlayers.get(num);
                playerIdentity.put(p, Identity.Wolf);    //狼人
                noRolePlayers.remove(num);
            }
            noRolePlayers.forEach(player -> playerIdentity.put(player, Identity.Villager));
            noRolePlayers.clear();
        }  //分配角色
        Server.shout("法官", "游戏开始");
        Server.players.forEach(p -> p.say("法官", "你是" + Identity.getName(playerIdentity.get(p))));
        //获取狼人列表
        final String[] wolves = {""};
        getPlayerByIdentity(Identity.Wolf).forEach(p -> wolves[0] += p.getName() + " ");
        say2PlayersByIdentity(Identity.Wolf, "你的队友是：" + wolves[0]);
        /* 游戏正式开始 */
        for (int i = 0; ; i++) {
            game(i == 0);
        }
    }

    public void game(boolean police) {
        diePlayer.clear();
        Server.shout("法官", "天黑请闭眼");
        if (playerNum >= 11 && !getPlayerByIdentity(Identity.Guard).isEmpty()) {
            guardTime();
        }
        wolfTime();
        if (!getPlayerByIdentity(Identity.Prophet).isEmpty()) {
            prophetTime();
        }
        if (!getPlayerByIdentity(Identity.Witch).isEmpty()) {
            witchTime();
        }
        Server.shout("法官", "-----------------------------------------");
        Server.shout("法官", "天亮了");
        if (police) {
            policeTime();  //竞选警长
            prePolice = null;
            votedPolice = null;
            vote.clear();
            vote2Player.clear();
        }
        if (diePlayer.isEmpty()) {
            Server.shout("法官", "昨晚平安夜");
        } else {
            //获取死亡玩家列表
            final String[] diePlayerList = {""};
            diePlayer.forEach(p -> diePlayerList[0] += (p.getName() + "  "));
            Server.shout("法官", "昨晚" + diePlayerList[0] + "死了");
            diePlayer.forEach(p -> playerDie(p, false));
            diePlayer.clear();
        }
        voteTime();
        vote.clear();
        vote2Player.clear();
    }

    private void policeTime() {
        status = GameStatus.Police;
        Server.shout("法官", "-----------------------------------------");
        Server.shout("法官", "竞选警长 (输入/police yes|no)");
        Server.shout("法官", "等待全体玩家上警...");
        while (voted.size() < inGamePlayers.size()) {
            Thread.onSpinWait();
        } //等待玩家上警
        voted.clear();
        Server.shout("法官", "以下玩家上警");
        final String[] policeList = {""};
        prePolice.forEach(p -> policeList[0] += (p.getName() + "  "));
        Server.shout("法官", policeList[0]);
        status = GameStatus.PoliceSpeaking;
        Server.shout("法官", "-----------------------------------------");
        Server.shout("法官", "竞选玩家发言");
        speakingTime(prePolice);
        Server.shout("法官", "-----------------------------------------");
        Server.shout("法官", "全体玩家发言");
        speakingTime(inGamePlayers);
        Server.shout("法官", "-----------------------------------------");
        status = GameStatus.PoliceVote;
        Server.shout("法官", "请投票(/vote police 玩家名)");
        prePolice.forEach(p -> vote.put(p, 0d));  //初始化
        Server.shout("法官", "等待全体玩家投票...(/vote police null 弃票)");
        while (voted.size() < inGamePlayers.size()) {
            Thread.onSpinWait();
        }  //等待玩家全部投票
        voted.clear();
        Server.shout("法官", "-----------------------------------------");
        final double[] maxv = {0};
        vote.forEach((p, v) -> {
            if (v > maxv[0]) {
                police.clear();
                police.add(p);
                maxv[0] = v;
            } else if (v == maxv[0]) {
                police.add(p);
            }
        });
        if (police.size() == 1) {
            Server.shout("法官", "玩家" + police.get(0).getName() + "当上了警长！");
        } else {
            Server.shout("法官", "出现平票：");
            police.forEach(p -> Server.shout("法官", p.getName() + "：" + vote.get(p) + "票"));
            Server.shout("法官", "再次投票");
            policeTime();
        }
    }

    private void voteTime() {
        Server.shout("法官", "-----------------------------------------");
        Server.shout("法官", "发言时间");
        speakingTime(inGamePlayers);
        Server.shout("法官", "-----------------------------------------");
        status = GameStatus.Vote;
        Server.shout("法官", "投票时间(/vote 玩家名)");
        Server.shout("法官", "等待全部玩家投票...(/vote null 弃票)");
        inGamePlayers.forEach(p -> vote.put(p, 0d));  //初始化
        while (voted.size() < inGamePlayers.size()) {
            Thread.onSpinWait();
        }  //等待玩家全部投票
        voted.clear();
        List<Player> kickPlayer = new ArrayList<>();
        final double[] maxv = {0};
        vote.forEach((p, v) -> {
            if (v > maxv[0]) {
                kickPlayer.clear();
                kickPlayer.add(p);
                maxv[0] = v;
            } else if (v == maxv[0]) {
                kickPlayer.add(p);
            }
        });
        if (kickPlayer.size() == 1) {
            Server.shout("法官", "玩家" + kickPlayer.get(0).getName() + "被放逐");
            playerDie(kickPlayer.get(0), true);
        } else if (kickPlayer.size() == 0) {
            Server.shout("法官", "没人被放逐");
        } else {
            Server.shout("法官", "出现平票：");
            vote.forEach((p, v) -> Server.shout("法官", p.getName() + "：" + v + "票"));
            Server.shout("法官", "再次投票");
            voteTime();
        }
    }

    private void guardTime() {
        status = GameStatus.Guard;
        say2PlayersByIdentity(Identity.Guard, "-----------------------------------------");
        say2PlayersByIdentity(Identity.Guard, "守卫请睁眼");
        //获取玩家列表
        final String[] playerList = {""};
        inGamePlayers.forEach(p -> playerList[0] += (p.getName() + "  "));
        say2PlayersByIdentity(Identity.Guard, "请选择你要保护的人 (/protect 玩家名)");
        say2PlayersByIdentity(Identity.Guard, playerList[0]);
        while (!protect) {
            Thread.onSpinWait();
        }
        say2PlayersByIdentity(Identity.Guard, "守卫请闭眼");
        protect = false;
    }

    private void wolfTime() {
        status = GameStatus.Wolf;
        say2PlayersByIdentity(Identity.Wolf, "-----------------------------------------");
        say2PlayersByIdentity(Identity.Wolf, "狼人请睁眼");
        //获取玩家列表
        final String[] playerList = {""};
        inGamePlayers.forEach(p -> playerList[0] += (p.getName() + "  "));
        say2PlayersByIdentity(Identity.Wolf, "你们要杀谁 (/kill 玩家名)");
        say2PlayersByIdentity(Identity.Wolf, playerList[0]);
        while (diePlayer.isEmpty()) {
            Thread.onSpinWait();
        }  //等待狼人杀人
        say2PlayersByIdentity(Identity.Wolf, "狼人请闭眼");
        killPlayer.clear();
    }

    private void prophetTime() {
        status = GameStatus.Prophet;
        say2PlayersByIdentity(Identity.Prophet, "-----------------------------------------");
        say2PlayersByIdentity(Identity.Prophet, "预言家请睁眼");
        //获取玩家列表
        final String[] playerList = {""};
        inGamePlayers.forEach(p -> playerList[0] += (p.getName() + "  "));
        say2PlayersByIdentity(Identity.Prophet, "请选择你要查验的人(/check 玩家名)");
        say2PlayersByIdentity(Identity.Prophet, playerList[0]);
        while (!check) {
            Thread.onSpinWait();
        }
        say2PlayersByIdentity(Identity.Prophet, "预言家请闭眼");
        check = false;
    }

    private void witchTime() {
        status = GameStatus.Witch;
        say2PlayersByIdentity(Identity.Witch, "-----------------------------------------");
        say2PlayersByIdentity(Identity.Witch, "女巫请睁眼");
        if (antidote) {
            say2PlayersByIdentity(Identity.Witch, "昨晚" + diePlayer.get(0).getName() + "死了");
            say2PlayersByIdentity(Identity.Witch, "你有一瓶解药，你要用吗? (/save yes|no)");
            while (save == null) {
                Thread.onSpinWait();
            }
        }
        if (!save && poison) {
            say2PlayersByIdentity(Identity.Witch, "--------------------------------------");
            say2PlayersByIdentity(Identity.Witch, "你有一瓶毒药，你要用吗? (/kill 玩家名)");
            while (!kill) {
                Thread.onSpinWait();
            }
        }
        say2PlayersByIdentity(Identity.Witch, "女巫请闭眼");
        kill = false;
        save = false;
    }

    public void protect(Player player) {
        if (player == protectPlayer) {
            say2PlayersByIdentity(Identity.Guard, "--------------------------------------");
            say2PlayersByIdentity(Identity.Guard, "你不能连续两夜保护同一个人");
            return;
        }
        protectPlayer = player;
        protect = true;
    }

    public void check(Player player) {
        say2PlayersByIdentity(Identity.Prophet, "--------------------------------------");
        if (playerIdentity.get(player) == Identity.Wolf) {
            say2PlayersByIdentity(Identity.Prophet, "TA是狼人");
        } else {
            say2PlayersByIdentity(Identity.Prophet, "TA是好人");
        }
        check = true;
    }

    public void savePlayer(boolean save) {
        if (save) {
            antidote = false;
            if (!diePlayer.isEmpty()) {
                diePlayer.clear();
            } else {
                diePlayer.add(protectPlayer);
            }
        } else {
            getPlayerByIdentity(Identity.Witch).forEach(p -> p.say("法官", "你没有解药！"));
        }
        this.save = save;
    }

    public void witchKillPlayer(Player player) {
        if (poison) {
            if (player != null) {
                diePlayer.add(player);
                poison = false;
            }
        } else {
            getPlayerByIdentity(Identity.Witch).forEach(p -> p.say("法官", "你没有毒药！"));
        }
        kill = true;
    }

    public void wolfKillPlayer() {
        if (killPlayer.size() != getPlayerByIdentity(Identity.Wolf).size()) {
            return;
        }
        final Player[] player = {null};
        AtomicReference<Boolean> ok = new AtomicReference<>(true);
        getPlayerByIdentity(Identity.Wolf).forEach(p -> {
            if (player[0] == null) {
                player[0] = killPlayer.get(p);
            } else if (player[0] != killPlayer.get(p)) {
                ok.set(false);
            }
        });
        if (ok.get()) {
            if (protectPlayer != player[0]) {
                diePlayer.add(player[0]);
            }
            return;
        }
        say2PlayersByIdentity(Identity.Wolf, "--------------------------------------");
        say2PlayersByIdentity(Identity.Wolf, "你们的同伴的选择是");
        killPlayer.forEach((wolf, p) -> say2PlayersByIdentity(Identity.Wolf, wolf.getName() + " 选择杀 " + p.getName()));
        say2PlayersByIdentity(Identity.Wolf, "请统一你们的选择");
    }

    public void hunterKillPlayer(Player player) {
        if (player == null) {
            getPlayerByIdentity(Identity.Hunter).forEach(p ->
                    Server.shout("法官", "玩家" + p.getName() + "不带走任何人"));
            return;
        }
        if (hunter == false) {
            hunter = true;
            getPlayerByIdentity(Identity.Hunter).forEach(p ->
                    Server.shout("法官", "玩家" + p.getName() + "带走了" + player.getName()));
            playerDie(player, true);
        }
    }

    /**
     * 判断哪个阵容获胜
     *
     * @return 获胜阵容
     *
     * 狼人胜利！
     * 好人胜利！
     * null 未获胜
     */
    public String whoWin() {
        final int[] gods = {0};
        final int[] wolves = { 0 };
        final int[] villages = { 0 };
        inGamePlayers.forEach(p -> {
            if (playerIdentity.get(p) == Identity.Wolf) {
                wolves[0]++;
            } else if (playerIdentity.get(p) == Identity.Villager) {
                villages[0]++;
            } else {
                gods[0]++;
            }
        });
        if (gods[0] == 0 || villages[0] == 0) {
            return "狼人胜利！";
        }
        if (wolves[0] == 0) {
            return "好人胜利！";
        }
        return null;
    }

    /**
     * 获取对应身份的玩家
     *
     * @param identity 身份
     * @return 玩家列表
     */
    public Set<Player> getPlayerByIdentity(Identity identity) {
        final Set<Player> players = new HashSet<>();
        inGamePlayers.forEach(p -> {
            if (playerIdentity.get(p) == identity) {
                players.add(p);
            }
        });
        return players;
    }

    /**
     * 对对应身份的玩家说话
     *
     * @param identity 身份
     * @param message 消息
     */
    public void say2PlayersByIdentity(Identity identity, String message) {
        getPlayerByIdentity(identity).forEach(p -> p.say("法官", message));
    }

    private void speakingTime(Collection<Player> players) {
        players.forEach(player -> {
            speaker = player;
            Server.shout("法官", "--------------------------------------");
            Server.shout("法官", "现在是" + player.getName() + "发言(45s)");
            for(int i = 90; i >= 0; i--) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (go) {
                    go = false;
                    break;
                }
            }
        });
        speaker = null;
    }

    /**
     * 玩家死亡
     *
     * @param player 玩家
     * @param message 是否有遗言
     */
    public void playerDie(Player player, boolean message) {
        inGamePlayers.remove(player);
        if (police.size() != 0 && player == police.get(0)) {
            player.say("法官", "你要把警徽留给谁(/give 玩家名)");
            while (!givePolice) {
                Thread.onSpinWait();
            }
            Server.shout("法官", "玩家" + player.getName() + "把警徽留给了" + police.get(0));
        } else if (playerIdentity.get(player) == Identity.Hunter) {
            hunter = false;
            Server.shout("法官", "猎人死了");
            player.say("法官", "你要带走谁(/kill 玩家名)");
            while (!hunter) Thread.onSpinWait();
        }
        if (whoWin() != null) {
            Server.shout("法官", "-----------------------------------------");
            Server.shout("法官", whoWin());
            Server.shout("法官", "公布身份");
            Server.players.forEach(p -> Server.shout("法官", p.getName() + "：" + Identity.getName(playerIdentity.get(p))));
            Server.shout("法官", "20s后关闭服务器");
            try {
                Thread.sleep(20000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.exit(0);
        }
        if (message) {
            speaker = player;
            Server.shout("法官", player.getName() + "的遗言时间(30s)");
            for (int i = 60; i >= 0; i--) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (go) {
                    go = false;
                    break;
                }
            }
            speaker = null;
        }
        player.send("die");
        player.say("法官", "20s后关闭客户端");
        player.say("法官", "-----------------------------------------");
        player.say("法官", "公布身份");
        Server.players.forEach(p -> player.say("法官", p.getName() + "：" + Identity.getName(playerIdentity.get(p))));
    }

    public void playerExit(Player player) {
        Server.shout("法官", "玩家" + player.getName() + "退出了游戏");
        if (status != GameStatus.Waiting) {
            inGamePlayers.remove(player);
            if (whoWin() != null) {
                Server.shout("法官", "-----------------------------------------");
                Server.shout("法官", whoWin());
                Server.shout("法官", "公布身份");
                Server.players.forEach(p -> Server.shout("法官", p.getName() + "：" + Identity.getName(playerIdentity.get(p))));
                Server.shout("法官", "20s后关闭服务器");
                try {
                    Thread.sleep(20000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.exit(0);
            }
        }
    }
}