package com.teammatch.home;

import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.Result;
import com.teammatch.home.HomeDtos.HomeView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    /** 我的主页：下一个节点、学期赛程带、我参加的比赛、待处理、推荐队友 */
    @GetMapping("/home")
    public Result<HomeView> home() {
        return Result.ok(homeService.view(StpUtil.getLoginIdAsLong()));
    }
}
