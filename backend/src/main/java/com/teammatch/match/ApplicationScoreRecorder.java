package com.teammatch.match;

import com.teammatch.team.ApplicationCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 发出申请或邀请后，记下那一刻的匹配分（team_application.match_score），看板的"推荐准不准"拿它和处理结果对比。
 * <p>
 * 推荐分平时不存，事后再算已经不准（入了队的人和队伍都变了），所以要在发出时记。
 * 在申请的事务提交之后才算，算分出错或者慢都不影响申请本身，只是这一条没有分。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationScoreRecorder {

    private final RecommendService recommendService;
    private final MatchMapper matchMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(ApplicationCreated e) {
        try {
            double score = recommendService.scoreOf(e.userId(), e.teamId(), e.byApplicant());
            matchMapper.saveApplicationScore(e.applicationId(), score);
        } catch (RuntimeException ex) {
            log.info("申请 {} 没记下匹配分：{}", e.applicationId(), ex.getMessage());
        }
    }
}
