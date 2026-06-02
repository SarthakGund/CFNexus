package com.cfduel.cf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Selects a duel problem for a set of participants (spec §10).
 *
 * <p>Prefers a problem of the exact {@code targetRating} that no participant has
 * solved and that has a reasonable solved count (>100), widening the rating
 * window by ±100 as a fallback, and finally relaxing the solved-count filter.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProblemSelectionService {

    private static final long MIN_SOLVED_COUNT = 100L;

    private final CfApiClient cfApiClient;

    /**
     * Picks an eligible problem for the given participants.
     *
     * @param participantHandles Codeforces handles of all participants.
     * @param targetRating       desired problem rating.
     * @return a chosen {@link CfProblem}.
     * @throws IllegalStateException if no eligible problem can be found.
     */
    public CfProblem selectProblem(List<String> participantHandles, int targetRating) {
        // 1. Union of all problems already solved by any participant.
        Set<String> solved = new HashSet<>();
        for (String handle : participantHandles) {
            if (handle != null && !handle.isBlank()) {
                solved.addAll(cfApiClient.getSolvedProblemKeys(handle));
            }
        }

        // 2-4. Exact-rating candidates, filtered and shuffled.
        List<CfProblem> exact = cfApiClient.getProblemsByRating(targetRating);
        List<CfProblem> candidates = filter(exact, solved, true);
        if (!candidates.isEmpty()) {
            Collections.shuffle(candidates);
            return candidates.get(0);
        }

        // 5. Fallback: widen to targetRating-100 .. targetRating+100 (excluding the
        //    exact rating already tried), same filters.
        List<CfProblem> widened = new ArrayList<>();
        for (int r = targetRating - 100; r <= targetRating + 100; r += 100) {
            if (r == targetRating || r <= 0) {
                continue;
            }
            widened.addAll(cfApiClient.getProblemsByRating(r));
        }
        List<CfProblem> widenedCandidates = filter(widened, solved, true);
        if (!widenedCandidates.isEmpty()) {
            Collections.shuffle(widenedCandidates);
            return widenedCandidates.get(0);
        }

        // 6. Last resort: ignore the solvedCount>100 rule across all gathered candidates.
        List<CfProblem> all = new ArrayList<>(exact);
        all.addAll(widened);
        List<CfProblem> relaxed = filter(all, solved, false);
        if (!relaxed.isEmpty()) {
            Collections.shuffle(relaxed);
            return relaxed.get(0);
        }

        throw new IllegalStateException("No eligible problem");
    }

    /**
     * Drops problems lacking a usable key, already solved by a participant, and
     * (when {@code enforceSolvedCount}) those with a solved count of 100 or fewer.
     */
    private List<CfProblem> filter(List<CfProblem> source, Set<String> solved, boolean enforceSolvedCount) {
        List<CfProblem> out = new ArrayList<>();
        for (CfProblem p : source) {
            if (p.contestId() == null || p.index() == null) {
                continue;
            }
            if (solved.contains(p.problemKey())) {
                continue;
            }
            if (enforceSolvedCount && (p.solvedCount() == null || p.solvedCount() <= MIN_SOLVED_COUNT)) {
                continue;
            }
            out.add(p);
        }
        return out;
    }
}
