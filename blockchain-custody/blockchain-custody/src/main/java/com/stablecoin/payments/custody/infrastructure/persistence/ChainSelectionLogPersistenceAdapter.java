package com.stablecoin.payments.custody.infrastructure.persistence;

import com.stablecoin.payments.custody.domain.model.ChainCandidate;
import com.stablecoin.payments.custody.domain.model.ChainSelectionResult;
import com.stablecoin.payments.custody.domain.port.ChainSelectionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * JdbcTemplate-based persistence adapter for the chain_selection_log table.
 * Uses Jackson 3 for JSONB serialization of candidate evaluations.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ChainSelectionLogPersistenceAdapter implements ChainSelectionLogRepository {

    private static final String INSERT_SQL = """
            INSERT INTO chain_selection_log (selection_id, transfer_id, evaluated_at, candidates, selected_chain)
            VALUES (?, ?, ?, ?::jsonb, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    public void save(UUID transferId, ChainSelectionResult result) {
        var selectionId = UUID.randomUUID();
        var evaluatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        var candidatesJson = serializeCandidates(result.candidates());

        jdbcTemplate.update(INSERT_SQL,
                selectionId,
                transferId,
                evaluatedAt,
                candidatesJson,
                result.selectedChain().value());

        log.debug("Saved chain selection log: selectionId={}, transferId={}, selectedChain={}",
                selectionId, transferId, result.selectedChain().value());
    }

    private String serializeCandidates(List<ChainCandidate> candidates) {
        try {
            var dtos = candidates.stream()
                    .map(c -> new CandidateDto(
                            c.chainId().value(),
                            c.feeUsd(),
                            c.finalitySeconds(),
                            c.healthScore(),
                            c.score(),
                            c.selected()))
                    .toList();
            return jsonMapper.writeValueAsString(dtos);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize chain candidates to JSON", e);
        }
    }

    private record CandidateDto(
            String chainId,
            double feeUsd,
            int finalitySeconds,
            double healthScore,
            double score,
            boolean selected
    ) {}
}
