insert into teams (
    team_id, city, name, abbreviation, season_founded, season_active_till, league
) values
    (1610612744, 'Golden State', 'Warriors', 'GSW', 1946, 2100, 'NBA'),
    (1610612747, 'Los Angeles', 'Lakers', 'LAL', 1948, 2100, 'NBA');

insert into players (
    player_id, first_name, last_name, birth_date, school, country, height_inches, body_weight_lbs,
    jersey, is_guard, is_forward, is_center, dleague_flag, nba_flag, games_played_flag,
    draft_year, draft_round, draft_number, from_year, to_year
) values (
    201939, 'Stephen', 'Curry', '1988-03-14', 'Davidson', 'USA', 74, 185,
    '30', true, false, false, false, true, true, 2009, 1, 7, 2009, 2026
);

insert into games (
    game_id, season_start_year, game_date_time_est, game_date, home_team_id, away_team_id,
    home_team_city, home_team_name, away_team_city, away_team_name, home_score, away_score,
    winner_team_id, game_type, game_label, arena_name, arena_city, arena_state
) values
    (22300001, 2023, '2024-01-01 22:00:00', '2024-01-01', 1610612744, 1610612747,
     'Golden State', 'Warriors', 'Los Angeles', 'Lakers', 100, 90,
     1610612744, 'Regular Season', 'Regular Season', 'Chase Center', 'San Francisco', 'CA'),
    (22300002, 2023, '2024-01-03 22:00:00', '2024-01-03', 1610612747, 1610612744,
     'Los Angeles', 'Lakers', 'Golden State', 'Warriors', 105, 110,
     1610612744, 'Regular Season', 'Regular Season', 'Crypto.com Arena', 'Los Angeles', 'CA'),
    (22300003, 2023, '2024-01-05 22:00:00', '2024-01-05', 1610612744, 1610612747,
     'Golden State', 'Warriors', 'Los Angeles', 'Lakers', 130, 120,
     1610612744, 'Regular Season', 'Regular Season', 'Chase Center', 'San Francisco', 'CA');

insert into player_game_stats (
    game_id, player_id, team_id, opponent_team_id, win, home, num_minutes,
    points, assists, blocks, steals, rebounds_total, fouls_personal, turnovers,
    plus_minus_points, starting_position
) values
    (22300001, 201939, 1610612744, 1610612747, true, true, 30.00,
     10, 4, 0, 1, 5, 2, 2, 10, 'G'),
    (22300002, 201939, 1610612744, 1610612747, true, false, 32.00,
     20, 6, 0, 1, 7, 2, 3, 5, 'G'),
    (22300003, 201939, 1610612744, 1610612747, true, true, 36.00,
     999, 99, 0, 1, 99, 2, 3, 15, 'G');

insert into team_game_stats (
    game_id, team_id, opponent_team_id, home, win, team_score, opponent_score,
    assists, blocks, steals, rebounds_total, turnovers, num_minutes
) values
    (22300001, 1610612744, 1610612747, true, true, 100, 90, 25, 3, 6, 44, 12, 240.00),
    (22300001, 1610612747, 1610612744, false, false, 90, 100, 22, 3, 6, 40, 14, 240.00),
    (22300002, 1610612747, 1610612744, true, false, 105, 110, 24, 4, 7, 41, 12, 240.00),
    (22300002, 1610612744, 1610612747, false, true, 110, 105, 26, 4, 7, 45, 11, 240.00),
    (22300003, 1610612744, 1610612747, true, true, 999, 120, 30, 5, 8, 48, 10, 240.00),
    (22300003, 1610612747, 1610612744, false, false, 120, 999, 25, 4, 8, 42, 12, 240.00);
