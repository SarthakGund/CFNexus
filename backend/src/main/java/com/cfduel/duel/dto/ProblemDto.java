package com.cfduel.duel.dto;

/** The problem assigned to a duel once it has started. */
public record ProblemDto(
        String problemId,
        Integer contestId,
        String index,
        String name,
        String url,
        Integer rating) {
}
