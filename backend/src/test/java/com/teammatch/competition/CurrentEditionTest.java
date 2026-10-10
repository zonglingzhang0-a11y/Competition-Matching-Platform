package com.teammatch.competition;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.teammatch.competition.CompetitionMappers.EditionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 列表里每个比赛显示哪一届：优先报名还没截止、截止最早的；都截止了取最新的。
 */
class CurrentEditionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 12, 0);

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CompetitionEdition.class);
    }

    @Test
    void picksSoonestOpenEditionElseLatestClosed() {
        EditionMapper mapper = mock(EditionMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                // 比赛 1：一届已截止，两届报名中，应选截止更早的那届
                edition(11, 1, 2025, NOW.minusMonths(10)),
                edition(12, 1, 2026, NOW.plusDays(30)),
                edition(13, 1, 2026, NOW.plusDays(5)),
                // 比赛 2：都截止了，应选年份最新的
                edition(21, 2, 2024, NOW.minusYears(2)),
                edition(22, 2, 2025, NOW.minusYears(1)),
                // 比赛 3：报名时间待定的，排在有截止时间的后面
                edition(31, 3, 2026, null),
                edition(32, 3, 2026, NOW.plusDays(60))));
        CompetitionService service = new CompetitionService(null, mapper, null, null, null, Clock.systemDefaultZone());

        Map<Long, CompetitionEdition> current = service.currentEditions(List.of(1L, 2L, 3L), NOW);

        assertThat(current.get(1L).getId()).isEqualTo(13L);
        assertThat(current.get(2L).getId()).isEqualTo(22L);
        assertThat(current.get(3L).getId()).isEqualTo(32L);
    }

    private static CompetitionEdition edition(long id, long competitionId, int year, LocalDateTime registrationEnd) {
        CompetitionEdition e = new CompetitionEdition();
        e.setId(id);
        e.setCompetitionId(competitionId);
        e.setYear(year);
        e.setRegistrationEnd(registrationEnd);
        e.setStatus(CompetitionEdition.Status.PUBLISHED);
        return e;
    }
}
