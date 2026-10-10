package com.teammatch.survey;

import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.Result;
import com.teammatch.survey.SurveyDtos.AnswersForm;
import com.teammatch.survey.SurveyDtos.EditionAnswers;
import com.teammatch.survey.SurveyDtos.MyAnswers;
import com.teammatch.survey.SurveyDtos.Progress;
import com.teammatch.survey.SurveyDtos.Survey;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/survey")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    /** 当前版本的全部题目 */
    @GetMapping
    public Result<Survey> survey() {
        return Result.ok(surveyService.survey());
    }

    @GetMapping("/progress")
    public Result<Progress> progress() {
        return Result.ok(surveyService.progress(StpUtil.getLoginIdAsLong()));
    }

    /** 我的通用部分答案；没填过时 data 为空 */
    @GetMapping("/general")
    public Result<MyAnswers> general() {
        return Result.ok(surveyService.general(StpUtil.getLoginIdAsLong()));
    }

    @PutMapping("/general")
    public Result<Void> saveGeneral(@Valid @RequestBody AnswersForm form) {
        surveyService.saveGeneral(StpUtil.getLoginIdAsLong(), form);
        return Result.ok();
    }

    @GetMapping("/editions/{id}")
    public Result<EditionAnswers> edition(@PathVariable long id) {
        return Result.ok(surveyService.edition(StpUtil.getLoginIdAsLong(), id));
    }

    @PutMapping("/editions/{id}")
    public Result<Void> saveEdition(@PathVariable long id, @Valid @RequestBody AnswersForm form) {
        surveyService.saveEdition(StpUtil.getLoginIdAsLong(), id, form);
        return Result.ok();
    }
}
