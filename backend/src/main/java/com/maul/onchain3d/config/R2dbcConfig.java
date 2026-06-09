package com.maul.onchain3d.config;

import org.springframework.context.annotation.Configuration;

/**
 * R2DBC configuration for PostgreSQL.
 *
 * <p>The r2dbc-postgresql driver natively supports {@code float[]} column mapping used by
 * the {@code address_embedding.embedding} (vector) column. No custom codec registration
 * is required for basic array types. If a custom pgvector codec is needed in the future
 * (e.g., for typed {@code Vector} objects), register it here via
 * {@code PostgresqlConnectionConfiguration.Builder#codecRegistrar(CodecRegistrar)}.
 */
@Configuration
public class R2dbcConfig {
    // pgvector float[] columns work natively with R2DBC PostgreSQL driver.
    // Add custom CodecRegistrar beans here if vector type handling needs to be extended.
}
