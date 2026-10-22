package com.teammatch.crawler;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.Result;
import com.teammatch.crawler.AiSummaryDtos.View;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 比赛页的 AI 总结：所有人都能看存好的总结和信息渠道；只有管理员能点"生成"（要调大模型，本机模型一次只能处理一个请求）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiSummaryController {

    private final AiSummaryService service;

    @GetMapping("/competitions/{id}/ai-summary")
    public Result<View> view(@PathVariable long id) {
        return Result.ok(service.view(id, StpUtil.hasRole("ADMIN")));
    }

    /** 调大模型重新生成，本机模型要等几十秒 */
    @SaCheckRole("ADMIN")
    @PostMapping("/admin/competitions/{id}/ai-summary")
    public Result<View> generate(@PathVariable long id) {
        return Result.ok(service.generate(StpUtil.getLoginIdAsLong(), id));
    }
}
