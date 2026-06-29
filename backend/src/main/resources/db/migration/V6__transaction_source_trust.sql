alter table transactions
    add column if not exists source_url text,
    add column if not exists source_status text not null default 'official',
    add column if not exists confidence numeric(5,4) not null default 1.0000,
    add column if not exists affects_projection boolean not null default true,
    add column if not exists reported_at timestamptz;

create index if not exists idx_transactions_projection_window
    on transactions (transaction_date, affects_projection, source_status);
