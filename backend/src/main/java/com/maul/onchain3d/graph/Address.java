package com.maul.onchain3d.graph;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * R2DBC entity mapping to the {@code address} table.
 */
@Data
@Table("address")
public class Address {

    @Id
    private String address;
    private String chain;
    private String label;
    private OffsetDateTime firstSeenAt;
    private OffsetDateTime lastSeenAt;
}
