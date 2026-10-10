package com.teammatch.match;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teammatch.common.BizException;
import com.teammatch.competition.Competition;
import com.teammatch.match.MatchInputs.Facts;
import com.teammatch.match.MatchInputs.Labels;
import com.teammatch.match.MatchInputs.Need;
import com.teammatch.match.MatchInputs.Person;
import com.teammatch.match.MatchInputs.TeamContext;
import com.teammatch.survey.SurveyDtos.Option;
import com.teammatch.survey.SurveyDtos.Question;
import com.teammatch.survey.SurveyEntities.MatchConfigMapper;
import com.teammatch.survey.SurveyEntities.MatchConfigRow;
import com.teammatch.survey.SurveyEntities.SurveyVersion;
import com.teammatch.survey.SurveyService;
import com.teammatch.user.SysUser;
import com.teammatch.user.SysUserMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员查看和修改匹配配置（当前问卷版本的三套：默认、短期集中型、长周期型）。
 * <p>
 * 保存前先检查字段，再拿一组按问卷选项编出来的答案试算一次，能算出分数才写库，写完清掉配置缓存。
 */
@Service
@RequiredArgsConstructor
public class MatchConfigAdminService {

    private final MatchConfigMapper mapper;
    private final SurveyService surveyService;
    private final MatchConfigService configService;
    private final SysUserMapper userMapper;
    private final JsonMapper json;
    private final Clock clock;

    public record PresetConfig(Competition.MatchPreset preset, String config, LocalDateTime updatedAt, String updatedBy) {
    }

    /** 配置页要显示题目标题和选项文字，所以把题目也带上 */
    public record QuestionRef(String code, String part, String type, String title, List<Option> options) {
    }

    public record ConfigPage(String versionName, List<QuestionRef> questions, List<PresetConfig> presets,
                             List<String> scorers) {
    }

    public record ConfigForm(@NotBlank(message = "配置不能为空") String config) {
    }

    public ConfigPage page() {
        SurveyVersion v = surveyService.activeVersion();
        List<QuestionRef> questions = surveyService.questions(v.getId()).stream()
                .map(q -> new QuestionRef(q.code(), q.part(), q.type(), q.title(), q.options())).toList();
        Map<String, MatchConfigRow> rows = rows(v.getId());
        Set<Long> editorIds = rows.values().stream().map(MatchConfigRow::getUpdatedBy).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> editors = editorIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(editorIds).stream().collect(Collectors.toMap(SysUser::getId, SysUser::getNickname));
        List<PresetConfig> presets = Arrays.stream(Competition.MatchPreset.values()).map(p -> {
            MatchConfigRow r = rows.get(p.name());
            return r == null ? new PresetConfig(p, null, null, null)
                    : new PresetConfig(p, r.getConfig(), r.getUpdatedAt(),
                    r.getUpdatedBy() == null ? null : editors.get(r.getUpdatedBy()));
        }).toList();
        return new ConfigPage(v.getName(), questions, presets, MatchConfigValidator.SCORERS);
    }

    @Transactional
    public void save(long adminId, Competition.MatchPreset preset, String raw) {
        SurveyVersion v = surveyService.activeVersion();
        List<Question> questions = surveyService.questions(v.getId());
        Set<String> codes = questions.stream().map(Question::code).collect(Collectors.toSet());
        MatchConfig parsed = parse(raw);
        Map<String, MatchConfigRow> rows = rows(v.getId());

        MatchConfig merged;
        if (preset == Competition.MatchPreset.DEFAULT) {
            MatchConfigValidator.validateDefault(parsed, codes);
            // 默认配置改了（比如删了一个维度），预设可能就对不上了
            for (Competition.MatchPreset other : Competition.MatchPreset.values()) {
                MatchConfigRow r = rows.get(other.name());
                if (other == preset || r == null) {
                    continue;
                }
                try {
                    MatchConfigValidator.validatePreset(parsed, parse(r.getConfig()));
                } catch (BizException e) {
                    throw new BizException("这样改以后，预设 " + other + " 会对不上：" + e.getMessage());
                }
            }
            merged = parsed;
        } else {
            MatchConfigRow d = rows.get(Competition.MatchPreset.DEFAULT.name());
            if (d == null) {
                throw new BizException("请先保存默认配置");
            }
            MatchConfig defaults = parse(d.getConfig());
            MatchConfigValidator.validatePreset(defaults, parsed);
            merged = defaults.applyPreset(parsed);
        }
        tryRun(merged, questions);

        MatchConfigRow row = rows.get(preset.name());
        if (row == null) {
            row = new MatchConfigRow();
            row.setVersionId(v.getId());
            row.setPreset(preset.name());
        }
        row.setConfig(json.writeValueAsString(json.readTree(raw)));
        row.setUpdatedBy(adminId);
        row.setUpdatedAt(LocalDateTime.now(clock));
        if (row.getId() == null) {
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
        configService.evict();
    }

    private Map<String, MatchConfigRow> rows(long versionId) {
        return mapper.selectList(new LambdaQueryWrapper<MatchConfigRow>().eq(MatchConfigRow::getVersionId, versionId))
                .stream().collect(Collectors.toMap(MatchConfigRow::getPreset, Function.identity()));
    }

    private MatchConfig parse(String raw) {
        try {
            return json.readValue(raw, MatchConfig.class);
        } catch (JacksonException e) {
            throw new BizException("配置不是合法的 JSON，或者字段类型不对：" + e.getOriginalMessage());
        }
    }

    /**
     * 试算一次：候选人和队长按每道题的第一个选项作答，能正常出分就说明配置能用。
     */
    private void tryRun(MatchConfig config, List<Question> questions) {
        Map<String, Object> general = new HashMap<>();
        Map<String, Object> edition = new HashMap<>();
        for (Question q : questions) {
            Object answer = sampleAnswer(q);
            if (answer != null) {
                (q.code().startsWith("P") ? edition : general).put(q.code(), answer);
            }
        }
        Person cand = new Person(1, "候选人", general, edition, Map.of(1L, 2), 0, null);
        Person captain = new Person(2, "队长", general, edition, Map.of(2L, 3), 3, 80.0);
        TeamContext team = new TeamContext(10, 2, List.of(captain), List.of(new Need("试算", 1, Map.of(1L, 1))),
                7, "MIXED", true);
        Labels labels = new Labels() {
            @Override
            public String title(String code) {
                return code;
            }

            @Override
            public String option(String code, String value) {
                return value;
            }

            @Override
            public String skill(long id) {
                return "技能" + id;
            }
        };
        try {
            MatchEngine engine = new MatchEngine(config, labels);
            engine.score(cand, team, Facts.NONE, true);
            engine.score(new Person(3, "空白", Map.of(), Map.of(), Map.of(), 0, null), team, Facts.NONE, false);
        } catch (RuntimeException e) {
            throw new BizException("配置能读出来，但试算时出错了（" + e.getClass().getSimpleName() + "），请检查各题的参数");
        }
    }

    private static Object sampleAnswer(Question q) {
        List<String> values = q.options() == null ? List.of() : q.options().stream().map(Option::value).toList();
        return switch (q.type()) {
            case "SINGLE" -> values.isEmpty() ? null : values.getFirst();
            case "MULTI" -> values.isEmpty() ? null : List.of(values.getFirst());
            case "RANKING" -> values.isEmpty() ? null : values.subList(0, Math.min(3, values.size()));
            case "SLIDER" -> 10;
            case "SLOT_GRID" -> 7;
            case "PLEDGE" -> true;
            default -> null;
        };
    }
}
