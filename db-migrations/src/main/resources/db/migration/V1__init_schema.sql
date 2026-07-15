create table product_catalog
(
    gtin              varchar(14) primary key,
    name              varchar(512) not null,
    manufacturer_inn  varchar(12)  not null,
    manufacturer_name varchar(512) not null,
    category_code     varchar(64)  not null,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null
);

create table product_instance
(
    code_hash          varchar(64) primary key,
    gtin               varchar(14) not null
        references product_catalog (gtin),
    circulation_status varchar(32) not null,
    production_date    date,
    expiration_date    date,
    version            bigint      not null default 0,
    updated_at         timestamptz not null
);

create index idx_product_instance_gtin
    on product_instance (gtin);

create table product_check
(
    id                  uuid         primary key,
    client_id           varchar(128) not null,
    idempotency_key     varchar(128) not null,
    request_hash        varchar(64)  not null,
    code_hash           varchar(64)  not null,
    decision            varchar(32)  not null,
    reason_codes        jsonb        not null,
    unavailable_sources jsonb        not null,
    degraded            boolean      not null,
    result_snapshot     jsonb        not null,
    duration_ms         bigint       not null,
    created_at          timestamptz  not null,

    constraint uq_product_check_idempotency
        unique (client_id, idempotency_key)
);

create index idx_product_check_created_at
    on product_check (created_at);

create index idx_product_check_code_hash_created_at
    on product_check (code_hash, created_at desc);

create table outbox_event
(
    id              uuid         primary key,
    aggregate_id    uuid         not null,
    event_type      varchar(128) not null,
    payload         jsonb        not null,
    status          varchar(32)  not null,
    attempts        integer      not null default 0,
    next_attempt_at timestamptz  not null,
    created_at      timestamptz  not null,
    published_at    timestamptz
);

create index idx_outbox_pending
    on outbox_event (status, next_attempt_at);
