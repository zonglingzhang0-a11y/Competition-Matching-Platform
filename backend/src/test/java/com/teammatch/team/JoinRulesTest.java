package com.teammatch.team;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.teammatch.competition.CompetitionEdition;
import com.teammatch.team.TeamMappers.MemberMapper;
import com.teammatch.user.SysUser;
import com.teammatch.user.UserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * "这个人现在能不能加入这支队"的各种情况。
 */
class JoinRulesTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 12, 0);

    private MemberMapper memberMapper;
    private UserService userService;
    private TeamSupport support;
    private Team team;
    private CompetitionEdition edition;
    private SysUser captain;
    private SysUser joiner;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TeamMember.class);
    }

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        userService = mock(UserService.class);
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        support = new TeamSupport(null, memberMapper, null, null, null, null, null, userService, clock);

        captain = user(1L, 100L);
        joiner = user(2L, 100L);
        when(userService.getById(1L)).thenReturn(captain);
        when(userService.isVerified(any())).thenReturn(true);

        team = new Team();
        team.setId(10L);
        team.setCaptainId(1L);
        team.setStatus(Team.Status.RECRUITING);
        edition = new CompetitionEdition();
        edition.setId(20L);
        edition.setRegistrationEnd(NOW.plusDays(10));
        edition.setSameSchoolRequired(true);
    }

    @Test
    void canJoinWhenEverythingIsFine() {
        assertThat(support.joinBlockReason(team, edition, joiner, true)).isNull();
    }

    @Test
    void fullOrClosedTeamsBlock() {
        team.setStatus(Team.Status.FULL);
        assertThat(support.joinBlockReason(team, edition, joiner, true)).isEqualTo("队伍已经满员了");
        team.setStatus(Team.Status.CLOSED);
        assertThat(support.joinBlockReason(team, edition, joiner, true)).isEqualTo("队伍已经停止招募");
    }

    @Test
    void closedRegistrationBlocks() {
        edition.setRegistrationEnd(NOW.minusMinutes(1));
        assertThat(support.joinBlockReason(team, edition, joiner, true)).isEqualTo("这届比赛报名已截止");
    }

    @Test
    void unverifiedUserBlocksWithWordingForActor() {
        when(userService.isVerified(joiner)).thenReturn(false);
        assertThat(support.joinBlockReason(team, edition, joiner, true)).isEqualTo("请先完成学校认证");
        assertThat(support.joinBlockReason(team, edition, joiner, false)).isEqualTo("对方还没完成学校认证");
    }

    @Test
    void alreadyInAnotherTeamBlocks() {
        TeamMember other = new TeamMember();
        other.setTeamId(99L);
        when(memberMapper.selectOne(any())).thenReturn(other);
        assertThat(support.joinBlockReason(team, edition, joiner, false)).isEqualTo("对方已经在这届比赛的其他队伍里了");

        other.setTeamId(10L);
        assertThat(support.joinBlockReason(team, edition, joiner, true)).isEqualTo("你已经在这支队伍里了");
    }

    @Test
    void differentSchoolBlocksOnlyWhenRequired() {
        joiner.setSchoolId(200L);
        assertThat(support.joinBlockReason(team, edition, joiner, true)).contains("要求同校组队");
        edition.setSameSchoolRequired(false);
        assertThat(support.joinBlockReason(team, edition, joiner, true)).isNull();
    }

    private static SysUser user(long id, long schoolId) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setSchoolId(schoolId);
        u.setVerifyStatus(SysUser.VerifyStatus.VERIFIED);
        return u;
    }
}
