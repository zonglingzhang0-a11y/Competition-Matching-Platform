package com.teammatch.survey;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teammatch.common.BizException;
import com.teammatch.competition.Competition;
import com.teammatch.competition.CompetitionEdition;
import com.teammatch.competition.CompetitionMappers.CompetitionMapper;
import com.teammatch.competition.CompetitionMappers.EditionMapper;
import com.teammatch.competition.CompetitionMappers.RoleTemplateItemMapper;
import com.teammatch.competition.RoleTemplateItem;
import com.teammatch.survey.SurveyDtos.AnswersForm;
import com.teammatch.survey.SurveyDtos.EditionAnswers;
import com.teammatch.survey.SurveyDtos.MyAnswers;
import com.teammatch.survey.SurveyDtos.Option;
import com.teammatch.survey.SurveyDtos.Progress;
import com.teammatch.survey.SurveyDtos.Question;
import com.teammatch.survey.SurveyDtos.Survey;
import com.teammatch.survey.SurveyEntities.QuestionMapper;
import com.teammatch.survey.SurveyEntities.ResponseMapper;
import com.teammatch.survey.SurveyEntities.SurveyQuestion;
import com.teammatch.survey.SurveyEntities.SurveyResponse;
import com.teammatch.survey.SurveyEntities.SurveyVersion;
import com.teammatch.survey.SurveyEntities.VersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 问卷：题目、答案的读写，以及给匹配引擎批量提供答案。
 */
@Service
@RequiredArgsConstructor
public class SurveyService {

    public static final String PART_GENERAL = "GENERAL";
    public static final String PART_COMPETITION = "COMPETITION";

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<Option>> OPTIONS = new TypeReference<>() {
    };

    private final VersionMapper versionMapper;
    private final QuestionMapper questionMapper;
    private final ResponseMapper responseMapper;
    private final EditionMapper editionMapper;
    private final CompetitionMapper competitionMapper;
    private final RoleTemplateItemMapper roleItemMapper;
    private final JsonMapper json;
    private final Clock clock;

    public SurveyVersion activeVersion() {
        SurveyVersion v = versionMapper.selectOne(new LambdaQueryWrapper<SurveyVersion>()
                .eq(SurveyVersion::getActive, true)
                .orderByDesc(SurveyVersion::getId)
                .last("LIMIT 1"));
        if (v == null) {
            throw new BizException(500, "问卷还没配置好，请联系管理员");
        }
        return v;
    }

    public List<Question> questions(long versionId) {
        return questionMapper.selectList(new LambdaQueryWrapper<SurveyQuestion>()
                        .eq(SurveyQuestion::getVersionId, versionId)
                        .orderByAsc(SurveyQuestion::getSort))
                .stream().map(this::toQuestion).toList();
    }

    public Survey survey() {
        SurveyVersion v = activeVersion();
        return new Survey(v.getCode(), questions(v.getId()));
    }

    // ---------- 通用部分 ----------

    public MyAnswers general(long userId) {
        return toMine(findResponse(userId, activeVersion().getId(), null));
    }

    public void saveGeneral(long userId, AnswersForm form) {
        SurveyVersion v = activeVersion();
        List<Question> qs = part(questions(v.getId()), PART_GENERAL);
        Map<String, Object> cleaned = AnswerValidator.validate(qs, form.answers(), form.quick(), List.of());
        upsert(userId, v.getId(), null, cleaned, form.quick(), false);
    }

    // ---------- 比赛级部分 ----------

    public EditionAnswers edition(long userId, long editionId) {
        CompetitionEdition e = requireEdition(editionId);
        Competition c = competitionMapper.selectById(e.getCompetitionId());
        return new EditionAnswers(e.getId(), c == null ? "" : c.getName(), e.getTitle(), roles(c),
                toMine(findResponse(userId, activeVersion().getId(), editionId)));
    }

    public void saveEdition(long userId, long editionId, AnswersForm form) {
        CompetitionEdition e = requireEdition(editionId);
        Competition c = competitionMapper.selectById(e.getCompetitionId());
        SurveyVersion v = activeVersion();
        List<Question> qs = part(questions(v.getId()), PART_COMPETITION);
        Map<String, Object> cleaned = AnswerValidator.validate(qs, form.answers(), form.quick(), roles(c));
        upsert(userId, v.getId(), editionId, cleaned, form.quick(), Boolean.TRUE.equals(cleaned.get("PLEDGE")));
    }

    public Progress progress(long userId) {
        List<SurveyResponse> rows = responseMapper.selectList(new LambdaQueryWrapper<SurveyResponse>()
                .eq(SurveyResponse::getUserId, userId)
                .eq(SurveyResponse::getVersionId, activeVersion().getId()));
        SurveyResponse general = rows.stream().filter(r -> r.getEditionId() == null).findFirst().orElse(null);
        return new Progress(general != null, general != null && Boolean.TRUE.equals(general.getQuick()),
                rows.stream().map(SurveyResponse::getEditionId).filter(id -> id != null).toList());
    }

    // ---------- 给匹配引擎用 ----------

    /** 一批人的通用部分答案；没填的人不在结果里 */
    public Map<Long, Map<String, Object>> generalAnswers(Collection<Long> userIds) {
        return answersOf(userIds, null);
    }

    /** 一批人在某一届的比赛级答案；没填的人不在结果里 */
    public Map<Long, Map<String, Object>> editionAnswers(Collection<Long> userIds, long editionId) {
        return answersOf(userIds, editionId);
    }

    // ---------- 内部 ----------

    private Map<Long, Map<String, Object>> answersOf(Collection<Long> userIds, Long editionId) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return responseMapper.selectList(new LambdaQueryWrapper<SurveyResponse>()
                        .in(SurveyResponse::getUserId, userIds)
                        .eq(SurveyResponse::getVersionId, activeVersion().getId())
                        .isNull(editionId == null, SurveyResponse::getEditionId)
                        .eq(editionId != null, SurveyResponse::getEditionId, editionId))
                .stream().collect(Collectors.toMap(SurveyResponse::getUserId, r -> parseMap(r.getAnswers())));
    }

    private void upsert(long userId, long versionId, Long editionId, Map<String, Object> answers, boolean quick,
                        boolean pledged) {
        LocalDateTime now = LocalDateTime.now(clock);
        SurveyResponse existing = findResponse(userId, versionId, editionId);
        SurveyResponse r = existing != null ? existing : new SurveyResponse();
        r.setUserId(userId);
        r.setVersionId(versionId);
        r.setEditionId(editionId);
        r.setAnswers(json.writeValueAsString(answers));
        r.setQuick(quick);
        if (pledged && r.getPledgedAt() == null) {
            r.setPledgedAt(now);
        }
        r.setSubmittedAt(now);
        if (existing == null) {
            responseMapper.insert(r);
        } else {
            responseMapper.updateById(r);
        }
    }

    private SurveyResponse findResponse(long userId, long versionId, Long editionId) {
        return responseMapper.selectOne(new LambdaQueryWrapper<SurveyResponse>()
                .eq(SurveyResponse::getUserId, userId)
                .eq(SurveyResponse::getVersionId, versionId)
                .isNull(editionId == null, SurveyResponse::getEditionId)
                .eq(editionId != null, SurveyResponse::getEditionId, editionId));
    }

    private MyAnswers toMine(SurveyResponse r) {
        return r == null ? null : new MyAnswers(parseMap(r.getAnswers()), Boolean.TRUE.equals(r.getQuick()),
                r.getSubmittedAt(), r.getPledgedAt());
    }

    private Question toQuestion(SurveyQuestion q) {
        List<Option> options = q.getOptions() == null ? null : json.readValue(q.getOptions(), OPTIONS);
        Map<String, Object> settings = q.getSettings() == null ? Map.of() : parseMap(q.getSettings());
        return new Question(q.getCode(), q.getPart(), q.getDimension(), q.getType(), q.getTitle(), q.getNote(),
                options, settings, Boolean.TRUE.equals(q.getRequired()), Boolean.TRUE.equals(q.getInQuick()));
    }

    private List<String> roles(Competition c) {
        if (c == null || c.getRoleTemplateId() == null) {
            return List.of();
        }
        return roleItemMapper.selectList(new LambdaQueryWrapper<RoleTemplateItem>()
                        .eq(RoleTemplateItem::getTemplateId, c.getRoleTemplateId())
                        .orderByAsc(RoleTemplateItem::getSort))
                .stream().map(RoleTemplateItem::getName).toList();
    }

    private CompetitionEdition requireEdition(long editionId) {
        CompetitionEdition e = editionMapper.selectById(editionId);
        if (e == null || e.getStatus() != CompetitionEdition.Status.PUBLISHED) {
            throw new BizException(404, "这一届比赛不存在或还没发布");
        }
        return e;
    }

    private Map<String, Object> parseMap(String text) {
        return text == null ? Map.of() : json.readValue(text, MAP);
    }

    static List<Question> part(List<Question> all, String part) {
        return all.stream().filter(q -> part.equals(q.part())).toList();
    }
}
