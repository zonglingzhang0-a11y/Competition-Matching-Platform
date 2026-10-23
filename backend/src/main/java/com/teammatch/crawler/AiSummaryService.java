package com.teammatch.crawler;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teammatch.common.BizException;
import com.teammatch.competition.Competition;
import com.teammatch.competition.CompetitionEdition;
import com.teammatch.competition.CompetitionMappers.CompetitionMapper;
import com.teammatch.competition.CompetitionMappers.EditionMapper;
import com.teammatch.crawler.AiSummaryDtos.Channel;
import com.teammatch.crawler.AiSummaryDtos.PointView;
import com.teammatch.crawler.AiSummaryDtos.SourceView;
import com.teammatch.crawler.AiSummaryDtos.Summary;
import com.teammatch.crawler.AiSummaryDtos.View;
import com.teammatch.crawler.AiSummaryParser.Point;
import com.teammatch.crawler.AiSummaryParser.Source;
import com.teammatch.crawler.CrawlEntities.CrawlRecord;
import com.teammatch.crawler.CrawlEntities.CrawlRecordMapper;
import com.teammatch.crawler.CrawlEntities.CrawlSource;
import com.teammatch.crawler.CrawlEntities.CrawlSourceMapper;
import com.teammatch.crawler.CrawlEntities.RecordStatus;
import com.teammatch.llm.LlmClient;
import com.teammatch.recognition.RecognitionService;
import com.teammatch.school.School;
import com.teammatch.school.SchoolMapper;
import com.teammatch.user.SysUser;
import com.teammatch.user.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 比赛页的 AI 总结：把平台抓到的这个比赛的官方通知交给大模型，整理成几条要点，每条都核对过原文（见 {@link AiSummaryParser}）。
 * <p>
 * 管理员点"生成"才调大模型，结果存起来；学生打开页面只是读存好的。来源网站不希望内容被 AI 使用的（allow_llm = 0），
 * 通知不交给大模型，只在页面上列出链接。页面上同时提醒信息可能不全，并列出更多信息渠道。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSummaryService {

    /** 页面上列出最近的几篇通知 */
    private static final int RECORDS_SHOWN = 10;
    /** 最多交给大模型几篇（本机模型输入别太长） */
    private static final int RECORDS_TO_LLM = 4;
    /** 这几项没总结出来时，提醒去别的渠道查 */
    private static final List<String> IMPORTANT = List.of("报名时间", "比赛时间", "组队要求");

    private final CompetitionMapper competitionMapper;
    private final EditionMapper editionMapper;
    private final CrawlSourceMapper sourceMapper;
    private final CrawlRecordMapper recordMapper;
    private final SummaryMapper summaryMapper;
    private final ChannelMapper channelMapper;
    private final UserService userService;
    private final SchoolMapper schoolMapper;
    private final RecognitionService recognitionService;
    private final LlmClient llm;
    private final JsonMapper json;
    private final Clock clock;
    /** 本机模型一次只处理一个请求：同一时间只整理一个比赛 */
    private final AtomicBoolean generating = new AtomicBoolean();

    public View view(long competitionId, long userId, boolean admin) {
        Competition c = requireCompetition(competitionId);
        Map<Long, CrawlSource> sources = sources(competitionId);
        List<CrawlRecord> records = records(sources.keySet());
        AiSummary saved = summaryMapper.selectById(competitionId);

        Summary summary = null;
        List<String> missing = List.of();
        Set<Long> used = Set.of();
        if (saved != null) {
            List<Point> points = json.readValue(saved.getPoints(), new TypeReference<List<Point>>() {
            });
            used = new HashSet<>(json.readValue(saved.getRecordIds(), new TypeReference<List<Long>>() {
            }));
            Set<Long> ids = points.stream().map(Point::recordId).collect(Collectors.toSet());
            Map<Long, CrawlRecord> byId = ids.isEmpty() ? Map.of()
                    : recordMapper.selectByIds(ids).stream().collect(Collectors.toMap(CrawlRecord::getId, Function.identity()));
            summary = new Summary(points.stream().map(p -> {
                CrawlRecord r = byId.get(p.recordId());
                return new PointView(p.label(), p.text(), p.quote(), p.recordId(),
                        r == null ? null : r.getTitle(), r == null ? null : r.getSourceUrl());
            }).toList(), saved.getModel(), saved.getGeneratedAt());
            Set<String> labels = points.stream().map(Point::label).collect(Collectors.toSet());
            missing = IMPORTANT.stream().filter(l -> !labels.contains(l)).toList();
        }
        Set<Long> usedIds = used;
        List<SourceView> sourceViews = records.stream().map(r -> new SourceView(r.getId(), r.getTitle(), r.getSourceUrl(),
                r.getFetchedAt(), aiAllowed(sources.get(r.getSourceId())), usedIds.contains(r.getId()))).toList();
        return new View(llm.ready(), admin, summary, missing, sourceViews, channels(c, sources.values(), userId));
    }

    /** 管理员点"生成"：调大模型，核对通过的要点存起来；失败时保留原来的总结 */
    public View generate(long adminId, long competitionId) {
        Competition c = requireCompetition(competitionId);
        if (!llm.ready()) {
            throw new BizException(400, "后端还没打开大模型：在配置里打开 app.llm.enabled，填好接口地址");
        }
        Map<Long, CrawlSource> sources = sources(competitionId);
        List<Source> input = records(sources.keySet()).stream()
                .filter(r -> aiAllowed(sources.get(r.getSourceId())) && r.getRawText() != null && !r.getRawText().isBlank())
                .limit(RECORDS_TO_LLM)
                .map(r -> new Source(r.getId(), r.getTitle() == null ? "" : r.getTitle(), r.getRawText()))
                .toList();
        if (input.isEmpty()) {
            throw new BizException(400, "还没有抓到这个比赛能交给 AI 的通知：先去「管理后台 → 通知抓取」抓一次"
                    + "（网站不希望内容被 AI 使用的不会交给 AI）");
        }
        if (!generating.compareAndSet(false, true)) {
            throw new BizException(409, "大模型正在整理别的比赛，等这次完成再试");
        }
        String answer;
        try {
            answer = llm.chat(AiSummaryParser.SYSTEM, AiSummaryParser.userPrompt(c.getName(), input, llm.maxChars()), 1500);
        } catch (IOException e) {
            log.info("AI 总结：大模型请求失败：{}", e.getMessage());
            throw new BizException(502, "大模型没有回应：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(503, "生成被中断了，再试一次");
        } finally {
            generating.set(false);
        }
        List<Point> points;
        try {
            points = AiSummaryParser.parse(answer, input, json);
        } catch (IllegalArgumentException e) {
            log.info("AI 总结：大模型的回答用不了：{}", e.getMessage());
            points = List.of();
        }
        if (points.isEmpty()) {
            throw new BizException(502, "大模型的回答里没有能和原文对上的要点，原来的总结没动，过一会儿再试一次");
        }
        AiSummary s = new AiSummary();
        s.setCompetitionId(competitionId);
        s.setPoints(json.writeValueAsString(points));
        s.setRecordIds(json.writeValueAsString(input.stream().map(Source::recordId).toList()));
        s.setModel(llm.model());
        s.setGeneratedAt(LocalDateTime.now(clock));
        s.setGeneratedBy(adminId);
        if (summaryMapper.selectById(competitionId) == null) {
            summaryMapper.insert(s);
        } else {
            summaryMapper.updateById(s);
        }
        return view(competitionId, adminId, true);
    }

    private Competition requireCompetition(long competitionId) {
        Competition c = competitionMapper.selectById(competitionId);
        if (c == null) {
            throw new BizException(404, "这个比赛不存在或已下架");
        }
        return c;
    }

    private Map<Long, CrawlSource> sources(long competitionId) {
        return sourceMapper.selectList(new LambdaQueryWrapper<CrawlSource>().eq(CrawlSource::getCompetitionId, competitionId))
                .stream().collect(Collectors.toMap(CrawlSource::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    /** 最近抓到的通知，自动忽略的（往年的旧通知）和管理员忽略的不算 */
    private List<CrawlRecord> records(Set<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return List.of();
        }
        return recordMapper.selectList(new LambdaQueryWrapper<CrawlRecord>()
                .in(CrawlRecord::getSourceId, sourceIds)
                .ne(CrawlRecord::getStatus, RecordStatus.IGNORED)
                .orderByDesc(CrawlRecord::getFetchedAt)
                .orderByDesc(CrawlRecord::getId)
                .last("LIMIT " + RECORDS_SHOWN));
    }

    private static boolean aiAllowed(CrawlSource s) {
        return s != null && Boolean.TRUE.equals(s.getAllowLlm());
    }

    /**
     * 更多信息渠道：官网、各届的官方通知、平台抓取的通知列表页（同一个网址只列一次），
     * 再加上按"我"和平台数据写实的四条：本校教务处（直接搜）、公众号（复制全称）、往届参赛的同学、本校的加分认定。
     */
    private List<Channel> channels(Competition c, Iterable<CrawlSource> sources, long userId) {
        List<Channel> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (c.getOfficialUrl() != null && seen.add(sameSite(c.getOfficialUrl()))) {
            out.add(Channel.link("比赛官网", c.getOfficialUrl(), "最新通知、报名入口、往届赛题"));
        }
        editionMapper.selectList(new LambdaQueryWrapper<CompetitionEdition>()
                        .eq(CompetitionEdition::getCompetitionId, c.getId())
                        .eq(CompetitionEdition::getStatus, CompetitionEdition.Status.PUBLISHED)
                        .isNotNull(CompetitionEdition::getNoticeUrl)
                        .orderByDesc(CompetitionEdition::getYear))
                .forEach(e -> {
                    if (seen.add(sameSite(e.getNoticeUrl()))) {
                        out.add(Channel.link(e.getTitle() + "的官方通知", e.getNoticeUrl(), null));
                    }
                });
        for (CrawlSource s : sources) {
            if (seen.add(sameSite(s.getListUrl()))) {
                out.add(Channel.link(s.getName(), s.getListUrl(), "平台定期从这里抓新通知"));
            }
        }

        SysUser me = userService.getById(userId);
        School school = me.getSchoolId() == null || !userService.isVerified(me) ? null : schoolMapper.selectById(me.getSchoolId());
        String schoolName = school == null ? null : school.getName();

        out.add(Channel.link(schoolName == null ? "学校教务处、学院的竞赛通知" : schoolName + "教务处、学院的竞赛通知",
                search((schoolName == null ? "" : schoolName + " ") + c.getName() + " 校赛 通知"),
                "校赛选拔、报名入口和本校的认定办法常在这里，点开直接搜" + (schoolName == null ? "（认证学校后会搜本校的）" : "")));

        out.add(new Channel("比赛的官方微信公众号", null,
                "在微信里搜「" + c.getName() + "」，赛题、答疑、延期的消息常常先发在这里", c.getName(), null));

        PastRow past = channelMapper.pastParticipants(c.getId(), userId, me.getSchoolId());
        long total = past == null || past.total() == null ? 0 : past.total();
        long mine = past == null || past.sameSchool() == null ? 0 : past.sameSchool();
        out.add(total == 0
                ? new Channel("往届参赛的学长学姐、指导老师", null, "平台上还没有这个比赛往届的参赛记录，可以问问身边参加过的人", null, null)
                : new Channel("平台上参加过往届的同学", null, total + " 人参加完了往届比赛" + (schoolName == null ? "" : "（本校 " + mine + " 人）")
                        + "，在本页往届的队伍里能找到他们，问问备赛经验", null, "editions"));

        String label = school == null ? null : recognitionService.labelsForSchool(school.getId(), List.of(c.getId())).get(c.getId());
        String recognitionHint = school == null ? "看各校同学标注的认定级别"
                : label != null ? "本校同学确认过：「" + label + "」（以学校当年的文件为准）"
                : "本校还没有确认过的认定，知道的话可以去标注";
        out.add(new Channel("本页的「加分认定」", null, recognitionHint, null, "recognition"));
        return out;
    }

    /** 判断是不是同一个网址：不管 http / https、www 和末尾的斜杠 */
    static String sameSite(String url) {
        return url.strip().toLowerCase().replaceFirst("^https?://", "").replaceFirst("^www\\.", "").replaceAll("/+$", "");
    }

    private static String search(String keywords) {
        return "https://www.baidu.com/s?wd=" + URLEncoder.encode(keywords, StandardCharsets.UTF_8);
    }

    /** competition_ai_summary 的一行 */
    @Data
    @TableName("competition_ai_summary")
    public static class AiSummary {
        @TableId(type = IdType.INPUT)
        private Long competitionId;
        private String points;
        private String recordIds;
        private String model;
        private LocalDateTime generatedAt;
        private Long generatedBy;
    }

    @Mapper
    public interface SummaryMapper extends BaseMapper<AiSummary> {
    }

    @Mapper
    public interface ChannelMapper {

        /** 参加完往届的人数（队伍标记了比赛结束、人还在队里），不算自己；另数和我同校的 */
        @Select("""
                SELECT COUNT(DISTINCT m.user_id) AS total,
                       COUNT(DISTINCT CASE WHEN u.school_id = #{schoolId} THEN m.user_id END) AS sameSchool
                FROM team_member m
                JOIN team t ON t.id = m.team_id AND t.deleted = 0 AND t.status = 'FINISHED'
                JOIN competition_edition e ON e.id = t.edition_id AND e.competition_id = #{competitionId}
                JOIN sys_user u ON u.id = m.user_id
                WHERE m.status = 'ACTIVE' AND m.user_id <> #{userId}
                """)
        PastRow pastParticipants(@Param("competitionId") long competitionId, @Param("userId") long userId,
                                 @Param("schoolId") Long schoolId);
    }

    public record PastRow(Long total, Long sameSchool) {
    }
}
