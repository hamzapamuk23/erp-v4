-- Spring Modulith 2.1.1 event publication registry, v2 layout
-- (org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql), in its own schema (doc §7.1, ADR-0012).
-- Created by migration because Modulith's own schema initialization is switched off (it would run at start-up).
create schema platform_events;

create table platform_events.event_publication (
    id                     uuid                     not null,
    listener_id            text                     not null,
    event_type             text                     not null,
    serialized_event       text                     not null,
    publication_date       timestamp with time zone not null,
    completion_date        timestamp with time zone,
    status                 text,
    completion_attempts    int,
    last_resubmission_date timestamp with time zone,
    primary key (id)
);

create index event_publication_serialized_event_hash_idx on platform_events.event_publication using hash (serialized_event);
create index event_publication_by_completion_date_idx on platform_events.event_publication (completion_date);
