package com.cfduel.achievement;

import com.cfduel.duel.event.DuelCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Thin Spring listener that fires after a duel result has been committed
 * (spec §13: "runs as a Spring ApplicationEventListener after each duel result
 * is saved"). It delegates all stat/achievement logic to
 * {@link AchievementService}; failures are swallowed so a bad evaluation can
 * never roll back or break the duel flow.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AchievementChecker {

    private final AchievementService achievementService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDuelCompleted(DuelCompletedEvent event) {
        try {
            achievementService.onDuelCompleted(event);
        } catch (RuntimeException ex) {
            log.warn("achievement evaluation failed for room {}: {}",
                    event.roomId(), ex.getMessage());
        }
    }
}
