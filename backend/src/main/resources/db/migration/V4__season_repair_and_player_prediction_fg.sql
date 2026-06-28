insert into seasons (season_start_year, label, starts_on, ends_on)
values (2019, '2019-20', '2019-10-01', '2020-10-13')
on conflict (season_start_year) do update set
    label = excluded.label,
    ends_on = excluded.ends_on;

update games
set season_start_year = 2019,
    updated_at = now()
where game_date between '2020-07-01' and '2020-10-13'
  and season_start_year <> 2019;

alter table player_stat_predictions
    add column if not exists projected_field_goal_percentage numeric(6,4);
