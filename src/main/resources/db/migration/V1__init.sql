create table x402_payment_intents (
    id uuid primary key,
    merchant_id varchar(128) not null,
    endpoint varchar(256) not null,
    asset varchar(32) not null,
    amount bigint not null,
    payer varchar(128) not null,
    payee varchar(128) not null,
    idempotency_key varchar(128) not null,
    status varchar(32) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create unique index idx_x402_intent_merchant_endpoint_idem
    on x402_payment_intents (merchant_id, endpoint, idempotency_key);

create table x402_payment_authorizations (
    id uuid primary key,
    payment_intent_id uuid not null,
    payer varchar(42) not null,
    payee varchar(42) not null,
    transfer_value numeric(38, 0) not null,
    valid_after bigint not null,
    valid_before bigint not null,
    nonce varchar(64) not null,
    digest varchar(64) not null,
    sig_v integer not null,
    sig_r varchar(66) not null,
    sig_s varchar(66) not null,
    status varchar(32) not null,
    consumed boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create unique index idx_x402_auth_intent on x402_payment_authorizations (payment_intent_id);
create unique index idx_x402_auth_digest on x402_payment_authorizations (digest);
create unique index idx_x402_auth_payer_nonce on x402_payment_authorizations (payer, nonce);

create table x402_payment_settlements (
    id uuid primary key,
    payment_intent_id uuid not null,
    authorization_id uuid not null,
    settlement_ref varchar(128) not null,
    status varchar(32) not null,
    tx_hash varchar(66),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create unique index idx_x402_settlement_intent on x402_payment_settlements (payment_intent_id);
create unique index idx_x402_settlement_auth on x402_payment_settlements (authorization_id);

create table x402_payment_audit_logs (
    id uuid primary key,
    payment_intent_id uuid not null,
    allowed boolean not null,
    event_type varchar(64) not null,
    reason varchar(300) not null,
    created_at timestamp(6) with time zone not null
);

create index idx_x402_audit_intent on x402_payment_audit_logs (payment_intent_id);

create table x402_payment_ledger_entries (
    id uuid primary key,
    payment_intent_id uuid not null,
    type varchar(16) not null,
    asset varchar(32) not null,
    amount bigint not null,
    payer varchar(128) not null,
    payee varchar(128) not null,
    created_at timestamp(6) with time zone not null
);

create index idx_x402_ledger_intent on x402_payment_ledger_entries (payment_intent_id);
