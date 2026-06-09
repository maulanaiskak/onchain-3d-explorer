package com.maul.onchain3d.graph;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * R2DBC entity mapping to the {@code transfer} table.
 */
@Data
@Table("transfer")
public class Transfer {

    @Id
    private Long id;
    private String txHash;
    private int logIndex;
    private String chain;
    private String fromAddr;
    private String toAddr;
    private String asset;
    private BigDecimal valueRaw;
    private double valueNorm;
    private OffsetDateTime blockTime;
    private OffsetDateTime ingestedAt;
}
