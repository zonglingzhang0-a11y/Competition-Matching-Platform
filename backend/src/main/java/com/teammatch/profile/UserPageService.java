package com.teammatch.profile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teammatch.common.BizException;
import com.teammatch.profile.ProfileMappers.ExperienceMapper;
import com.teammatch.profile.UserPageAccess.HistoryVisibility;
import com.teammatch.profile.UserPageAccess.Level;
import com.teammatch.profile.UserPageDtos.Access;
import com.teammatch.profile.UserPageDtos.Credit;
import com.teammatch.profile.UserPageDtos.ExperienceView;
import com.teammatch.profile.UserPageDtos.Mutual;
import com.teammatch.profile.UserPageDtos.RecordState;
import com.teammatch.profile.UserPageDtos.SharedEdition;
import com.teammatch.profile.UserPageDtos.SharedTeam;
import com.teammatch.profile.UserPageDtos.Stats;
import com.teammatch.profile.UserPageDtos.TeamRecord;
import com.teammatch.profile.UserPageDtos.UserPage;
import com.teammatch.profile.UserPageMappers.PrivacyMapper;
import com.teammatch.profile.UserPageMappers.UserPageMapper;
import com.teammatch.survey.SurveyService;
import com.teammatch.user.SysUser;
import com.teammatch.user.SysUserMapper;
import com.teammatch.user.UserBriefs;
import com.teammatch.user.UserBriefs.Contact;
import com.teammatch.user.UserBriefs.UserBrief;
import com.teammatch.user.UserProfile;
import com.teammatch.user.UserProfileMapper;
import com.teammatch.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 个人资料页：点开别人的名字时看到的资料，类似游戏平台里点开头像看到的个人资料。
 * 能看到多少由两人的关系决定，规则见 {@link UserPageAccess}；这里只负责把数据取出来，再按规则裁掉看不到的部分。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPageService {

    private static final String F1 = "F1";
    private static final String F2 = "F2";

    private final SysUserMapper userMapper;
    private final UserProfileMapper profileMapper;
    private final ExperienceMapper experienceMapper;
    private final UserPageMapper pageMapper;
    private final PrivacyMapper privacyMapper;
    private final UserService userService;
    private final UserBriefs userBriefs;
    private final SurveyService surveyService;
    private final Clock clock;

    /** user_privacy 表不存在（老库没跑升级脚本）时只提醒一次 */
    private final AtomicBoolean privacyTableWarned = new AtomicBoolean();

    /**
     * @param preview 看自己的资料页时，true 表示按"其他已认证同学看到的样子"返回；看别人时忽略
     */
    public UserPage view(long viewerId, long targetId, boolean preview) {
        SysUser viewer = userService.getById(viewerId);
        boolean admin = viewer.getRole() == SysUser.UserRole.ADMIN;
        SysUser target = userMapper.selectById(targetId);
        boolean self = viewerId == targetId;
        if (target == null || (target.getStatus() == SysUser.UserStatus.BANNED && !self && !admin)) {
            throw new BizException(404, "这个用户不存在");
        }

        boolean previewing = self && preview;
        Level level = previewing ? Level.MEMBER : UserPageAccess.level(self,
                !self && pageMapper.teammates(viewerId, targetId) > 0,
                !self && pageMapper.related(viewerId, targetId) > 0,
                admin, userService.isVerified(viewer));
        HistoryVisibility setting = historyVisibility(targetId);
        boolean intro = UserPageAccess.canSeeIntro(level);
        boolean history = UserPageAccess.canSeeHistory(level, setting);
        boolean contactVisible = UserPageAccess.canSeeContact(level);
        boolean details = UserPageAccess.canSeePrivateDetails(level);

        UserBrief brief = userBriefs.loadOne(targetId);
        UserProfile profile = profileMapper.selectById(targetId);
        Map<String, Object> answers = intro ? surveyService.generalAnswers(List.of(targetId)).getOrDefault(targetId, Map.of())
                : Map.of();
        UserPageMappers.UserPageMapper.CreditRow creditRow = pageMapper.credit(targetId);
        Credit credit = new Credit(creditRow == null ? null : creditRow.score(),
                creditRow == null || creditRow.ratedRounds() == null ? 0 : creditRow.ratedRounds());

        List<TeamRecord> teams = history ? teams(targetId) : List.of();
        List<ExperienceView> experiences = history ? experiences(targetId, details) : List.of();
        LocalDateTime now = LocalDateTime.now(clock);
        Stats stats = history ? new Stats(teams.size(),
                (int) teams.stream().filter(t -> t.state() == RecordState.FINISHED).count(),
                (int) teams.stream().filter(TeamRecord::captain).count(),
                (int) experiences.stream().filter(e -> UserPageAccess.awardRank(e.award()) > 0).count(),
                Math.max(1, ChronoUnit.DAYS.between(target.getCreatedAt(), now))) : null;

        Mutual mutual = self || level == Level.GUEST ? null : mutual(viewer, target);
        Contact contact = contactVisible ? userBriefs.contacts(List.of(targetId)).get(targetId) : null;

        return new UserPage(target.getId(), target.getNickname(), brief == null ? null : brief.schoolName(),
                userService.isVerified(target), brief == null ? null : brief.major(),
                brief == null ? null : brief.enrollYear(),
                intro && profile != null ? profile.getDegree() : null,
                intro && profile != null ? profile.getBio() : null,
                target.getCreatedAt() == null ? null : target.getCreatedAt().toLocalDate(),
                brief == null ? List.of() : brief.skills(), credit,
                text(answers.get(F1)), details ? text(answers.get(F2)) : null,
                stats, highlight(experiences), teams, experiences, mutual, contact,
                new Access(level, history, contactVisible, notice(level, history), self ? setting : null, previewing));
    }

    /** 本人的履历可见范围，没设置过就是公开。老库还没有 user_privacy 表时也按公开处理，不影响其他功能 */
    public HistoryVisibility historyVisibility(long userId) {
        try {
            String value = privacyMapper.historyVisibility(userId);
            return value == null ? HistoryVisibility.PUBLIC : HistoryVisibility.valueOf(value);
        } catch (DataAccessException e) {
            if (privacyTableWarned.compareAndSet(false, true)) {
                log.warn("读不了 user_privacy 表，资料页的可见范围一律按公开处理。老库请运行 sql/upgrade/2026-10-user-privacy.sql：{}",
                        e.getMessage());
            }
            return HistoryVisibility.PUBLIC;
        }
    }

    public void setHistoryVisibility(long userId, HistoryVisibility visibility) {
        try {
            privacyMapper.saveHistoryVisibility(userId, visibility.name());
        } catch (DataAccessException e) {
            log.warn("保存资料页可见范围失败：{}", e.getMessage());
            throw new BizException(500, "可见范围暂时保存不了：数据库还没有 user_privacy 表，请管理员运行 sql/upgrade/2026-10-user-privacy.sql");
        }
    }

    private List<TeamRecord> teams(long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return pageMapper.teams(userId).stream().map(r -> {
            RecordState state = "DISBANDED".equals(r.teamStatus()) ? RecordState.DISBANDED
                    : !"ACTIVE".equals(r.memberStatus()) ? RecordState.LEFT
                    : "FINISHED".equals(r.teamStatus()) ? RecordState.FINISHED
                    : RecordState.IN_TEAM;
            // 在队天数：离队的算到离队那天，还在队里的算到今天；比赛结束的算到录入成绩那天，没录就不显示
            LocalDateTime end = switch (state) {
                case IN_TEAM -> now;
                case FINISHED -> r.awardRecordedAt();
                case LEFT, DISBANDED -> r.leftAt();
            };
            Long days = end == null || r.joinedAt() == null ? null : Math.max(1, ChronoUnit.DAYS.between(r.joinedAt(), end));
            return new TeamRecord(r.teamId(), r.teamName(), r.competitionId(), r.competitionName(), r.editionTitle(),
                    r.roleName(), Boolean.TRUE.equals(r.captain()), state,
                    state == RecordState.FINISHED ? r.award() : null, r.joinedAt(), days);
        }).toList();
    }

    /** showProof：证明链接给不给看（证书照片上一般有真实姓名） */
    private List<ExperienceView> experiences(long userId, boolean showProof) {
        return experienceMapper.selectList(new LambdaQueryWrapper<UserExperience>()
                        .eq(UserExperience::getUserId, userId)
                        .orderByDesc(UserExperience::getYear)
                        .orderByDesc(UserExperience::getId))
                .stream()
                .map(e -> new ExperienceView(e.getId(), e.getTitle(), e.getYear(), e.getAward(),
                        showProof ? e.getProofUrl() : null, e.getProofUrl() != null && !e.getProofUrl().isBlank(),
                        e.getSource(), Boolean.TRUE.equals(e.getVerified())))
                .toList();
    }

    /**
     * 最高荣誉。平台记录（比赛结束时队长录入、自动生成）和核验过的履历更可信，优先从它们里面挑；
     * 都没有获奖时才用自己填的，页面上会标明"自己填写"。
     */
    static ExperienceView highlight(List<ExperienceView> experiences) {
        return experiences.stream()
                .filter(e -> UserPageAccess.awardRank(e.award()) > 0)
                .max(Comparator.comparing((ExperienceView e) -> trusted(e))
                        .thenComparingInt(e -> UserPageAccess.awardRank(e.award()))
                        .thenComparing(e -> e.year() == null ? 0 : e.year()))
                .orElse(null);
    }

    private static boolean trusted(ExperienceView e) {
        return e.source() == UserExperience.Source.PLATFORM || e.verified();
    }

    private Mutual mutual(SysUser viewer, SysUser target) {
        boolean sameSchool = viewer.getSchoolId() != null && Objects.equals(viewer.getSchoolId(), target.getSchoolId());
        List<SharedEdition> editions = pageMapper.sharedEditions(viewer.getId(), target.getId()).stream()
                .map(r -> new SharedEdition(r.competitionId(), r.competitionName(), r.editionTitle())).toList();
        List<SharedTeam> teams = pageMapper.sharedTeams(viewer.getId(), target.getId()).stream()
                .map(r -> new SharedTeam(r.teamId(), r.teamName(), r.competitionName(), Boolean.TRUE.equals(r.current())))
                .toList();
        return new Mutual(sameSchool, editions, teams);
    }

    private static String notice(Level level, boolean historyVisible) {
        if (level == Level.GUEST) {
            return "完成学校认证后，才能查看同学的完整资料";
        }
        if (!historyVisible) {
            return "TA 的参赛履历和组队记录只给队友，以及 TA 主动联系过的人看（TA 申请了你所在的队伍，或者 TA 的队伍邀请了你）";
        }
        return null;
    }

    private static String text(Object value) {
        return value instanceof String s && !s.isBlank() ? s.trim() : null;
    }
}
