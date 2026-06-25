alter table team_score_predictions
    add column if not exists home_team_id bigint references teams(team_id),
    add column if not exists away_team_id bigint references teams(team_id);
