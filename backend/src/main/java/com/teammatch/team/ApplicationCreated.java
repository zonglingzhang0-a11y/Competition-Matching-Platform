package com.teammatch.team;

/**
 * 发出了一条入队申请或邀请（事务提交后发布）。匹配模块收到后记下当时的匹配分，见 match/ApplicationScoreRecorder。
 *
 * @param byApplicant true：学生申请；false：队长邀请
 */
public record ApplicationCreated(long applicationId, long userId, long teamId, boolean byApplicant) {
}
