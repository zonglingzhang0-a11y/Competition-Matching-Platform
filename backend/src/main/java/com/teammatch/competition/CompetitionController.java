package com.teammatch.competition;

import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.PageResult;
import com.teammatch.common.Result;
import com.teammatch.competition.CompetitionDtos.CompetitionDetail;
import com.teammatch.competition.CompetitionDtos.CompetitionOption;
import com.teammatch.competition.CompetitionDtos.CompetitionSummary;
import com.teammatch.competition.CompetitionDtos.ListQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CompetitionController {

    private final CompetitionService competitionService;

    @GetMapping("/competitions")
    public Result<PageResult<CompetitionSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Competition.Level level,
            @RequestParam(defaultValue = "false") boolean openOnly,
            @RequestParam(defaultValue = "false") boolean followedOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        ListQuery q = new ListQuery(keyword, category, level, openOnly, followedOnly,
                Math.max(page, 1), Math.clamp(size, 1, 50));
        return Result.ok(competitionService.list(StpUtil.getLoginIdAsLong(), q));
    }

    /** 下拉框用的比赛列表 */
    @GetMapping("/competitions/options")
    public Result<List<CompetitionOption>> options() {
        return Result.ok(competitionService.options());
    }

    @GetMapping("/competitions/{id}")
    public Result<CompetitionDetail> detail(@PathVariable long id) {
        return Result.ok(competitionService.detail(StpUtil.getLoginIdAsLong(), id, false));
    }

    @PostMapping("/editions/{id}/follow")
    public Result<Void> follow(@PathVariable long id) {
        competitionService.follow(StpUtil.getLoginIdAsLong(), id);
        return Result.ok();
    }

    @DeleteMapping("/editions/{id}/follow")
    public Result<Void> unfollow(@PathVariable long id) {
        competitionService.unfollow(StpUtil.getLoginIdAsLong(), id);
        return Result.ok();
    }
}
