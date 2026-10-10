package com.teammatch.team;

import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.Result;
import com.teammatch.team.TeamDtos.ApplicationView;
import com.teammatch.team.TeamDtos.ApplyRequest;
import com.teammatch.team.TeamDtos.CreateTeamRequest;
import com.teammatch.team.TeamDtos.InviteRequest;
import com.teammatch.team.TeamDtos.MyApplications;
import com.teammatch.team.TeamDtos.NeedForm;
import com.teammatch.team.TeamDtos.RecruitingRequest;
import com.teammatch.team.TeamDtos.Seeker;
import com.teammatch.team.TeamDtos.TeamDetail;
import com.teammatch.team.TeamDtos.TeamSummary;
import com.teammatch.team.TeamDtos.TransferCaptainRequest;
import com.teammatch.team.TeamDtos.UpdateTeamRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final ApplicationService applicationService;

    private static long me() {
        return StpUtil.getLoginIdAsLong();
    }

    // ---------- 队伍 ----------

    @PostMapping("/teams")
    public Result<Long> create(@Valid @RequestBody CreateTeamRequest req) {
        return Result.ok(teamService.create(me(), req));
    }

    @GetMapping("/teams/mine")
    public Result<List<TeamSummary>> mine() {
        return Result.ok(teamService.mine(me()));
    }

    @GetMapping("/teams/{id}")
    public Result<TeamDetail> detail(@PathVariable long id) {
        return Result.ok(teamService.detail(me(), id));
    }

    @PutMapping("/teams/{id}")
    public Result<Void> update(@PathVariable long id, @Valid @RequestBody UpdateTeamRequest req) {
        teamService.update(me(), id, req);
        return Result.ok();
    }

    /** 队长停止招募或重新开放 */
    @PutMapping("/teams/{id}/recruiting")
    public Result<Void> setRecruiting(@PathVariable long id, @RequestBody RecruitingRequest req) {
        teamService.setRecruiting(me(), id, req.open());
        return Result.ok();
    }

    @PostMapping("/teams/{id}/disband")
    public Result<Void> disband(@PathVariable long id) {
        teamService.disband(me(), id);
        return Result.ok();
    }

    @PostMapping("/teams/{id}/leave")
    public Result<Void> leave(@PathVariable long id) {
        teamService.leave(me(), id);
        return Result.ok();
    }

    @DeleteMapping("/teams/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable long id, @PathVariable long userId) {
        teamService.removeMember(me(), id, userId);
        return Result.ok();
    }

    @PutMapping("/teams/{id}/captain")
    public Result<Void> transferCaptain(@PathVariable long id, @Valid @RequestBody TransferCaptainRequest req) {
        teamService.transferCaptain(me(), id, req.userId());
        return Result.ok();
    }

    /** 某一届的所有队伍 */
    @GetMapping("/editions/{id}/teams")
    public Result<List<TeamSummary>> listByEdition(@PathVariable long id) {
        return Result.ok(teamService.listByEdition(id));
    }

    /** 队长挑人邀请：关注了这一届、还没组队的人 */
    @GetMapping("/teams/{id}/seekers")
    public Result<List<Seeker>> seekers(@PathVariable long id) {
        return Result.ok(teamService.seekers(me(), id));
    }

    // ---------- 招募需求 ----------

    @PostMapping("/teams/{id}/needs")
    public Result<Long> addNeed(@PathVariable long id, @Valid @RequestBody NeedForm form) {
        return Result.ok(teamService.addNeed(me(), id, form));
    }

    @PutMapping("/needs/{id}")
    public Result<Void> updateNeed(@PathVariable long id, @Valid @RequestBody NeedForm form) {
        teamService.updateNeed(me(), id, form);
        return Result.ok();
    }

    @DeleteMapping("/needs/{id}")
    public Result<Void> cancelNeed(@PathVariable long id) {
        teamService.cancelNeed(me(), id);
        return Result.ok();
    }

    // ---------- 申请和邀请 ----------

    @PostMapping("/teams/{id}/applications")
    public Result<Long> apply(@PathVariable long id, @Valid @RequestBody ApplyRequest req) {
        return Result.ok(applicationService.apply(me(), id, req));
    }

    @PostMapping("/teams/{id}/invitations")
    public Result<Long> invite(@PathVariable long id, @Valid @RequestBody InviteRequest req) {
        return Result.ok(applicationService.invite(me(), id, req));
    }

    /** 队长看自己队伍的申请和邀请 */
    @GetMapping("/teams/{id}/applications")
    public Result<List<ApplicationView>> ofTeam(@PathVariable long id) {
        return Result.ok(applicationService.ofTeam(me(), id));
    }

    @GetMapping("/applications/mine")
    public Result<MyApplications> myApplications() {
        return Result.ok(applicationService.mine(me()));
    }

    @PostMapping("/applications/{id}/accept")
    public Result<Void> accept(@PathVariable long id) {
        applicationService.accept(me(), id);
        return Result.ok();
    }

    @PostMapping("/applications/{id}/reject")
    public Result<Void> reject(@PathVariable long id) {
        applicationService.reject(me(), id);
        return Result.ok();
    }

    @PostMapping("/applications/{id}/withdraw")
    public Result<Void> withdraw(@PathVariable long id) {
        applicationService.withdraw(me(), id);
        return Result.ok();
    }
}
