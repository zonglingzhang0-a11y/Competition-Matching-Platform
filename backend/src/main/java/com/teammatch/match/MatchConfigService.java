package com.teammatch.match;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teammatch.common.BizException;
import com.teammatch.competition.Competition;
import com.teammatch.survey.SurveyEntities.MatchConfigMapper;
import com.teammatch.survey.SurveyEntities.MatchConfigRow;
import com.teammatch.survey.SurveyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 读取匹配配置并缓存。管理员在后台改了配置后调用 {@link #evict()}。
 */
@Service
@RequiredArgsConstructor
public class MatchConfigService {

    private final MatchConfigMapper mapper;
    private final SurveyService surveyService;
    private final JsonMapper json;
    private final Map<String, MatchConfig> cache = new ConcurrentHashMap<>();

    public MatchConfig forPreset(Competition.MatchPreset preset) {
        long versionId = surveyService.activeVersion().getId();
        Competition.MatchPreset p = preset == null ? Competition.MatchPreset.DEFAULT : preset;
        return cache.computeIfAbsent(versionId + ":" + p, key -> load(versionId, p));
    }

    public void evict() {
        cache.clear();
    }

    private MatchConfig load(long versionId, Competition.MatchPreset preset) {
        List<MatchConfigRow> rows = mapper.selectList(new LambdaQueryWrapper<MatchConfigRow>()
                .eq(MatchConfigRow::getVersionId, versionId));
        MatchConfig defaults = rows.stream().filter(r -> "DEFAULT".equals(r.getPreset())).findFirst()
                .map(r -> json.readValue(r.getConfig(), MatchConfig.class))
                .orElseThrow(() -> new BizException(500, "匹配配置还没设置好，请联系管理员"));
        if (preset == Competition.MatchPreset.DEFAULT) {
            return defaults;
        }
        return rows.stream().filter(r -> preset.name().equals(r.getPreset())).findFirst()
                .map(r -> defaults.applyPreset(json.readValue(r.getConfig(), MatchConfig.class)))
                .orElse(defaults);
    }
}
