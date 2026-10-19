package com.teammatch.profile;

/**
 * 看别人的个人资料页时，能看到多少由两人的关系决定。规则都放在这里，方便单独测试。
 * <ul>
 *   <li>基本信息、技能、信用分：登录的人都能看，和列表、用户卡片上本来就显示的一样</li>
 *   <li>简介、"能给队伍带来的"：已认证的人才能看，防止有人随便注册小号批量扒资料</li>
 *   <li>参赛履历、组队记录、短板：队友和主动联系过的人一定能看；其他已认证同学看本人的设置</li>
 *   <li>联系方式：只有队友能看（沿用档案里"只给队友看"的规则）</li>
 *   <li>履历的证明链接（证书照片上一般有真实姓名、学号）、问卷里"希望队友包容我的短板"：
 *       只给真正要一起组队的人看（队友、主动联系过的人、管理员），不受本人"公开"设置的影响</li>
 * </ul>
 */
public final class UserPageAccess {

    private UserPageAccess() {
    }

    /**
     * SELF 自己 / TEAMMATE 现在是队友 /
     * RELATED 有关联（对方主动找过来：对方申请了我所在的队，或者对方所在的队邀请了我；或者查看的人是管理员）/
     * MEMBER 其他已认证同学 / GUEST 还没认证的人
     */
    public enum Level { SELF, TEAMMATE, RELATED, MEMBER, GUEST }

    /** 参赛履历和组队记录给谁看：PUBLIC 所有已认证同学 / RELATED 只给队友和主动联系过的人 */
    public enum HistoryVisibility { PUBLIC, RELATED }

    public static Level level(boolean self, boolean teammate, boolean related, boolean viewerAdmin, boolean viewerVerified) {
        if (self) {
            return Level.SELF;
        }
        if (teammate) {
            return Level.TEAMMATE;
        }
        // 主动申请入队、或者主动发出邀请，就等于同意被对方查看，所以这时不管本人怎么设置都能看到完整履历
        if (related || viewerAdmin) {
            return Level.RELATED;
        }
        return viewerVerified ? Level.MEMBER : Level.GUEST;
    }

    public static boolean canSeeIntro(Level level) {
        return level != Level.GUEST;
    }

    public static boolean canSeeHistory(Level level, HistoryVisibility setting) {
        return switch (level) {
            case SELF, TEAMMATE, RELATED -> true;
            case MEMBER -> setting == HistoryVisibility.PUBLIC;
            case GUEST -> false;
        };
    }

    public static boolean canSeeContact(Level level) {
        return level == Level.SELF || level == Level.TEAMMATE;
    }

    /** 证明链接和短板：比履历更私人，履历公开了也只给要一起组队的人看 */
    public static boolean canSeePrivateDetails(Level level) {
        return level == Level.SELF || level == Level.TEAMMATE || level == Level.RELATED;
    }

    /**
     * 奖项有多"重"，用来挑资料页上的最高荣誉，以及统计获奖次数。0 表示不算获奖（没填、成功参赛）。
     * 先看级别（国家 > 省 > 市 > 校），同一级别再看等次（特等 > 一 > 二 > 三 > 优秀）。
     */
    public static int awardRank(String award) {
        if (award == null || award.isBlank() || award.contains("参赛")) {
            return 0;
        }
        int level;
        if (award.contains("国际") || award.contains("国家") || award.contains("全国") || award.startsWith("国")) {
            level = 40;
        } else if (award.contains("省") || award.contains("赛区")) {
            level = 30;
        } else if (award.contains("市")) {
            level = 20;
        } else if (award.contains("校")) {
            level = 10;
        } else {
            level = 5;
        }
        int grade;
        if (award.contains("特等")) {
            grade = 5;
        } else if (award.contains("一等") || award.endsWith("一")) {
            grade = 4;
        } else if (award.contains("二等") || award.endsWith("二")) {
            grade = 3;
        } else if (award.contains("三等") || award.endsWith("三")) {
            grade = 2;
        } else {
            grade = 1;
        }
        return level + grade;
    }
}
