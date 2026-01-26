package com.example.cbs_mvp.service;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class CandidateStateMachine {

    // Allowed transitions map
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "CANDIDATE", Set.of("DRAFT_READY", "REJECTED", "CANDIDATE"), // Self-transition ok (update)
            "DRAFT_READY", Set.of("EBAY_DRAFT_CREATED", "EBAY_DRAFT_FAILED", "REJECTED", "CANDIDATE"),
            "REJECTED", Set.of("CANDIDATE"), // Can be revived
            "EBAY_DRAFT_FAILED", Set.of("DRAFT_READY", "EBAY_DRAFT_CREATED", "REJECTED", "CANDIDATE"),
            "EBAY_DRAFT_CREATED", Set.of("EBAY_DRAFT_FAILED") // E.g. manual fallback? Or mostly terminal
    );

    public void validate(String fromState, String toState) {
        if (fromState == null) {
            if ("CANDIDATE".equals(toState))
                return; // Initial
            throw new IllegalStateException("Invalid initial state: " + toState);
        }

        if (fromState.equals(toState))
            return; // No-op transition is valid

        Set<String> allowed = TRANSITIONS.get(fromState);
        if (allowed == null || !allowed.contains(toState)) {
            throw new IllegalStateException(
                    String.format("Invalid transition: %s -> %s", fromState, toState));
        }
    }
}
