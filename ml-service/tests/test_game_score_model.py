import unittest

from app.game_score_model import GameScoreBaselineModel


class GameScoreBaselineModelTests(unittest.TestCase):

    def test_fallback_prediction_uses_team_feature_averages(self):
        prediction = GameScoreBaselineModel().predict({
            "home_last_5_team_score_avg": 112.0,
            "home_season_team_score_avg": 110.0,
            "away_last_5_team_score_avg": 104.0,
            "away_season_team_score_avg": 106.0,
            "home_games_played_prior": 12,
            "away_games_played_prior": 12,
        }, 1610612744, 1610612747)

        self.assertEqual(111.0, prediction["home_team_score"])
        self.assertEqual(105.0, prediction["away_team_score"])
        self.assertEqual(1610612744, prediction["predicted_winner_team_id"])
        self.assertEqual(6.0, prediction["point_differential"])
        self.assertGreater(prediction["confidence_score"], 0)

    def test_exact_tie_does_not_choose_home_team(self):
        prediction = GameScoreBaselineModel().predict({
            "home_last_5_team_score_avg": 108.0,
            "away_last_5_team_score_avg": 108.0,
        }, 1610612744, 1610612747)

        self.assertEqual(108.0, prediction["home_team_score"])
        self.assertEqual(108.0, prediction["away_team_score"])
        self.assertEqual(0.0, prediction["point_differential"])
        self.assertIsNone(prediction["predicted_winner_team_id"])

    def test_fit_trains_game_score_model(self):
        model = GameScoreBaselineModel.fit([
            training_row(100, 90, "2024-01-01T22:00:00"),
            training_row(110, 105, "2024-01-03T22:00:00"),
            training_row(130, 120, "2024-01-05T22:00:00"),
        ])

        prediction = model.predict({
            "home_last_5_team_score_avg": 115.0,
            "away_last_5_team_score_avg": 107.0,
            "season_point_differential_delta": 8.0,
        }, 1610612744, 1610612747)

        self.assertEqual(3, model.trained_rows)
        self.assertGreater(prediction["home_team_score"], 0)
        self.assertGreater(prediction["away_team_score"], 0)
        self.assertIn(prediction["predicted_winner_team_id"], [1610612744, 1610612747])

    def test_evaluate_time_split_reports_score_metrics(self):
        evaluation = GameScoreBaselineModel.evaluate_time_split([
            training_row(100, 90, "2024-01-01T22:00:00"),
            training_row(110, 105, "2024-01-03T22:00:00"),
            training_row(130, 120, "2024-01-05T22:00:00"),
        ], train_ratio=0.67)

        self.assertEqual(2, evaluation["train_rows"])
        self.assertEqual(1, evaluation["test_rows"])
        self.assertIn("home_team_score", evaluation["metrics"])
        self.assertIn("away_team_score", evaluation["metrics"])
        self.assertEqual("time_grouped_by_game_datetime", evaluation["split_strategy"])
        self.assertIn("feature_average", evaluation["baseline_metrics"])
        self.assertIn("training_mean", evaluation["baseline_metrics"])
        self.assertIn("home_team_score", evaluation["baseline_metrics"]["feature_average"])

    def test_evaluate_time_split_keeps_same_timestamp_on_one_side(self):
        evaluation = GameScoreBaselineModel.evaluate_time_split([
            training_row(100, 90, "2024-01-01T22:00:00"),
            training_row(110, 105, "2024-01-01T22:00:00"),
            training_row(130, 120, "2024-01-05T22:00:00"),
            training_row(118, 115, "2024-01-05T22:00:00"),
        ], train_ratio=0.5)

        self.assertEqual(2, evaluation["train_rows"])
        self.assertEqual(2, evaluation["test_rows"])
        self.assertEqual(1, evaluation["train_groups"])
        self.assertEqual(1, evaluation["test_groups"])
        self.assertEqual("2024-01-01T22:00:00", evaluation["training_data_end"])
        self.assertEqual("2024-01-05T22:00:00", evaluation["validation_data_start"])


def training_row(home_score, away_score, game_time):
    return {
        "gameDateTime": game_time,
        "homeTeamId": 1610612744,
        "awayTeamId": 1610612747,
        "features": {
            "home_last_5_team_score_avg": float(home_score),
            "away_last_5_team_score_avg": float(away_score),
            "season_point_differential_delta": float(home_score - away_score),
        },
        "targets": {
            "homeScore": home_score,
            "awayScore": away_score,
            "winnerTeamId": 1610612744 if home_score >= away_score else 1610612747,
            "pointDifferential": home_score - away_score,
        },
    }


if __name__ == "__main__":
    unittest.main()
