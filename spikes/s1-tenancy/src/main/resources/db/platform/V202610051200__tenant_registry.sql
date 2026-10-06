-- Tenant registry in the platform DB (doc §4.3). Spike subset: no cluster, time zone or schema version columns.
create table tenant (
    tenant_key    text primary key check (tenant_key ~ '^[a-z][a-z0-9_]{1,29}$'),
    database_name text not null unique,
    status        text not null check (status in ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'MAINTENANCE', 'ARCHIVED'))
);
