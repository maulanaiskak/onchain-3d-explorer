package com.maul.onchain3d.copilot;

import com.maul.onchain3d.copilot.dto.AddressSummary;
import com.maul.onchain3d.copilot.dto.TransferFact;

import java.util.List;
import java.util.Set;

/**
 * Grounded retrieval context assembled by {@link RetrievalService} for each copilot query.
 *
 * @param neighbors      top-K semantically similar address summaries from the vector store
 * @param topTransfers   exact top-N transfers by value from the SQL facts layer
 * @param allowedNodeIds union of addresses from both sources (used for graph focus filtering)
 */
public record RetrievalContext(
        List<AddressSummary> neighbors,
        List<TransferFact> topTransfers,
        Set<String> allowedNodeIds) {}
