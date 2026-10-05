package com.smart.erp.spike.s1.sample;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

/** Read side with jOOQ (ADR-0011). No code generation in the spike; S3 covers codegen. */
@Component
public class SampleQueries {

    static final Table<?> SAMPLE_RECORD = DSL.table(DSL.name("sample", "sample_record"));
    static final Field<UUID> ID = DSL.field(DSL.name("id"), SQLDataType.UUID);
    static final Field<String> NAME = DSL.field(DSL.name("name"), SQLDataType.VARCHAR);
    static final Field<Instant> PROCESSED_AT = DSL.field(DSL.name("processed_at"), SQLDataType.INSTANT);

    private final DSLContext dsl;

    SampleQueries(DSLContext dsl) {
        this.dsl = dsl;
    }

    public int countByName(String name) {
        return dsl.fetchCount(SAMPLE_RECORD, NAME.eq(name));
    }

    public Optional<Instant> processedAt(UUID id) {
        return Optional.ofNullable(
                dsl.select(PROCESSED_AT).from(SAMPLE_RECORD).where(ID.eq(id)).fetchOne(PROCESSED_AT));
    }
}
