package com.teammatch.calendar;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.teammatch.calendar.CalendarDtos.CalendarEvent;
import com.teammatch.calendar.CalendarDtos.Layer;
import com.teammatch.common.BizException;
import com.teammatch.user.SysUser;
import com.teammatch.user.SysUserMapper;
import com.teammatch.user.SysUser.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * 订阅到手机日历：每人一条私有链接 /api/ics/{token}.ics，日历应用定时来取，输出 iCalendar。
 * <p>
 * 链接里的 token 就是凭证（日历应用没法登录），所以要足够长、随机；泄露了可以重置，旧链接马上失效。
 * 内容是过去 30 天到未来 180 天的赛程：关注和参加的比赛节点、所在队伍的里程碑、个人日程；截止类提前一天提醒。
 */
@Service
@RequiredArgsConstructor
public class CalendarFeedService {

    static final int PAST_DAYS = 30;
    static final int FUTURE_DAYS = 180;

    private final SysUserMapper userMapper;
    private final CalendarService calendarService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** 我的订阅链接里的 token，第一次用时生成 */
    public String token(long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u.getCalendarToken() != null) {
            return u.getCalendarToken();
        }
        return reset(userId);
    }

    /** 换一个新 token，旧链接立即失效 */
    public String reset(long userId) {
        String token = newToken();
        userMapper.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getCalendarToken, token));
        return token;
    }

    /** 日历应用来取的内容；token 不对或账号被停用都当作不存在 */
    public String ics(String token) {
        if (token == null || !token.matches("[0-9a-f]{64}")) {
            throw new BizException(404, "订阅链接不存在");
        }
        SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getCalendarToken, token));
        if (u == null || u.getStatus() == UserStatus.BANNED) {
            throw new BizException(404, "订阅链接不存在");
        }
        LocalDate today = LocalDate.now(clock);
        IcsWriter w = new IcsWriter("竞搭 · " + u.getNickname() + "的赛程", clock.instant());
        for (CalendarEvent e : calendarService.viewForFeed(u.getId(), today.minusDays(PAST_DAYS), today.plusDays(FUTURE_DAYS)).events()) {
            w.event(e.id(), summary(e), description(e), e.start(), e.end(), e.allDay(), e.deadline() && !Boolean.TRUE.equals(e.done()));
        }
        return w.finish();
    }

    /** 比赛和队伍的节点前面带上比赛简称或队名；截止类标出来，做完的里程碑标"已完成" */
    static String summary(CalendarEvent e) {
        String head = e.layer() == Layer.PERSONAL ? "" : e.layerName() + " · ";
        String tail = Boolean.TRUE.equals(e.done()) ? "（已完成）" : e.deadline() ? "（截止）" : "";
        return head + e.title() + tail;
    }

    private static String description(CalendarEvent e) {
        String source = switch (e.layer()) {
            case OFFICIAL -> "比赛官方节点" + (e.joined() ? "" : "（关注中）");
            case TEAM -> "队伍「" + e.layerName() + "」的节点";
            case PERSONAL -> "个人日程";
        };
        return source + (e.fuzzy() ? "，时间还没定" : "") + (e.conflictWith() != null ? "，和" + e.conflictWith() + "撞期" : "")
                + "。来自竞搭。";
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
