insert into teams (
    team_id, city, name, abbreviation, season_founded, season_active_till, league
) values
    (1610612744, 'Golden State', 'Warriors', 'GSW', 1946, 2100, 'NBA'),
    (1610612747, 'Los Angeles', 'Lakers', 'LAL', 1948, 2100, 'NBA'),
    (9040, 'All-Star', 'All-Star LeBron', 'LBN', 2018, 2100, 'NBA'),
    (1610610036, 'Sheboygan', 'Red Skins', 'SHE', 1949, 1950, 'NBA');

insert into players (
    player_id, first_name, last_name, birth_date, school, country, height_inches, body_weight_lbs,
    jersey, is_guard, is_forward, is_center, dleague_flag, nba_flag, games_played_flag,
    draft_year, draft_round, draft_number, from_year, to_year
) values
    (201939, 'Stephen', 'Curry', '1988-03-14', 'Davidson', 'USA', 74, 185,
     '30', true, false, false, false, true, true, 2009, 1, 7, 2009, 2026),
    (2544, 'LeBron', 'James', '1984-12-30', null, 'USA', 81, 250,
     '23', false, true, false, false, true, true, 2003, 1, 1, 2003, 2026),
    (893, 'Michael', 'Jordan', '1963-02-17', 'North Carolina', 'USA', 78, 216,
     '23', true, false, false, false, true, true, 1984, 1, 3, 1984, 2002);

insert into games (
    game_id, season_start_year, game_date_time_est, game_date, home_team_id, away_team_id,
    home_team_city, home_team_name, away_team_city, away_team_name, home_score, away_score,
    winner_team_id, game_type, game_label, arena_name, arena_city, arena_state
) values (
    12300000, 2023, '2023-10-10 22:00:00', '2023-10-10', 1610612744, 1610612747,
    'Golden State', 'Warriors', 'Los Angeles', 'Lakers', 98, 101,
    1610612747, 'Preseason', 'Preseason', 'Chase Center', 'San Francisco', 'CA'
), (
    12300001, 2023, '2024-01-15 22:00:00', '2024-01-15', 1610612744, 1610612747,
    'Golden State', 'Warriors', 'Los Angeles', 'Lakers', 120, 115,
    1610612744, 'Regular Season', 'Regular Season', 'Chase Center', 'San Francisco', 'CA'
), (
    12300002, 2023, '2023-12-01 22:00:00', '2023-12-01', 1610612744, 1610612747,
    'Golden State', 'Warriors', 'Los Angeles', 'Lakers', 112, 108,
    1610612744, 'NBA Emirates Cup', 'NBA Cup', 'Chase Center', 'San Francisco', 'CA'
);

insert into player_game_stats (
    game_id, player_id, team_id, opponent_team_id, win, home, num_minutes,
    points, assists, blocks, steals, field_goals_attempted, field_goals_made,
    field_goals_percentage, three_pointers_attempted, three_pointers_made,
    three_pointers_percentage, free_throws_attempted, free_throws_made,
    free_throws_percentage, rebounds_defensive, rebounds_offensive, rebounds_total,
    fouls_personal, turnovers, plus_minus_points, starting_position
) values
    (12300000, 201939, 1610612744, 1610612747, false, true, 19.00,
     12, 3, 0, 1, 10, 4, 0.400, 5, 2, 0.400, 2, 2, 1.000,
     2, 0, 2, 1, 2, -4, 'G'),
    (12300000, 2544, 1610612747, 1610612744, true, false, 21.00,
     16, 4, 1, 1, 12, 6, 0.500, 3, 1, 0.333, 4, 3, 0.750,
     4, 1, 5, 1, 2, 6, 'F'),
    (12300001, 201939, 1610612744, 1610612747, true, true, 35.50,
     32, 7, 1, 2, 22, 11, 0.500, 12, 6, 0.500, 4, 4, 1.000,
     4, 1, 5, 2, 3, 8, 'G'),
    (12300001, 2544, 1610612747, 1610612744, false, false, 36.00,
     28, 9, 0, 1, 20, 10, 0.500, 6, 2, 0.333, 7, 6, 0.857,
     6, 2, 8, 1, 4, -5, 'F'),
    (12300002, 201939, 1610612744, 1610612747, true, true, 33.00,
     24, 8, 0, 2, 19, 9, 0.474, 9, 4, 0.444, 2, 2, 1.000,
     3, 1, 4, 2, 2, 7, 'G'),
    (12300002, 2544, 1610612747, 1610612744, false, false, 34.00,
     25, 6, 1, 1, 18, 8, 0.444, 5, 1, 0.200, 9, 8, 0.889,
     6, 2, 8, 1, 3, -7, 'F');

insert into team_game_stats (
    game_id, team_id, opponent_team_id, home, win, team_score, opponent_score,
    assists, blocks, steals, field_goals_made, field_goals_attempted,
    field_goals_percentage, rebounds_total, turnovers, num_minutes
) values
    (12300000, 1610612744, 1610612747, true, false, 98, 101, 21, 4, 6, 38, 88, 0.432, 37, 16, 240.00),
    (12300000, 1610612747, 1610612744, false, true, 101, 98, 24, 5, 7, 40, 90, 0.444, 39, 14, 240.00),
    (12300001, 1610612744, 1610612747, true, true, 120, 115, 29, 6, 8, 45, 92, 0.489, 44, 12, 240.00),
    (12300001, 1610612747, 1610612744, false, false, 115, 120, 25, 4, 7, 43, 88, 0.489, 42, 13, 240.00),
    (12300002, 1610612744, 1610612747, true, true, 112, 108, 27, 5, 8, 41, 86, 0.477, 40, 11, 240.00),
    (12300002, 1610612747, 1610612744, false, false, 108, 112, 23, 5, 6, 39, 85, 0.459, 41, 12, 240.00);
