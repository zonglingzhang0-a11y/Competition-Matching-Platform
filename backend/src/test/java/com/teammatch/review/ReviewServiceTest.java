package com.teammatch.review;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.teammatch.competition.CompetitionMappers.CompetitionMapper;
import com.teammatch.competition.CompetitionMappers.EditionMapper;
import com.teammatch.match.MatchConfigService;
import com.teammatch.notification.Notification;
import com.teammatch.notification.NotificationService;
import com.teammatch.profile.ProfileMappers.ExperienceMapper;
import com.teammatch.review.ReviewEntities.CreditMapper;
import com.teammatch.review.ReviewEntities.CreditMapper.Credit;
import com.teammatch.review.ReviewEntities.PeerReview;
import com.teammatch.review.ReviewEntities.PeerReviewMapper;
import com.teammatch.review.ReviewEntities.ReviewRound;
import com.teammatch.review.ReviewEntities.RoundMapper;
import com.teammatch.review.ReviewEntities.RoundStatus;
import com.teammatch.team.Team;
import com.teammatch.team.TeamMappers.TeamMapper;
import com.teammatch.team.TeamMember;
import com.teammatch.team.TeamSupport;
import com.teammatch.user.SysUser;
import com.teammatch.user.SysUserMapper;
import com.teammatch.user.UserBriefs;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 互评揭晓时的暂停推荐：只在这一轮又被评为经常失联、不交付时才检查，只数上次暂停结束之后的场次。
 */
class ReviewServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 12, 0);
    private static final long ROUND = 9;
    private static final long TEAM = 5;
    private static final long A = 1;
    private static final long B = 2;
    private static final long ADMIN = 99;
    /** 失联"经常"，其余"从未" */
    private static final String SEVERE = "[3,0,0,0,0,0]";
    private static final String FINE = "[0,0,0,0,0,0]";

    private RoundMapper roundMapper;
    private PeerReviewMapper reviewMapper;
    private CreditMapper creditMapper;
    private SysUserMapper userMapper;
    private NotificationService notifications;
    private ReviewService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PeerReview.class);
        TableInfoHelper.initTableInfo(assistant, ReviewRound.class);
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    @BeforeEach
    void setUp() {
        roundMapper = mock(RoundMapper.class);
        reviewMapper = mock(PeerReviewMapper.class);
        creditMapper = mock(CreditMapper.class);
        userMapper = mock(SysUserMapper.class);
        notifications = mock(NotificationService.class);
        TeamMapper teamMapper = mock(TeamMapper.class);
        TeamSupport teamSupport = mock(TeamSupport.class);
        service = new ReviewService(roundMapper, reviewMapper, creditMapper, teamMapper, mock(EditionMapper.class),
                mock(CompetitionMapper.class), mock(ExperienceMapper.class), userMapper, teamSupport,
                mock(UserBriefs.class), notifications, mock(MatchConfigService.class), JsonMapper.builder().build());

        ReviewRound round = new ReviewRound();
        round.setId(ROUND);
        round.setTeamId(TEAM);
        round.setStatus(RoundStatus.OPEN);
        round.setClosesAt(NOW.minusHours(1));
        when(roundMapper.selectForUpdate(ROUND)).thenReturn(round);
        when(teamSupport.now()).thenReturn(NOW);
        when(teamSupport.activeMembers(TEAM)).thenReturn(List.of(member(A), member(B)));
        Team team = new Team();
        team.setName("测试队");
        when(teamMapper.selectById(TEAM)).thenReturn(team);
        SysUser b = new SysUser();
        b.setNickname("小 B");
        when(userMapper.selectById(B)).thenReturn(b);
        SysUser admin = new SysUser();
        admin.setId(ADMIN);
        when(userMapper.selectList(any())).thenReturn(List.of(admin));
    }

    @Test
    void liftedUserRatedFineThisRoundIsNotSuspendedAgain() {
        when(reviewMapper.selectList(any())).thenReturn(List.of(review(ROUND, A, B, FINE), review(ROUND, B, A, FINE)));
        when(creditMapper.find(B)).thenReturn(credit(B, NOW.minusDays(3)));

        assertThat(service.publishIfDue(ROUND)).isTrue();

        verify(creditMapper, never()).suspend(anyLong(), any());
        verify(reviewMapper, never()).publishedFor(anyLong());
        verify(reviewMapper, never()).publishedSince(anyLong(), any());
    }

    @Test
    void onlyRoundsAfterLastSuspensionCount() {
        LocalDateTime lifted = NOW.minusDays(3);
        when(reviewMapper.selectList(any())).thenReturn(List.of(review(ROUND, A, B, SEVERE), review(ROUND, B, A, FINE)));
        when(creditMapper.find(B)).thenReturn(credit(B, lifted));
        // 解除之后只有这一轮；以前那两场由 SQL 按揭晓时间过滤掉了
        when(reviewMapper.publishedSince(B, lifted)).thenReturn(List.of(review(ROUND, A, B, SEVERE)));

        service.publishIfDue(ROUND);

        verify(reviewMapper).publishedSince(B, lifted);
        verify(reviewMapper, never()).publishedFor(anyLong());
        verify(creditMapper, never()).suspend(anyLong(), any());
    }

    @Test
    void secondSevereRoundSuspendsAndNotifiesAdmins() {
        when(reviewMapper.selectList(any())).thenReturn(List.of(review(ROUND, A, B, SEVERE), review(ROUND, B, A, FINE)));
        when(reviewMapper.publishedFor(B)).thenReturn(List.of(review(3, A, B, SEVERE), review(ROUND, A, B, SEVERE)));

        service.publishIfDue(ROUND);

        verify(creditMapper).suspend(B, NOW.plus(ReviewService.SUSPEND));
        verify(creditMapper, never()).suspend(eq(A), any());
        verify(notifications).send(eq(ADMIN), eq(Notification.Type.SYSTEM), any(), any(), eq("ADMIN_CREDIT"), eq(B));
    }

    private static TeamMember member(long userId) {
        TeamMember m = new TeamMember();
        m.setUserId(userId);
        return m;
    }

    private static PeerReview review(long roundId, long rater, long ratee, String answers) {
        PeerReview r = new PeerReview();
        r.setRoundId(roundId);
        r.setRaterId(rater);
        r.setRateeId(ratee);
        r.setAnswers(answers);
        return r;
    }

    private static Credit credit(long userId, LocalDateTime suspendedUntil) {
        return new Credit(userId, BigDecimal.valueOf(60), 2, suspendedUntil);
    }
}
