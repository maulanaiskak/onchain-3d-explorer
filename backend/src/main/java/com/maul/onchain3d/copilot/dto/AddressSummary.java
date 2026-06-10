package com.maul.onchain3d.copilot.dto;

/**
 * Semantic neighbour returned by the pgvector cosine search in {@code RetrievalService}.
 *
 * @param address    wallet address
 * @param summary    human-readable summary text that was embedded
 * @param similarity cosine similarity score in [0, 1] (higher = more similar)
 */
public record AddressSummary(String address, String summary, double similarity) {}
