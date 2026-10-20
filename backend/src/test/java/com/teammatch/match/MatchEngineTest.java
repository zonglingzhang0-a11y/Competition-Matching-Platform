package com.teammatch.match;

import com.teammatch.match.MatchInputs.Facts;
import com.teammatch.match.MatchInputs.Labels;
import com.teammatch.match.MatchInputs.Need;
import com.teammatch.match.MatchInputs.Person;
import com.teammatch.match.MatchInputs.TeamContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用 初始数据（db/migration/V2__seed.sql）里真实的匹配配置来测引擎，配置改了测试会跟着用新的。
 */
class MatchEngineTest {

    private static final long PYTHON = 2;
    private static final long VUE = 5;
    /** 周一到周五晚上 */
    private static final int WEEKDAY_EVENINGS = bits(2, 5, 8, 11, 14);

    private static MatchConfig defaults;
    private static MatchConfig shortIntensive;
    private static MatchEngine engine;

    private static final Labels LABELS = new Labels() {
        @Override
        public String title(String code) {
            return code;
        }

        @Override
        public String option(String code, String value) {
            return Map.of("A", "冲国一", "D", "先体验", "internship", "实习", "exam", "考试").getOrDefault(value, value);
        }

        @Override
        public String skill(long id) {
            return id == PYTHON ? "Python" : "技能" + id;
        }
    };

    @BeforeAll
    static void loadConfig() throws Exception {
        String seed = Files.readString(Path.of("src/main/resources/db/migration/V2__seed.sql"));
        JsonMapper json = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        defaults = json.readValue(extract(seed, "DEFAULT"), MatchConfig.class);
        shortIntensive = defaults.applyPreset(json.readValue(extract(seed, "SHORT_INTENSIVE"), MatchConfig.class));
        engine = new MatchEngine(defaults, LABELS);
    }

    private static String extract(String seed, String preset) {
        Matcher m = Pattern.compile("\\(@v, '" + preset + "', '(\\{.*?\\})'\\)", Pattern.DOTALL).matcher(seed);
        assertThat(m.find()).as("V2__seed.sql 里找到 " + preset + " 配置").isTrue();
        return m.group(1);
    }

    @Test
    void defaultWeightsSumToOne() {
        assertThat(defaults.dimensions().stream().mapToDouble(MatchConfig.Dimension::weight).sum()).isEqualTo(1.0, within());
        defaults.dimensions().forEach(d ->
                assertThat(d.items().stream().mapToDouble(MatchConfig.Item::weight).sum()).as(d.key()).isEqualTo(1.0, within()));
    }

    @Test
    void presetOverridesWeightsAndMatrix() {
        assertThat(weight(shortIntensive, "time")).isEqualTo(0.30);
        assertThat(weight(shortIntensive, "collab")).isEqualTo(0.10);
        assertThat(g2(defaults).get(3).get(3)).isEqualTo(0.3);
        assertThat(g2(shortIntensive).get(3).get(3)).isEqualTo(0.2);
        assertThat(g2(defaults).get(3).get(3)).as("默认配置没被改动").isEqualTo(0.3);
    }

    @Test
    void idealCandidateScoresHighWithReasons() {
        MatchResult r = engine.score(ideal(), team(), Facts.NONE, true);

        assertThat(r.veto()).isNull();
        assertThat(r.score()).isGreaterThan(85);
        assertThat(r.k()).isEqualTo(1.0);
        assertThat(r.completeness()).isGreaterThan(0.9);
        assertThat(r.reasons()).hasSizeBetween(1, 3);
        assertThat(String.join("|", r.reasons())).containsAnyOf("能补上「编程」需要的 Python", "你想做的「编程」正是队伍在招的");
        assertThat(r.talkAbout()).isNull();
    }

    @Test
    void worseFitScoresLower() {
        Person poor = person(9, Map.of("G2", "D", "G3", "C", "G4", bits(18, 19), "G5", 24, "G6", "D", "G7", "D", "G8", "A"),
                Map.of("P1", "C", "P2", 2, "P4", List.of("论文写作与排版")), Map.of(VUE, 1));
        MatchResult good = engine.score(ideal(), team(), Facts.NONE, true);
        MatchResult bad = engine.score(poor, team(), Facts.NONE, true);
        assertThat(bad.score()).isLessThan(good.score() - 20);
        assertThat(bad.talkAbout()).startsWith("组队前先聊聊：");
    }

    @Test
    void conflictingGoalsAreVetoed() {
        Person casual = person(9, Map.of(), Map.of("P1", "D"), Map.of());
        MatchResult r = engine.score(casual, team(), Facts.NONE, true);
        assertThat(r.veto()).isEqualTo("目标差得太远：你想「先体验」，队伍里有人想「冲国一」");
        assertThat(r.score()).isZero();
    }

    @Test
    void timeConflictIsVetoed() {
        MatchResult r = engine.score(ideal(), team(), new Facts("某创新大赛", false, null), false);
        assertThat(r.veto()).isEqualTo("比赛时间和TA已经参加的「某创新大赛」重叠");
    }

    @Test
    void personalConflictLowersScoreButDoesNotVeto() {
        MatchResult clash = engine.score(ideal(), team(), new Facts(null, false, "期中考试"), true);
        MatchResult free = engine.score(ideal(), team(), Facts.NONE, true);
        assertThat(clash.veto()).isNull();
        assertThat(clash.warnings()).contains("比赛期间你有个人日程「期中考试」");
        assertThat(clash.score()).isLessThan(free.score());
    }

    @Test
    void knownConstraintsLowerCommitment() {
        Map<String, Object> edition = new HashMap<>(ideal().edition());
        edition.put("P5", List.of("internship", "exam"));
        Person busy = person(8, ideal().general(), edition, ideal().skills());
        MatchResult r = engine.score(busy, team(), Facts.NONE, true);
        // 队伍目标是冲国一（高目标），每项占用扣 0.1
        assertThat(r.k()).isEqualTo(0.8);
        assertThat(r.warnings()).contains("你备赛期间有「实习」「考试」，投入可能受影响");
        assertThat(r.score()).isLessThan(engine.score(ideal(), team(), Facts.NONE, true).score());
    }

    @Test
    void onlineOnlyAgainstOfflineTeamIsPenalised() {
        Map<String, Object> edition = new HashMap<>(ideal().edition());
        edition.put("P5", List.of("online_only"));
        Person remote = person(8, ideal().general(), edition, ideal().skills());
        TeamContext offline = new TeamContext(1, 1, team().members(), team().openNeeds(), 0, "OFFLINE", false);
        MatchResult r = engine.score(remote, offline, Facts.NONE, false);
        assertThat(r.warnings()).contains("TA只能线上参与，你们队以线下为主");
    }

    @Test
    void missingAnswersStillScoreWithLowCompleteness() {
        Person onlySkills = person(7, Map.of(), Map.of(), Map.of(PYTHON, 3));
        MatchResult r = engine.score(onlySkills, team(), Facts.NONE, true);
        assertThat(r.veto()).isNull();
        assertThat(r.completeness()).isLessThan(0.3);
        assertThat(r.dimensions()).anyMatch(d -> d.score() == null);
        // 数据少，分数被收向中间
        assertThat(r.score()).isBetween(45.0, 65.0);
    }

    @Test
    void sparseDataNeverOutranksAGoodFullProfile() {
        Person nothing = person(6, Map.of(), Map.of(), Map.of());
        MatchResult empty = engine.score(nothing, team(), Facts.NONE, true);
        assertThat(empty.score()).isEqualTo(50.0);
        assertThat(empty.warnings()).contains("你还没填问卷，分数只供参考");
        assertThat(empty.score()).isLessThan(engine.score(ideal(), team(), Facts.NONE, true).score());
    }

    // ---------- 测试数据 ----------

    /** 队长：冲国一，晚上有空，队里在招"编程"（要求 Python 熟练） */
    private static TeamContext team() {
        Person captain = person(1,
                map("G1", List.of("interest", "postgrad"), "G2", "B", "G3", "B", "G4", WEEKDAY_EVENINGS, "G5", 2,
                        "G6", "B", "G7", "B", "G8", "B", "G9", List.of("shaper", "coordinator"), "G10", "B",
                        "G11", List.of("missing", "freeride", "sloppy")),
                Map.of("P1", "A", "P2", 12, "P3", "B", "P6", List.of("by_contribution", "pre_agreed"),
                        "P7", List.of("shared_doc", "git")),
                Map.of(VUE, 3));
        return new TeamContext(1, 1, List.of(captain), List.of(new Need("编程", 1, Map.of(PYTHON, 2))),
                0, "MIXED", false);
    }

    /** 和队长几乎一样的理想人选，会 Python，想做编程 */
    private static Person ideal() {
        return person(2,
                map("G1", List.of("interest", "postgrad"), "G2", "B", "G3", "B", "G4", WEEKDAY_EVENINGS, "G5", 2,
                        "G6", "B", "G7", "B", "G8", "B", "G9", List.of("implementer", "finisher"), "G10", "C",
                        "G11", List.of("missing", "freeride", "sloppy")),
                Map.of("P1", "A", "P2", 12, "P3", "B", "P4", List.of("编程", "论文写作与排版"),
                        "P5", List.of("none"), "P6", List.of("by_contribution"), "P7", List.of("git")),
                Map.of(PYTHON, 3));
    }

    private static Person person(long id, Map<String, Object> general, Map<String, Object> edition, Map<Long, Integer> skills) {
        return new Person(id, "u" + id, general, edition, skills, 0, null);
    }

    /** Map.of 最多 10 对，通用问卷有 11 题 */
    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static int bits(int... positions) {
        int b = 0;
        for (int p : positions) {
            b |= 1 << p;
        }
        return b;
    }

    private static double weight(MatchConfig c, String key) {
        return c.dimensions().stream().filter(d -> d.key().equals(key)).findFirst().orElseThrow().weight();
    }

    private static List<List<Double>> g2(MatchConfig c) {
        return c.dimensions().stream().flatMap(d -> d.items().stream()).filter(i -> "G2".equals(i.q()))
                .findFirst().orElseThrow().matrix();
    }

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(1e-9);
    }
}
