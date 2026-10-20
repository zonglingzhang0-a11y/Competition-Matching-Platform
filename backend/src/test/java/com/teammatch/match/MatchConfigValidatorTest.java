package com.teammatch.match;

import com.teammatch.common.BizException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 管理员改匹配配置时的检查。正例用 初始数据（db/migration/V2__seed.sql）里的真实配置，反例在它上面改一处。
 */
class MatchConfigValidatorTest {

    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private static String seed;
    private static String defaultJson;
    private static Set<String> codes;

    @BeforeAll
    static void load() throws Exception {
        seed = Files.readString(Path.of("src/main/resources/db/migration/V2__seed.sql"));
        defaultJson = extract("DEFAULT");
        codes = new HashSet<>();
        Matcher m = Pattern.compile("\\(@v, '([A-Z0-9]+)', '(GENERAL|COMPETITION)'").matcher(seed);
        while (m.find()) {
            codes.add(m.group(1));
        }
    }

    private static String extract(String preset) {
        Matcher m = Pattern.compile("\\(@v, '" + preset + "', '(\\{.*?\\})'\\)", Pattern.DOTALL).matcher(seed);
        if (!m.find()) {
            throw new IllegalStateException("V2__seed.sql 里没找到 " + preset);
        }
        return m.group(1);
    }

    private static MatchConfig parse(String json) {
        return JSON.readValue(json, MatchConfig.class);
    }

    @Test
    void seedConfigsPass() {
        MatchConfig defaults = parse(defaultJson);
        assertThatCode(() -> MatchConfigValidator.validateDefault(defaults, codes)).doesNotThrowAnyException();
        assertThatCode(() -> MatchConfigValidator.validatePreset(defaults, parse(extract("SHORT_INTENSIVE"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> MatchConfigValidator.validatePreset(defaults, parse(extract("LONG_TERM"))))
                .doesNotThrowAnyException();
    }

    @Test
    void matrixMustBeSquareAndMatchOptions() {
        String bad = defaultJson.replace("\"matrix\": [[1,0.6,0.2],[0.6,1,0.6],[0.2,0.6,1]]",
                "\"matrix\": [[1,0.6],[0.6,1]]");
        assertThatThrownBy(() -> MatchConfigValidator.validateDefault(parse(bad), codes))
                .isInstanceOf(BizException.class).hasMessageContaining("P3").hasMessageContaining("3×3");
    }

    @Test
    void matrixValuesStayBetweenZeroAndOne() {
        String bad = defaultJson.replace("[[1,0.6,0.2,0],[0.6,1,0.7,0.3]", "[[1,0.6,0.2,0],[0.6,1.5,0.7,0.3]");
        assertThatThrownBy(() -> MatchConfigValidator.validateDefault(parse(bad), codes))
                .hasMessageContaining("P1").hasMessageContaining("0 到 1");
    }

    @Test
    void unknownQuestionAndScorerAreRejected() {
        String unknownQ = defaultJson.replace("{\"q\": \"G1\", \"scorer\": \"tags\"", "{\"q\": \"G99\", \"scorer\": \"tags\"");
        assertThatThrownBy(() -> MatchConfigValidator.validateDefault(parse(unknownQ), codes))
                .hasMessageContaining("G99").hasMessageContaining("问卷里没有");
        String unknownScorer = defaultJson.replace("\"scorer\": \"tags\"", "\"scorer\": \"magic\"");
        assertThatThrownBy(() -> MatchConfigValidator.validateDefault(parse(unknownScorer), codes))
                .hasMessageContaining("打分方式");
    }

    @Test
    void sliderNeedsTolerance() {
        String bad = defaultJson.replace("{\"q\": \"P2\", \"scorer\": \"slider\", \"weight\": 0.35, \"tolerance\": 10}",
                "{\"q\": \"P2\", \"scorer\": \"slider\", \"weight\": 0.35}");
        assertThatThrownBy(() -> MatchConfigValidator.validateDefault(parse(bad), codes))
                .hasMessageContaining("tolerance");
    }

    @Test
    void negativeOrAllZeroWeightsAreRejected() {
        String negative = defaultJson.replace("\"weight\": 0.25, \"items\"", "\"weight\": -0.25, \"items\"");
        assertThatThrownBy(() -> MatchConfigValidator.validateDefault(parse(negative), codes))
                .hasMessageContaining("目标与投入").hasMessageContaining("权重");
        String zero = defaultJson.replaceAll("\"weight\": 0\\.(25|20|15), \"items\"", "\"weight\": 0, \"items\"");
        assertThatThrownBy(() -> MatchConfigValidator.validateDefault(parse(zero), codes))
                .hasMessageContaining("不能全是 0");
    }

    @Test
    void presetMustReferToExistingDimensionsAndOptions() {
        MatchConfig defaults = parse(defaultJson);
        assertThatThrownBy(() -> MatchConfigValidator.validatePreset(defaults,
                parse("{\"dimensionWeights\": {\"luck\": 0.5}}")))
                .hasMessageContaining("luck");
        assertThatThrownBy(() -> MatchConfigValidator.validatePreset(defaults,
                parse("{\"matrixOverrides\": {\"G2\": [{\"row\": \"Z\", \"col\": \"D\", \"value\": 0.2}]}}")))
                .hasMessageContaining("G2 的选项只有");
        assertThatThrownBy(() -> MatchConfigValidator.validatePreset(defaults,
                parse("{\"matrixOverrides\": {\"P2\": [{\"row\": \"A\", \"col\": \"B\", \"value\": 0.2}]}}")))
                .hasMessageContaining("不是用相容矩阵");
        assertThatThrownBy(() -> MatchConfigValidator.validatePreset(defaults, parse(defaultJson)))
                .hasMessageContaining("预设只能写");
    }

    @Test
    void defaultMustNotCarryPresetFields() {
        String bad = defaultJson.replaceFirst("\\{", "{\"extends\": \"DEFAULT\",");
        assertThatThrownBy(() -> MatchConfigValidator.validateDefault(parse(bad), codes))
                .hasMessageContaining("预设用的");
    }
}
