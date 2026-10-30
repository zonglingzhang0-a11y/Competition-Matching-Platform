package com.teammatch.home;

import com.teammatch.home.HomeDtos.Layout;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 主页自定义：每个面板显示还是隐藏、在自己那一栏里排第几、日历栏加不加宽。存在 user_setting 表，跟着账号走。
 * <p>
 * 面板在哪一栏是固定的（前端决定），这里只存一串面板的先后顺序。不认识的面板丢掉，漏掉的按默认顺序补在后面；
 * 「待你处理」不能隐藏，免得错过邀请和申请。
 */
@Service
@RequiredArgsConstructor
public class HomeLayoutService {

    /** 主页的面板，默认顺序：顶部、左栏（日历、学期赛程带）、中栏（我参加的、适合报名的）、右栏（待处理、推荐、消息） */
    static final List<String> PANELS = List.of("today", "schedule", "semester", "mine", "open", "pending", "recommend",
            "messages");
    private static final Set<String> ALWAYS_SHOWN = Set.of("pending");

    private final LayoutMapper mapper;
    private final JsonMapper json;

    public Layout layout(long userId) {
        String saved = mapper.homeLayout(userId);
        if (saved == null) {
            return defaults();
        }
        try {
            return normalize(json.readValue(saved, Layout.class));
        } catch (JacksonException e) {
            // 存坏了就当没设置过
            return defaults();
        }
    }

    public Layout save(long userId, Layout layout) {
        Layout clean = normalize(layout);
        mapper.saveHomeLayout(userId, json.writeValueAsString(clean));
        return clean;
    }

    static Layout defaults() {
        return new Layout(PANELS, List.of(), false);
    }

    /** 只留认识的面板、去重，漏掉的按默认顺序补上；待你处理不能隐藏 */
    static Layout normalize(Layout in) {
        if (in == null) {
            return defaults();
        }
        Set<String> order = new LinkedHashSet<>();
        if (in.order() != null) {
            in.order().stream().filter(PANELS::contains).forEach(order::add);
        }
        order.addAll(PANELS);
        List<String> hidden = in.hidden() == null ? List.of()
                : in.hidden().stream().filter(p -> PANELS.contains(p) && !ALWAYS_SHOWN.contains(p)).distinct().toList();
        return new Layout(new ArrayList<>(order), hidden, in.wideCalendar());
    }

    @Mapper
    public interface LayoutMapper {

        @Select("SELECT home_layout FROM user_setting WHERE user_id = #{userId}")
        String homeLayout(@Param("userId") long userId);

        @Insert("""
                INSERT INTO user_setting (user_id, home_layout) VALUES (#{userId}, #{layout})
                ON DUPLICATE KEY UPDATE home_layout = #{layout}
                """)
        int saveHomeLayout(@Param("userId") long userId, @Param("layout") String layout);
    }
}
