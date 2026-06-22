alter table player_feature_snapshots
    alter column snapshot_time type timestamp using snapshot_time at time zone 'America/New_York',
    alter column data_cutoff_time type timestamp using data_cutoff_time at time zone 'America/New_York';

alter table team_feature_snapshots
    alter column snapshot_time type timestamp using snapshot_time at time zone 'America/New_York',
    alter column data_cutoff_time type timestamp using data_cutoff_time at time zone 'America/New_York';

alter table game_feature_snapshots
    alter column snapshot_time type timestamp using snapshot_time at time zone 'America/New_York',
    alter column data_cutoff_time type timestamp using data_cutoff_time at time zone 'America/New_York';
