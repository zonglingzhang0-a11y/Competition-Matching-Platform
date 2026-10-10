package com.teammatch.match;

import java.util.List;
import java.util.Map;

/**
 * 匹配引擎的输入。引擎本身不查数据库，所有数据由 {@link RecommendService} 准备好再传进来，方便单元测试。
 */
public final class MatchInputs {

    private MatchInputs() {
    }

    /**
     * 一个人。general / edition 是问卷答案（题目代码 → 值），没填就是空 Map。
     *
     * @param skills      技能 id → 熟练度 1–3
     * @param experiences 参赛履历条数，判断"能不能带新人"用
     * @param credit      信用分 0–100，没有互评记录时为空
     */
    public record Person(long userId, String nickname, Map<String, Object> general, Map<String, Object> edition,
                         Map<Long, Integer> skills, int experiences, Double credit) {

        /** P 开头的是比赛级题目，其他是通用题目 */
        Object answer(String code) {
            return code.startsWith("P") ? edition.get(code) : general.get(code);
        }
    }

    /**
     * 队伍还在招的一个需求。
     *
     * @param skills 技能 id → 最低熟练度
     */
    public record Need(String roleName, int remaining, Map<Long, Integer> skills) {
    }

    /**
     * 一支队伍。members 里包括队长。
     *
     * @param meetingSlots 例会时段位图，和问卷 G4 的编码一样
     * @param meetingMode  OFFLINE / ONLINE / MIXED
     */
    public record TeamContext(long teamId, long captainId, List<Person> members, List<Need> openNeeds,
                              int meetingSlots, String meetingMode, boolean englishRequired) {

        Person captain() {
            return members.stream().filter(m -> m.userId() == captainId).findFirst().orElse(null);
        }
    }

    /** 和这次打分有关、但不在问卷里的事实 */
    public record Facts(
            /* 和已经参加的另一场比赛撞期时，写那场比赛的名字；不撞期为空 */
            String timeConflictWith,
            /* 被平台暂停推荐 */
            boolean suspended,
            /* 比赛期间有个人日程（标了参与撞期检测的），写日程标题；没有为空 */
            String personalConflictWith) {

        public static final Facts NONE = new Facts(null, false, null);
    }

    /** 生成推荐理由时要用到的文字：题目标题、选项文字、技能名 */
    public interface Labels {

        String title(String questionCode);

        String option(String questionCode, String value);

        String skill(long skillId);
    }
}
