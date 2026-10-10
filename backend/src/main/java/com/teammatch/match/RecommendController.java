package com.teammatch.match;

import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.Result;
import com.teammatch.match.RecommendService.HomeRecommendation;
import com.teammatch.match.RecommendService.RecommendedUser;
import com.teammatch.match.RecommendService.TeamRecommendations;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    /** 这一届里推荐给我的队伍 */
    @GetMapping("/editions/{id}/recommended-teams")
    public Result<TeamRecommendations> teams(@PathVariable long id) {
        return Result.ok(recommendService.teamsFor(StpUtil.getLoginIdAsLong(), id));
    }

    /** 队长：推荐给我们队的人 */
    @GetMapping("/teams/{id}/recommended-users")
    public Result<List<RecommendedUser>> users(@PathVariable long id) {
        return Result.ok(recommendService.usersFor(StpUtil.getLoginIdAsLong(), id));
    }

    /** 首页：我关注的比赛里推荐给我的队伍 */
    @GetMapping("/recommendations/home")
    public Result<List<HomeRecommendation>> home() {
        return Result.ok(recommendService.home(StpUtil.getLoginIdAsLong()));
    }
}
