create table if not exists orders (
    id bigserial primary key,
    sku varchar(64) not null,
    qty integer not null,
    cents bigint not null,
    tx_id varchar(64) not null,
    created_at timestamp not null default now()
);
