package com.maul.onchain3d.graph;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * R2DBC entity mapping to the {@code address_embedding} table.
 *
 * <p>The {@code embedding} field is stored as a pgvector {@code vector(3072)} column.
 * It is mapped to {@code float[]} which is handled natively by the r2dbc-postgresql driver.
 */
@Data
@Table("address_embedding")
public class AddressEmbedding {

    @Id
    private String address;
    private String summary;
    private float[] embedding;
    private OffsetDateTime updatedAt;
}
