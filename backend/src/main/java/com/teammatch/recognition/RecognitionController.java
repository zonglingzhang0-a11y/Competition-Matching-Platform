package com.teammatch.recognition;

import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.Result;
import com.teammatch.recognition.RecognitionDtos.Board;
import com.teammatch.recognition.RecognitionDtos.MineForm;
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

/**
 * 各校加分认定：看本校和其他学校的认定，标注本校的、确认同学标注的。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecognitionController {

    private final RecognitionService recognitionService;

    @GetMapping("/competitions/{id}/recognitions")
    public Result<Board> board(@PathVariable long id) {
        return Result.ok(recognitionService.board(StpUtil.getLoginIdAsLong(), id));
    }

    /** 提交或修改自己对这个比赛的标注 */
    @PutMapping("/competitions/{id}/recognitions/mine")
    public Result<Board> saveMine(@PathVariable long id, @Valid @RequestBody MineForm form) {
        long userId = StpUtil.getLoginIdAsLong();
        recognitionService.saveMine(userId, id, form);
        return Result.ok(recognitionService.board(userId, id));
    }

    @DeleteMapping("/competitions/{id}/recognitions/mine")
    public Result<Board> withdrawMine(@PathVariable long id) {
        long userId = StpUtil.getLoginIdAsLong();
        recognitionService.withdrawMine(userId, id);
        return Result.ok(recognitionService.board(userId, id));
    }

    @PostMapping("/recognitions/{id}/confirm")
    public Result<Void> confirm(@PathVariable long id) {
        recognitionService.confirm(StpUtil.getLoginIdAsLong(), id);
        return Result.ok();
    }

    @DeleteMapping("/recognitions/{id}/confirm")
    public Result<Void> unconfirm(@PathVariable long id) {
        recognitionService.unconfirm(StpUtil.getLoginIdAsLong(), id);
        return Result.ok();
    }
}
