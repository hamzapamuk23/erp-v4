-- The spike's only business table (doc §7.2 conventions: uuid id, bigint version, snake_case).
create schema sample;

create table sample.sample_record (
    id           uuid primary key,
    version      bigint      not null,
    name         text        not null,
    processed_at timestamptz
);
