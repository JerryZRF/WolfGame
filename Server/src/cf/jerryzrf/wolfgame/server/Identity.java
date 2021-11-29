package cf.jerryzrf.wolfgame.server;

public enum Identity {
    Wolf, Villager, Hunter, Witch, Prophet, Guard;

    public static String getName(Identity identity) {
        return switch (identity) {
            case Wolf -> "狼人";
            case Villager -> "村民";
            case Hunter -> "猎人";
            case Witch -> "女巫";
            case Prophet -> "预言家";
            case Guard -> "守卫";
        };
    }
}
