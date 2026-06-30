-- The real source of truth is inside backend/src/main/resources/db/migration, this is for reviewing snapshot context schema in a single file

alter table transactions
    add column if not exists source_url text,
    add column if not exists source_status text not null default 'official',
    add column if not exists confidence numeric(5,4) not null default 1.0000,
    add column if not exists affects_projection boolean not null default true,
    add column if not exists reported_at timestamptz;

create index if not exists idx_transactions_projection_window
    on transactions (transaction_date, affects_projection, source_status);

create table if not exists team_strength_ratings (
    id bigserial primary key,
    season_start_year integer not null,
    team_id bigint not null references teams(team_id),
    source_season_start_year integer,
    rating numeric(8,4) not null,
    base_win_percentage numeric(8,4),
    point_differential numeric(8,4),
    roster_impact_score numeric(8,4),
    injury_risk_score numeric(8,4),
    generated_at timestamptz not null default now(),
    explanation text,
    unique (season_start_year, team_id)
);

create table if not exists standings_projections (
    id bigserial primary key,
    season_start_year integer not null,
    team_id bigint not null references teams(team_id),
    conference text,
    projected_wins numeric(6,2) not null,
    projected_losses numeric(6,2) not null,
    low_wins numeric(6,2),
    median_wins numeric(6,2),
    high_wins numeric(6,2),
    projected_seed integer,
    playoff_probability numeric(6,4),
    projection_method text not null,
    schedule_available boolean not null default false,
    generated_at timestamptz not null default now(),
    top_reasons text,
    uncertainty_factors text,
    unique (season_start_year, team_id)
);

create table if not exists season_simulation_runs (
    id bigserial primary key,
    season_start_year integer not null,
    run_count integer not null,
    schedule_available boolean not null default false,
    generated_at timestamptz not null default now(),
    notes text
);

create table if not exists projected_team_records (
    id bigserial primary key,
    simulation_run_id bigint not null references season_simulation_runs(id) on delete cascade,
    team_id bigint not null references teams(team_id),
    expected_wins numeric(6,2) not null,
    expected_losses numeric(6,2) not null,
    low_wins numeric(6,2),
    median_wins numeric(6,2),
    high_wins numeric(6,2),
    conference_seed integer,
    playoff_probability numeric(6,4)
);

create table if not exists roster_change_events (
    id bigserial primary key,
    season_start_year integer not null,
    team_id bigint references teams(team_id),
    player_id bigint references players(player_id),
    event_type text not null,
    impact_score numeric(8,4),
    event_date date,
    source text,
    notes text,
    ingested_at timestamptz not null default now()
);

create table if not exists team_context_snapshots (
    id bigserial primary key,
    season_start_year integer not null,
    team_id bigint not null references teams(team_id),
    snapshot_date date not null default current_date,
    roster_turnover_score numeric(8,4),
    injury_risk_score numeric(8,4),
    team_strength_rating numeric(8,4),
    context_summary text,
    generated_at timestamptz not null default now(),
    unique (season_start_year, team_id, snapshot_date)
);

create index if not exists idx_standings_projections_season
    on standings_projections (season_start_year);

create index if not exists idx_team_strength_ratings_season
    on team_strength_ratings (season_start_year);

create index if not exists idx_roster_change_events_team_season
    on roster_change_events (team_id, season_start_year);
