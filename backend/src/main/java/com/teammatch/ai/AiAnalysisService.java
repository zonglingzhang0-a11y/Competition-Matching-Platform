package com.teammatch.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teammatch.ai.AiDtos.AiProfileAnalysis;
import com.teammatch.ai.AiDtos.AiTeamAdvice;
import com.teammatch.ai.AiDtos.ProfileBody;
import com.teammatch.ai.AiDtos.SkillPick;
import com.teammatch.ai.AiDtos.TeamBody;
import com.teammatch.ai.AiFacts.AxisScore;
import com.teammatch.ai.AiFacts.MemberFact;
import com.teammatch.ai.AiFacts.NeedFact;
import com.teammatch.ai.AiFacts.ProfileFacts;
import com.teammatch.ai.AiFacts.SkillFact;
import com.teammatch.ai.AiFacts.TeamFacts;
import com.teammatch.common.BizException;
import com.teammatch.llm.LlmClient;
import com.teammatch.profile.ProfileDtos.Experience;
import com.teammatch.profile.ProfileDtos.ProfileResponse;
import com.teammatch.profile.ProfileDtos.SkillLevel;
import com.teammatch.profile.ProfileDtos.SkillLevelInput;
import com.teammatch.profile.ProfileMappers.SkillMapper;
import com.teammatch.profile.ProfileService;
import com.teammatch.profile.Skill;
import com.teammatch.radar.RadarDtos.Radar;
import com.teammatch.radar.RadarMath;
import com.teammatch.survey.SurveyDtos.Question;
import com.teammatch.survey.SurveyService;
import com.teammatch.team.TeamDtos.MemberView;
import com.teammatch.team.TeamDtos.NeedView;
import com.teammatch.team.TeamDtos.TeamDetail;
import com.teammatch.team.TeamService;
import com.teammatch.team.TeamSupport;
import com.teammatch.user.UserBriefs.UserBrief;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 分析：个人档案的"优势 / 性格 / 建议补的技能"，和队伍招募时的"该找什么样的人"。
 *
 * <p>三条底线：
 * <ol>
 *   <li><b>AI 只是建议</b>：技能要用户自己勾选确认才写进档案；招募建议不会自动处理申请。</li>
 *   <li><b>防编造</b>：模型说的每条优势、性格、技能都要带资料原话，找不到就丢掉（见 {@link AiReplyParser}）。</li>
 *   <li><b>能降级</b>：没配模型、连不上、回答用不了时，退回本地规则（{@link RuleAnalysis}），页面上标明来源。</li>
 * </ol>
 *
 * <p>送出去的内容只有和组队有关的部分：技能、履历、专业、问卷里合作相关的几道题；
 * 邮箱、联系方式、真实姓名、完整问卷答案都不会发给模型。个人分析是本人点的，队伍建议只有队长能看。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private static final String SYSTEM_PROFILE = """
            你是校园竞赛组队平台的能力分析助手。根据学生填写的资料、技能、履历和问卷答案，
            归纳他的优势、和组队有关的性格特点、适合在队里承担的角色，并从给定的技能字典里
            找出资料里提到、但档案技能里还没填的技能。
            规则：
            1. 只根据输入，不要编造。每条优势、性格、技能都要给 evidence：从输入里原样照抄的一句话（不超过 60 字）。
            2. 技能名只能从给定的技能字典里选；level 按证据判断：1 入门、2 熟练、3 精通。
            3. 不评价外貌、家庭、健康、政治宗教等敏感信息；性格只说和合作、分工、沟通有关的。
            4. 只输出一个 JSON 对象，不要输出别的文字、不要用 markdown 代码块。
            格式：
            {"summary":"两三句话总结",
             "strengths":[{"title":"简短标题","detail":"说明","evidence":"资料原话"}],
             "skills":[{"name":"技能字典里的名字","level":2,"evidence":"资料原话"}],
             "personality":[{"trait":"简短特点","evidence":"资料原话"}],
             "axisHints":[{"axis":"CODE","reason":"为什么建议补这一轴"}]}
            axis 只能是 CODE / MODEL / DATA / DESIGN / WRITE / HARDWARE 之一。""";

    private static final String SYSTEM_TEAM = """
            你是竞赛组队平台的招募顾问。下面是一支队伍已有的队员（能力雷达 0–100 分、技能、问卷里体现的合作特点）
            和还开着的招募需求，以及本地算好的差距。请给队长一份招募建议：队伍最缺哪类能力、应该找什么样的人、
            组队前或面试时该问什么。只根据输入，不编造队员经历，不做性格好坏、外貌、家庭这类敏感评价。
            只输出一个 JSON 对象，不要输出别的文字、不要用 markdown 代码块。格式：
            {"summary":"两三句话总结队伍现状和最大的缺口",
             "weakAxes":[{"axis":"CODE","reason":"为什么缺"}],
             "suggestions":["具体建议，最多 4 条"],
             "candidateProfile":["理想的新队员什么样，最多 4 条"],
             "questions":["组队前该问的问题，最多 4 条"]}
            axis 只能是 CODE / MODEL / DATA / DESIGN / WRITE / HARDWARE 之一。""";

    private final AiProperties props;
    private final LlmClient llm;
    private final AiAnalysisMapper mapper;
    private final JsonMapper json;
    private final ProfileService profileService;
    private final SurveyService surveyService;
    private final SkillMapper skillMapper;
    private final TeamService teamService;
    private final TeamSupport teamSupport;

    // ---------- 个人分析 ----------

    /** 最近一次分析，没分析过返回 null */
    public AiProfileAnalysis profile(long userId) {
        AiAnalysis row = find(AiAnalysis.KIND_PROFILE, userId);
        if (row == null) {
            return null;
        }
        try {
            ProfileBody body = json.readValue(row.getPayload(), ProfileBody.class);
            return new AiProfileAnalysis(body, row.getSource(), row.getModel(), row.getCreatedAt());
        } catch (RuntimeException e) {
            log.info("存下来的分析结果读不了，当作没分析过：{}", e.getMessage());
            return null;
        }
    }

    /** 这个功能打开了（app.ai.enabled，要把档案发给模型），模型连接也配好了（app.llm） */
    private boolean useModel() {
        return props.enabled() && llm.ready();
    }

    public AiProfileAnalysis analyzeProfile(long userId) {
        AiProfileAnalysis cached = profile(userId);
        if (useModel() && cached != null && "LLM".equals(cached.source()) && cached.createdAt() != null
                && cached.createdAt().isAfter(LocalDateTime.now().minusSeconds(props.cooldownSeconds()))) {
            return cached;
        }
        ProfileFacts facts = profileFacts(userId);
        Radar radar = personRadar(facts.skills(), facts.major());
        Map<String, String> categories = categories();

        ProfileBody body;
        String source;
        String model = null;
        if (useModel()) {
            try {
                String reply = llm.chat(SYSTEM_PROFILE, profilePrompt(facts, radar, categories), props.maxTokens());
                body = AiReplyParser.parseProfile(reply, facts, categories, json);
                source = "LLM";
                model = llm.model();
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.info("AI 分析没成功，退回本地规则：{}", e.getMessage());
                body = RuleAnalysis.profile(facts, radar);
                source = "RULE";
            }
        } else {
            body = RuleAnalysis.profile(facts, radar);
            source = "RULE";
        }
        LocalDateTime at = save(AiAnalysis.KIND_PROFILE, userId, body, source, model);
        return new AiProfileAnalysis(body, source, model, at);
    }

    /** 用户勾选确认后才把 AI 提的技能写进档案；等级取"已有"和"建议"里高的那个 */
    @Transactional
    public List<SkillLevel> applySkills(long userId, List<SkillPick> picks) {
        if (picks == null || picks.isEmpty()) {
            throw new BizException("没有要加入的技能");
        }
        Map<String, Skill> dict = skillMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(s -> key(s.getName()), s -> s, (a, b) -> a));
        Map<Long, SkillLevelInput> merged = new LinkedHashMap<>();
        for (SkillLevel s : profileService.skills(userId)) {
            merged.put(s.skillId(), new SkillLevelInput(s.skillId(), s.level()));
        }
        for (SkillPick pick : picks.stream().filter(p -> p != null && p.name() != null).toList()) {
            Skill skill = dict.get(key(pick.name()));
            if (skill == null) {
                throw new BizException("技能字典里没有「" + pick.name() + "」，请刷新页面后重试");
            }
            int level = Math.max(1, Math.min(3, pick.level()));
            SkillLevelInput old = merged.get(skill.getId());
            if (old == null) {
                merged.put(skill.getId(), new SkillLevelInput(skill.getId(), level));
            } else if (old.level() < level) {
                merged.put(skill.getId(), new SkillLevelInput(skill.getId(), level));
            }
        }
        if (merged.size() > 20) {
            throw new BizException("技能最多 20 项，先删掉几项再确认");
        }
        return profileService.replaceSkills(userId, new ArrayList<>(merged.values()));
    }

    // ---------- 队伍招募建议 ----------

    /** 最近一次的招募建议，只有队长能看；没分析过返回 null */
    public AiTeamAdvice teamAdvice(long captainId, long teamId) {
        teamSupport.requireCaptain(captainId, teamId);
        AiAnalysis row = find(AiAnalysis.KIND_TEAM, teamId);
        if (row == null) {
            return null;
        }
        try {
            TeamBody body = json.readValue(row.getPayload(), TeamBody.class);
            return new AiTeamAdvice(body, row.getSource(), row.getModel(), row.getCreatedAt());
        } catch (RuntimeException e) {
            log.info("存下来的招募建议读不了，当作没分析过：{}", e.getMessage());
            return null;
        }
    }

    public AiTeamAdvice analyzeTeam(long captainId, long teamId) {
        teamSupport.requireCaptain(captainId, teamId);
        AiTeamAdvice cached = teamAdvice(captainId, teamId);
        if (useModel() && cached != null && "LLM".equals(cached.source()) && cached.createdAt() != null
                && cached.createdAt().isAfter(LocalDateTime.now().minusSeconds(props.cooldownSeconds()))) {
            return cached;
        }
        TeamFacts facts = teamFacts(captainId, teamId);

        TeamBody body;
        String source;
        String model = null;
        if (useModel()) {
            try {
                String reply = llm.chat(SYSTEM_TEAM, teamPrompt(facts), props.maxTokens());
                body = AiReplyParser.parseTeam(reply, facts, json);
                source = "LLM";
                model = llm.model();
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.info("AI 招募建议没成功，退回本地规则：{}", e.getMessage());
                body = RuleAnalysis.team(facts);
                source = "RULE";
            }
        } else {
            body = RuleAnalysis.team(facts);
            source = "RULE";
        }
        LocalDateTime at = save(AiAnalysis.KIND_TEAM, teamId, body, source, model);
        return new AiTeamAdvice(body, source, model, at);
    }

    // ---------- 事实整理 ----------

    private ProfileFacts profileFacts(long userId) {
        ProfileResponse p = profileService.get(userId);
        Map<String, Object> answers = surveyService.generalAnswers(List.of(userId)).getOrDefault(userId, Map.of());
        List<Question> questions = personalityQuestions();
        List<String> lines = SurveyText.summarize(questions, answers);
        List<String> personalityLines = SurveyText.summarize(traitQuestions(questions), answers);
        String selfIntro = answers.get("F1") instanceof String s ? s.strip() : null;
        List<String> experiences = p.experiences().stream().map(AiAnalysisService::experienceText)
                .filter(s -> !s.isBlank()).limit(6).toList();
        return new ProfileFacts(p.nickname(), p.major(), p.degree(), p.bio(), selfIntro,
                skillFacts(p.skills()), experiences, lines.stream().limit(8).toList(),
                personalityLines.stream().limit(5).toList());
    }

    private TeamFacts teamFacts(long captainId, long teamId) {
        TeamDetail d = teamService.detail(captainId, teamId);
        List<Long> memberIds = d.members().stream().map(m -> m.user().id()).toList();
        Map<Long, Map<String, Object>> answers = surveyService.generalAnswers(memberIds);
        List<Question> traits = traitQuestions(personalityQuestions());

        List<MemberFact> members = d.members().stream().map(m -> {
            UserBrief u = m.user();
            List<SkillFact> skills = skillFacts(u.skills());
            Radar radar = personRadar(skills, u.major());
            List<String> memberTraits = SurveyText.summarize(traits, answers.getOrDefault(u.id(), Map.of()))
                    .stream().limit(4).toList();
            return new MemberFact(u.nickname(), skills, memberTraits, axesOf(radar));
        }).toList();

        List<NeedView> open = d.needs().stream().filter(n -> n.status() == com.teammatch.team.TeamNeed.Status.OPEN).toList();
        List<NeedFact> needs = open.stream().map(n -> new NeedFact(n.roleName(), n.headcount(), n.filled(),
                n.note(), n.skills().stream().map(s -> new SkillFact(s.name(), s.category(),
                s.minLevel() == null ? 1 : s.minLevel())).toList())).toList();

        List<RadarMath.Requirement> requirements = open.stream()
                .flatMap(n -> n.skills().stream().map(s -> new RadarMath.Requirement(n.roleName(), s.name(),
                        s.category(), s.minLevel() == null ? 1 : s.minLevel())))
                .toList();
        Radar needRadar = RadarMath.need(requirements, open.size());
        List<AxisScore> needAxes = axesOf(needRadar);

        List<AxisScore> bestAxes = new ArrayList<>();
        for (com.teammatch.radar.RadarAxis axis : com.teammatch.radar.RadarAxis.values()) {
            int best = members.stream().flatMap(m -> m.radar().stream())
                    .filter(a -> a.key().equals(axis.key())).mapToInt(AxisScore::score).max().orElse(0);
            bestAxes.add(new AxisScore(axis.key(), axis.label(), best));
        }

        List<AiDtos.SkillGap> gaps = new ArrayList<>();
        Map<String, Integer> bestSkill = new HashMap<>();
        for (MemberFact m : members) {
            for (SkillFact s : m.skills()) {
                bestSkill.merge(key(s.name()), s.level(), Math::max);
            }
        }
        List<SkillFact> wanted = needs.stream().flatMap(n -> n.skills().stream())
                .collect(Collectors.toMap(s -> key(s.name()), s -> s, (a, b) -> a.level() >= b.level() ? a : b,
                        LinkedHashMap::new)).values().stream().toList();
        for (SkillFact s : wanted) {
            int best = bestSkill.getOrDefault(key(s.name()), 0);
            gaps.add(new AiDtos.SkillGap(s.name(), s.level(), best,
                    best >= s.level() ? "COVERED" : best > 0 ? "PARTIAL" : "MISSING"));
        }
        return new TeamFacts(d.name(), d.edition().competitionName(), d.edition().editionTitle(),
                needs, members, needAxes, bestAxes, gaps);
    }

    private List<Question> personalityQuestions() {
        long versionId = surveyService.activeVersion().getId();
        return surveyService.questions(versionId).stream()
                .filter(q -> SurveyService.PART_GENERAL.equals(q.part()) && !"TEXT".equals(q.type()) && !"PLEDGE".equals(q.type()))
                .toList();
    }

    /** 只取协作、角色这两类题，页面上"合作风格"的标签用这些答案 */
    private static List<Question> traitQuestions(List<Question> questions) {
        return questions.stream()
                .filter(q -> "collab".equals(q.dimension()) || "skill".equals(q.dimension()))
                .toList();
    }

    // ---------- 提示词 ----------

    static String profilePrompt(ProfileFacts facts, Radar radar, Map<String, String> categories) {
        StringBuilder sb = new StringBuilder();
        sb.append("技能字典（技能名只能从这里选）：")
                .append(categories.keySet().stream().limit(90).collect(Collectors.joining("、"))).append('\n');
        sb.append("学生资料：\n");
        line(sb, "专业", facts.major());
        line(sb, "学历", facts.degree());
        line(sb, "简介", facts.bio());
        sb.append("- 已填技能：").append(facts.skills().isEmpty() ? "（还没填）"
                : facts.skills().stream().map(s -> s.name() + "（" + RuleAnalysis.LEVELS[s.level()] + "）")
                .collect(Collectors.joining("、"))).append('\n');
        if (!facts.experiences().isEmpty()) {
            sb.append("- 履历：\n");
            facts.experiences().forEach(e -> sb.append("  - ").append(e).append('\n'));
        }
        if (!facts.surveyLines().isEmpty()) {
            sb.append("- 问卷：\n");
            facts.surveyLines().forEach(l -> sb.append("  - ").append(l).append('\n'));
        }
        line(sb, "自我描述（问卷开放题）", facts.selfIntro());
        if (!radar.axes().isEmpty()) {
            sb.append("- 现在的能力雷达（没填技能的轴是按专业和相关技能估的）：").append(radar.axes().stream()
                    .map(a -> a.label() + " " + a.score()
                            + ("SKILL".equals(a.basis()) ? "" : "（" + a.band() + "）"))
                    .collect(Collectors.joining("、"))).append('\n');
        }
        sb.append("\n请分析这个学生的优势、和组队有关的性格特点、适合的角色，以及资料里提到但技能档案没填的技能。");
        return cut(sb.toString(), 6000);
    }

    static String teamPrompt(TeamFacts facts) {
        StringBuilder sb = new StringBuilder();
        sb.append("队伍：").append(facts.teamName()).append("，参加 ").append(facts.competitionName())
                .append(" · ").append(facts.editionTitle()).append('\n');
        sb.append("队员（共 ").append(facts.members().size()).append(" 人）：\n");
        for (MemberFact m : facts.members()) {
            sb.append("- ").append(m.nickname()).append("：雷达 ")
                    .append(m.radar().stream().filter(a -> a.score() > 0)
                            .map(a -> a.label() + " " + a.score()).collect(Collectors.joining("、")));
            if (m.skills().isEmpty()) {
                sb.append("；还没填技能");
            } else {
                sb.append("；技能 ").append(m.skills().stream()
                        .map(s -> s.name() + "（" + RuleAnalysis.LEVELS[s.level()] + "）")
                        .collect(Collectors.joining("、")));
            }
            if (!m.traits().isEmpty()) {
                sb.append("；问卷 ").append(String.join("；", m.traits()));
            }
            sb.append('\n');
        }
        sb.append("招募需求：\n");
        if (facts.needs().isEmpty()) {
            sb.append("- （还没写）\n");
        }
        for (NeedFact n : facts.needs()) {
            sb.append("- ").append(n.roleName()).append("，还差 ").append(Math.max(0, n.headcount() - n.filled()))
                    .append(" 人");
            if (!n.skills().isEmpty()) {
                sb.append("；要求 ").append(n.skills().stream()
                        .map(s -> s.name() + " ≥ " + RuleAnalysis.LEVELS[s.level()]).collect(Collectors.joining("、")));
            }
            if (n.note() != null && !n.note().isBlank()) {
                sb.append("；备注：").append(n.note());
            }
            sb.append('\n');
        }
        sb.append("队里现有水平（需求 / 队里最高）：\n");
        for (AxisScore need : facts.needAxes()) {
            if (need.score() == 0) {
                continue;
            }
            AxisScore best = facts.bestAxes().stream().filter(b -> b.key().equals(need.key())).findFirst()
                    .orElse(new AxisScore(need.key(), need.label(), 0));
            sb.append("- ").append(need.label()).append("：需求 ").append(need.score())
                    .append(" / 队里最高 ").append(best.score()).append('\n');
        }
        if (!facts.skillGaps().isEmpty()) {
            sb.append("技能差距：\n");
            for (AiDtos.SkillGap g : facts.skillGaps()) {
                sb.append("- ").append(g.name()).append("：要求 ").append(RuleAnalysis.LEVELS[g.minLevel()])
                        .append("，队里最高 ").append(g.bestLevel() == 0 ? "没人会" : RuleAnalysis.LEVELS[g.bestLevel()])
                        .append('\n');
            }
        }
        sb.append("\n请给出招募建议：最缺哪类能力、该找什么样的人、组队前或面试时问什么。");
        return cut(sb.toString(), 6000);
    }

    // ---------- 内部 ----------

    private Map<String, String> categories() {
        return skillMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(Skill::getName, Skill::getCategory, (a, b) -> a, LinkedHashMap::new));
    }

    private List<SkillFact> skillFacts(List<SkillLevel> skills) {
        return skills.stream().map(s -> new SkillFact(s.name(), s.category(), s.level())).toList();
    }

    private static Radar personRadar(List<SkillFact> skills, String major) {
        return RadarMath.person(skills.stream()
                .map(s -> new RadarMath.Entry(s.name(), s.category(), s.level())).toList(), major);
    }

    private static List<AxisScore> axesOf(Radar radar) {
        return radar.axes().stream().map(a -> new AxisScore(a.key(), a.label(), a.score())).toList();
    }

    private AiAnalysis find(String kind, long subjectId) {
        return mapper.selectOne(new LambdaQueryWrapper<AiAnalysis>()
                .eq(AiAnalysis::getKind, kind).eq(AiAnalysis::getSubjectId, subjectId));
    }

    /** 存下最近一次结果，返回分析时间（秒为单位，和库里存的一样） */
    private LocalDateTime save(String kind, long subjectId, Object body, String source, String model) {
        AiAnalysis row = find(kind, subjectId);
        boolean create = row == null;
        if (create) {
            row = new AiAnalysis();
            row.setKind(kind);
            row.setSubjectId(subjectId);
        }
        row.setPayload(json.writeValueAsString(body));
        row.setSource(source);
        row.setModel(model);
        // 重新分析时也要更新：页面上的"分析于"和 60 秒冷却都按最近一次算（库里的默认值只在第一次插入时生效）
        LocalDateTime at = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        row.setCreatedAt(at);
        if (create) {
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
        return at;
    }

    private static String experienceText(Experience e) {
        StringBuilder sb = new StringBuilder();
        if (e.year() != null) {
            sb.append(e.year()).append(" 年 ");
        }
        sb.append(e.title());
        if (e.award() != null && !e.award().isBlank()) {
            sb.append(" ").append(e.award());
        }
        return sb.toString();
    }

    private static String key(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static void line(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("- ").append(label).append("：").append(value).append('\n');
        }
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
