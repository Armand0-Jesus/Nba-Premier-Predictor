create table if not exists users (
    id bigserial primary key,
    email text not null unique,
    display_name text not null,
    created_at timestamptz not null default now()
);

create table if not exists teams (
    team_id bigint primary key,
    city text not null,
    name text not null,
    abbreviation text,
    season_founded integer,
    season_active_till integer,
    league text,
    updated_at timestamptz not null default now()
);

create table if not exists players (
    player_id bigint primary key,
    first_name text not null,
    last_name text,
    birth_date date,
    school text,
    country text,
    height_inches integer,
    body_weight_lbs integer,
    jersey text,
    is_guard boolean not null default false,
    is_forward boolean not null default false,
    is_center boolean not null default false,
    dleague_flag boolean not null default false,
    nba_flag boolean not null default false,
    games_played_flag boolean not null default false,
    draft_year integer,
    draft_round integer,
    draft_number integer,
    from_year integer,
    to_year integer,
    updated_at timestamptz not null default now()
);

create table if not exists seasons (
    season_start_year integer primary key,
    label text not null unique,
    starts_on date,
    ends_on date
);

create table if not exists games (
    game_id bigint primary key,
    season_start_year integer references seasons(season_start_year),
    game_date_time_est timestamp,
    game_date date,
    home_team_id bigint references teams(team_id),
    away_team_id bigint references teams(team_id),
    home_team_city text,
    home_team_name text,
    away_team_city text,
    away_team_name text,
    home_score integer,
    away_score integer,
    winner_team_id bigint,
    game_type text,
    game_subtype text,
    game_label text,
    game_sub_label text,
    series_game_number integer,
    attendance integer,
    arena_id bigint,
    arena_name text,
    arena_city text,
    arena_state text,
    officials text,
    updated_at timestamptz not null default now()
);

create table if not exists player_game_stats (
    id bigserial primary key,
    game_id bigint not null references games(game_id) on delete cascade,
    player_id bigint not null references players(player_id),
    team_id bigint references teams(team_id),
    opponent_team_id bigint references teams(team_id),
    win boolean,
    home boolean,
    num_minutes numeric(6,2),
    points integer,
    assists integer,
    blocks integer,
    steals integer,
    field_goals_attempted integer,
    field_goals_made integer,
    field_goals_percentage numeric(5,3),
    three_pointers_attempted integer,
    three_pointers_made integer,
    three_pointers_percentage numeric(5,3),
    free_throws_attempted integer,
    free_throws_made integer,
    free_throws_percentage numeric(5,3),
    rebounds_defensive integer,
    rebounds_offensive integer,
    rebounds_total integer,
    fouls_personal integer,
    turnovers integer,
    plus_minus_points integer,
    comment text,
    starting_position text,
    updated_at timestamptz not null default now(),
    unique (game_id, player_id, team_id)
);

create table if not exists team_game_stats (
    id bigserial primary key,
    game_id bigint not null references games(game_id) on delete cascade,
    team_id bigint not null references teams(team_id),
    opponent_team_id bigint references teams(team_id),
    home boolean,
    win boolean,
    team_score integer,
    opponent_score integer,
    assists integer,
    blocks integer,
    steals integer,
    field_goals_attempted integer,
    field_goals_made integer,
    field_goals_percentage numeric(5,3),
    three_pointers_attempted integer,
    three_pointers_made integer,
    three_pointers_percentage numeric(5,3),
    free_throws_attempted integer,
    free_throws_made integer,
    free_throws_percentage numeric(5,3),
    rebounds_defensive integer,
    rebounds_offensive integer,
    rebounds_total integer,
    fouls_personal integer,
    turnovers integer,
    plus_minus_points integer,
    num_minutes numeric(6,2),
    q1_points integer,
    q2_points integer,
    q3_points integer,
    q4_points integer,
    updated_at timestamptz not null default now(),
    unique (game_id, team_id)
);

create table if not exists model_versions (
    id bigserial primary key,
    version_name text not null unique,
    model_type text not null,
    target_variable text not null,
    trained_at timestamptz,
    training_data_start date,
    training_data_end date,
    feature_set_version text,
    artifact_path text,
    status text not null default 'candidate',
    is_active boolean not null default false,
    created_at timestamptz not null default now()
);

create table if not exists model_metrics (
    id bigserial primary key,
    model_version_id bigint references model_versions(id) on delete cascade,
    target_variable text not null,
    mae numeric(10,4),
    rmse numeric(10,4),
    r2_score numeric(10,4),
    validation_sample_size integer,
    evaluated_at timestamptz not null default now()
);

create table if not exists predictions (
    id bigserial primary key,
    prediction_type text not null,
    game_id bigint references games(game_id),
    player_id bigint references players(player_id),
    team_id bigint references teams(team_id),
    model_version_id bigint references model_versions(id),
    requested_at timestamptz not null default now(),
    data_cutoff_time timestamptz,
    input_fingerprint text,
    confidence_score numeric(6,4),
    explanation jsonb not null default '[]'::jsonb
);

create table if not exists player_stat_predictions (
    prediction_id bigint primary key references predictions(id) on delete cascade,
    projected_points numeric(6,2),
    projected_rebounds numeric(6,2),
    projected_assists numeric(6,2),
    projected_steals numeric(6,2),
    projected_blocks numeric(6,2),
    projected_turnovers numeric(6,2),
    projected_minutes numeric(6,2)
);

create table if not exists team_score_predictions (
    prediction_id bigint primary key references predictions(id) on delete cascade,
    home_team_score numeric(6,2),
    away_team_score numeric(6,2),
    predicted_winner_team_id bigint references teams(team_id),
    point_differential numeric(6,2)
);

create table if not exists fantasy_predictions (
    prediction_id bigint primary key references predictions(id) on delete cascade,
    fantasy_points numeric(6,2),
    floor_projection numeric(6,2),
    ceiling_projection numeric(6,2),
    risk_level text,
    scoring_formula jsonb not null default '{}'::jsonb
);

create table if not exists prediction_actuals (
    id bigserial primary key,
    prediction_id bigint not null references predictions(id) on delete cascade,
    actual_value jsonb not null,
    recorded_at timestamptz not null default now()
);

create table if not exists data_source_logs (
    id bigserial primary key,
    source_name text not null,
    source_type text not null,
    source_path text,
    ingested_at timestamptz not null default now(),
    status text not null,
    records_processed integer not null default 0,
    notes text
);

create table if not exists refresh_logs (
    id bigserial primary key,
    refresh_type text not null,
    started_at timestamptz not null default now(),
    finished_at timestamptz,
    status text not null,
    records_processed integer not null default 0,
    error_message text
);

create table if not exists raw_ingestion_records (
    id bigserial primary key,
    source_name text not null,
    source_record_id text,
    payload jsonb not null,
    ingested_at timestamptz not null default now()
);

create table if not exists ingestion_errors (
    id bigserial primary key,
    source_name text not null,
    source_record_id text,
    error_message text not null,
    raw_payload jsonb,
    created_at timestamptz not null default now()
);

create table if not exists transactions (
    id bigserial primary key,
    player_id bigint references players(player_id),
    from_team_id bigint references teams(team_id),
    to_team_id bigint references teams(team_id),
    transaction_type text not null,
    transaction_date date,
    source text,
    notes text,
    ingested_at timestamptz not null default now()
);

create table if not exists roster_snapshots (
    id bigserial primary key,
    snapshot_date date not null,
    team_id bigint not null references teams(team_id),
    player_id bigint not null references players(player_id),
    position text,
    roster_status text,
    projected_minutes numeric(5,2),
    source text,
    ingested_at timestamptz not null default now(),
    unique (snapshot_date, team_id, player_id)
);

create table if not exists injury_reports (
    id bigserial primary key,
    report_date date not null,
    game_date date,
    team_id bigint references teams(team_id),
    player_id bigint references players(player_id),
    injury_status text not null,
    reason text,
    source text,
    confidence numeric(5,4),
    valid_until timestamptz,
    scraped_at timestamptz not null default now(),
    ingested_at timestamptz not null default now()
);

create table if not exists draft_picks (
    id bigserial primary key,
    player_id bigint references players(player_id),
    team_id bigint references teams(team_id),
    draft_year integer not null,
    draft_round integer,
    draft_number integer,
    rookie_season_start_year integer
);

create table if not exists depth_chart_snapshots (
    id bigserial primary key,
    snapshot_time timestamptz not null,
    team_id bigint not null references teams(team_id),
    player_id bigint not null references players(player_id),
    position text,
    depth_chart_rank integer,
    source text,
    ingested_at timestamptz not null default now()
);

create table if not exists starter_snapshots (
    id bigserial primary key,
    snapshot_time timestamptz not null,
    game_id bigint references games(game_id),
    team_id bigint references teams(team_id),
    player_id bigint references players(player_id),
    projected_starter boolean not null default false,
    confirmed_starter boolean not null default false,
    source text,
    ingested_at timestamptz not null default now()
);

create table if not exists team_advanced_stats (
    id bigserial primary key,
    team_id bigint not null references teams(team_id),
    season_start_year integer references seasons(season_start_year),
    game_id bigint references games(game_id),
    pace numeric(8,3),
    offensive_rating numeric(8,3),
    defensive_rating numeric(8,3),
    net_rating numeric(8,3),
    source text,
    ingested_at timestamptz not null default now()
);

create table if not exists player_advanced_stats (
    id bigserial primary key,
    player_id bigint not null references players(player_id),
    team_id bigint references teams(team_id),
    season_start_year integer references seasons(season_start_year),
    game_id bigint references games(game_id),
    usage_percentage numeric(8,4),
    true_shooting_percentage numeric(8,4),
    effective_field_goal_percentage numeric(8,4),
    pace numeric(8,3),
    offensive_rating numeric(8,3),
    defensive_rating numeric(8,3),
    net_rating numeric(8,3),
    source text,
    ingested_at timestamptz not null default now()
);

create table if not exists player_feature_snapshots (
    id bigserial primary key,
    snapshot_time timestamptz not null,
    game_id bigint not null references games(game_id),
    player_id bigint not null references players(player_id),
    team_id bigint references teams(team_id),
    generated_at timestamptz not null default now(),
    data_cutoff_time timestamptz not null,
    model_version_id bigint references model_versions(id),
    features jsonb not null default '{}'::jsonb,
    unique (snapshot_time, game_id, player_id)
);

create table if not exists team_feature_snapshots (
    id bigserial primary key,
    snapshot_time timestamptz not null,
    game_id bigint not null references games(game_id),
    team_id bigint not null references teams(team_id),
    generated_at timestamptz not null default now(),
    data_cutoff_time timestamptz not null,
    model_version_id bigint references model_versions(id),
    features jsonb not null default '{}'::jsonb,
    unique (snapshot_time, game_id, team_id)
);

create table if not exists game_feature_snapshots (
    id bigserial primary key,
    snapshot_time timestamptz not null,
    game_id bigint not null references games(game_id),
    generated_at timestamptz not null default now(),
    data_cutoff_time timestamptz not null,
    model_version_id bigint references model_versions(id),
    features jsonb not null default '{}'::jsonb,
    unique (snapshot_time, game_id)
);

create table if not exists model_training_runs (
    id bigserial primary key,
    started_at timestamptz not null default now(),
    finished_at timestamptz,
    status text not null,
    training_data_range text,
    validation_data_range text,
    triggered_by text,
    notes text
);

create table if not exists model_registry (
    id bigserial primary key,
    model_version_id bigint not null references model_versions(id) on delete cascade,
    registry_status text not null,
    registered_at timestamptz not null default now(),
    archived_at timestamptz
);

create table if not exists prediction_errors (
    id bigserial primary key,
    prediction_id bigint not null references predictions(id) on delete cascade,
    target_variable text not null,
    predicted_value numeric(10,4),
    actual_value numeric(10,4),
    absolute_error numeric(10,4),
    squared_error numeric(12,4),
    recorded_at timestamptz not null default now()
);

create table if not exists model_promotion_history (
    id bigserial primary key,
    previous_model_version_id bigint references model_versions(id),
    candidate_model_version_id bigint references model_versions(id),
    promoted boolean not null,
    reason text not null,
    previous_mae numeric(10,4),
    candidate_mae numeric(10,4),
    promoted_at timestamptz not null default now()
);

create index if not exists idx_players_name on players (last_name, first_name);
create index if not exists idx_games_date on games (game_date_time_est);
create index if not exists idx_games_season on games (season_start_year);
create index if not exists idx_games_home_team on games (home_team_id);
create index if not exists idx_games_away_team on games (away_team_id);
create index if not exists idx_player_game_stats_player on player_game_stats (player_id);
create index if not exists idx_player_game_stats_game on player_game_stats (game_id);
create index if not exists idx_team_game_stats_team on team_game_stats (team_id);
create index if not exists idx_predictions_requested_at on predictions (requested_at);
