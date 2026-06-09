package com.maul.onchain3d.graph;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * R2DBC entity mapping to the {@code address_stats} table.
 */
@Data
@Table("address_stats")
public class AddressStats {

    @Id
    private String address;
    private String windowLabel;
    private double inValue;
    private double outValue;
    private int txCount;
    private boolean isWhale;
    private OffsetDateTime updatedAt;
}
