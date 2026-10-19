package com.teammatch.profile;

import com.teammatch.profile.UserPageAccess.HistoryVisibility;
import com.teammatch.profile.UserPageAccess.Level;
import com.teammatch.profile.UserPageDtos.ExperienceView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 个人资料页"谁能看到什么"的规则，以及最高荣誉怎么挑。
 */
class UserPageAccessTest {

    @Test
    void levelFollowsTheClosestRelation() {
        assertThat(UserPageAccess.level(true, false, false, false, true)).isEqualTo(Level.SELF);
        assertThat(UserPageAccess.level(false, true, true, false, true)).isEqualTo(Level.TEAMMATE);
        assertThat(UserPageAccess.level(false, false, true, false, false)).isEqualTo(Level.RELATED);
        assertThat(UserPageAccess.level(false, false, false, true, false)).isEqualTo(Level.RELATED);
        assertThat(UserPageAccess.level(false, false, false, false, true)).isEqualTo(Level.MEMBER);
        assertThat(UserPageAccess.level(false, false, false, false, false)).isEqualTo(Level.GUEST);
    }

    @Test
    void historyFollowsTheOwnersSettingOnlyForStrangers() {
        // 队友和主动联系过的人：不管怎么设置都能看
        for (Level level : new Level[] {Level.SELF, Level.TEAMMATE, Level.RELATED}) {
            assertThat(UserPageAccess.canSeeHistory(level, HistoryVisibility.RELATED)).isTrue();
        }
        // 其他已认证同学：看本人的设置
        assertThat(UserPageAccess.canSeeHistory(Level.MEMBER, HistoryVisibility.PUBLIC)).isTrue();
        assertThat(UserPageAccess.canSeeHistory(Level.MEMBER, HistoryVisibility.RELATED)).isFalse();
        // 没认证的人：一律看不到
        assertThat(UserPageAccess.canSeeHistory(Level.GUEST, HistoryVisibility.PUBLIC)).isFalse();
    }

    @Test
    void contactOnlyForTeammates() {
        assertThat(UserPageAccess.canSeeContact(Level.SELF)).isTrue();
        assertThat(UserPageAccess.canSeeContact(Level.TEAMMATE)).isTrue();
        assertThat(UserPageAccess.canSeeContact(Level.RELATED)).isFalse();
        assertThat(UserPageAccess.canSeeContact(Level.MEMBER)).isFalse();
        assertThat(UserPageAccess.canSeeContact(Level.GUEST)).isFalse();
    }

    @Test
    void introHiddenFromUnverifiedViewers() {
        assertThat(UserPageAccess.canSeeIntro(Level.MEMBER)).isTrue();
        assertThat(UserPageAccess.canSeeIntro(Level.GUEST)).isFalse();
    }

    @Test
    void awardRankOrdersByLevelThenGrade() {
        assertThat(UserPageAccess.awardRank("国家一等奖")).isGreaterThan(UserPageAccess.awardRank("国家二等奖"));
        assertThat(UserPageAccess.awardRank("国家三等奖")).isGreaterThan(UserPageAccess.awardRank("省级一等奖"));
        assertThat(UserPageAccess.awardRank("省级一等奖")).isGreaterThan(UserPageAccess.awardRank("省级二等奖"));
        assertThat(UserPageAccess.awardRank("省二")).isEqualTo(UserPageAccess.awardRank("省级二等奖"));
        assertThat(UserPageAccess.awardRank("省级三等奖")).isGreaterThan(UserPageAccess.awardRank("校级一等奖"));
        assertThat(UserPageAccess.awardRank("优秀奖")).isPositive();
    }

    @Test
    void participationIsNotAnAward() {
        assertThat(UserPageAccess.awardRank("成功参赛")).isZero();
        assertThat(UserPageAccess.awardRank(null)).isZero();
        assertThat(UserPageAccess.awardRank("  ")).isZero();
    }

    @Test
    void highlightPrefersTrustedRecordsOverSelfReported() {
        ExperienceView selfReported = exp(1, "国家一等奖", UserExperience.Source.MANUAL, false);
        ExperienceView platform = exp(2, "省级一等奖", UserExperience.Source.PLATFORM, false);
        ExperienceView verified = exp(3, "省级二等奖", UserExperience.Source.MANUAL, true);
        // 自己填的国一不如平台记录的省一
        assertThat(UserPageService.highlight(List.of(selfReported, platform, verified))).isEqualTo(platform);
        // 只有自己填的时才用自己填的
        assertThat(UserPageService.highlight(List.of(selfReported))).isEqualTo(selfReported);
        // 没有获奖就没有最高荣誉
        assertThat(UserPageService.highlight(List.of(exp(4, "成功参赛", UserExperience.Source.PLATFORM, false)))).isNull();
    }

    private static ExperienceView exp(long id, String award, UserExperience.Source source, boolean verified) {
        return new ExperienceView(id, "比赛", 2025, award, null, source, verified);
    }
}
