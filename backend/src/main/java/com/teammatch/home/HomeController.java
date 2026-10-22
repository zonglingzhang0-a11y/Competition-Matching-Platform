package com.teammatch.home;

import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.Result;
import com.teammatch.home.HomeDtos.HomeView;
import com.teammatch.home.HomeDtos.Layout;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;
    private final HomeLayoutService layoutService;

    /** 我的主页：下一个节点、学期赛程带、我参加的比赛、待处理、推荐队友 */
    @GetMapping("/home")
    public Result<HomeView> home() {
        return Result.ok(homeService.view(StpUtil.getLoginIdAsLong()));
    }

    /** 主页自定义：面板顺序、隐藏的面板、日历栏宽窄；没设置过返回默认 */
    @GetMapping("/home/layout")
    public Result<Layout> layout() {
        return Result.ok(layoutService.layout(StpUtil.getLoginIdAsLong()));
    }

    /** 保存主页自定义，返回整理后的结果（不认识的面板丢掉、漏掉的补上、待你处理不能隐藏） */
    @PutMapping("/home/layout")
    public Result<Layout> saveLayout(@RequestBody Layout layout) {
        return Result.ok(layoutService.save(StpUtil.getLoginIdAsLong(), layout));
    }
}
