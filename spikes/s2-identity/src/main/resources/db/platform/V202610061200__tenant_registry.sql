-- V202610061200__tenant_registry.sql: tenant registry and host mapping in the platform DB (doc §4.3).
-- The organization link lives on the tenant, not on the domain: a tenant may have several hosts, and a bearer request
-- has no meaningful host (S2 design decision 2). (issuer, alias) is the key, so a separate realm needs no resolver
-- change; the security chains still trust a single issuer (S2 finding).
create table tenant (
    tenant_key         text primary key check (tenant_key ~ '^[a-z][a-z0-9_]{1,29}$'),
    status             text not null check (status in ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'MAINTENANCE', 'ARCHIVED')),
    oidc_issuer        text not null,
    organization_alias text not null,
    unique (oidc_issuer, organization_alias)
);

create table tenant_domain (
    host       text primary key check (host = lower(host)),
    tenant_key text not null references tenant
);
