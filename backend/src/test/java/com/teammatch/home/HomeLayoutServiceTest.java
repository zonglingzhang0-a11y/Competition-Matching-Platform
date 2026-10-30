package com.teammatch.home;

import com.teammatch.home.HomeDtos.Layout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 主页自定义的整理：不认识的面板丢掉、漏掉的补上、待你处理不能隐藏 */
class HomeLayoutServiceTest {

    @Test
    void 没设置过就是默认顺序_什么都不隐藏() {
        Layout d = HomeLayoutService.normalize(null);
        assertThat(d.order()).isEqualTo(HomeLayoutService.PANELS);
        assertThat(d.hidden()).isEmpty();
        assertThat(d.wideCalendar()).isFalse();
    }

    @Test
    void 调过的顺序留着_漏掉的按默认顺序补在后面() {
        Layout l = HomeLayoutService.normalize(new Layout(List.of("messages", "pending", "open", "mine"), List.of(), true));
        assertThat(l.order()).containsExactly("messages", "pending", "open", "mine", "today", "schedule", "semester",
                "recommend");
        assertThat(l.wideCalendar()).isTrue();
    }

    @Test
    void 不认识的面板和重复的丢掉() {
        Layout l = HomeLayoutService.normalize(new Layout(List.of("hack", "mine", "mine", "today"), List.of("hack", "open", "open"), false));
        assertThat(l.order()).startsWith("mine", "today").doesNotContain("hack").hasSameSizeAs(HomeLayoutService.PANELS);
        assertThat(l.hidden()).containsExactly("open");
    }

    @Test
    void 待你处理不能隐藏() {
        Layout l = HomeLayoutService.normalize(new Layout(null, List.of("pending", "messages"), false));
        assertThat(l.hidden()).containsExactly("messages");
        assertThat(l.order()).isEqualTo(HomeLayoutService.PANELS);
    }
}
