import unittest

from app.baseline_model import PlayerBaselineModel


class PlayerBaselineModelTests(unittest.TestCase):

    def test_fallback_prediction_uses_pre_game_feature_averages(self):
        model = PlayerBaselineModel()

        prediction = model.predict({
            "games_played_prior": 6,
            "last_5_points_avg": 22.0,
            "last_5_rebounds_avg": 7.0,
            "last_5_assists_avg": 5.0,
            "last_5_minutes_avg": 33.0,
        })

        self.assertEqual(22.0, prediction["projected_points"])
        self.assertEqual(7.0, prediction["projected_rebounds"])
        self.assertEqual(5.0, prediction["projected_assists"])
        self.assertEqual(33.0, prediction["projected_minutes"])
        self.assertEqual(37.9, prediction["fantasy_points"])
        self.assertEqual("medium", prediction["risk_level"])
        self.assertGreater(prediction["confidence_score"], 0)

    def test_fit_trains_model_from_backend_training_rows(self):
        rows = [
            training_row(10, 4, 3, 28, 19.3),
            training_row(20, 6, 5, 32, 36.7),
            training_row(24, 8, 7, 34, 44.1),
        ]

        model = PlayerBaselineModel.fit(rows)
        prediction = model.predict({
            "games_played_prior": 4,
            "last_5_points_avg": 18.0,
            "season_points_avg": 18.0,
            "last_5_rebounds_avg": 6.0,
            "last_5_assists_avg": 5.0,
            "last_5_minutes_avg": 31.0,
        })

        self.assertEqual(3, model.trained_rows)
        self.assertGreater(prediction["projected_points"], 0)
        self.assertGreater(prediction["fantasy_points"], prediction["projected_points"])
        self.assertGreater(prediction["confidence_score"], 0.45)
        self.assertTrue(prediction["factors"])

    def test_evaluate_time_split_reports_holdout_metrics(self):
        rows = [
            training_row(10, 4, 3, 28, 19.3, "2023-10-01T19:00:00"),
            training_row(12, 5, 4, 29, 23.0, "2023-10-02T19:00:00"),
            training_row(20, 6, 5, 32, 36.7, "2023-10-03T19:00:00"),
            training_row(24, 8, 7, 34, 44.1, "2023-10-04T19:00:00"),
        ]

        evaluation = PlayerBaselineModel.evaluate_time_split(rows, train_ratio=0.75)

        self.assertEqual(3, evaluation["train_rows"])
        self.assertEqual(1, evaluation["test_rows"])
        self.assertEqual("2023-10-01T19:00:00", evaluation["training_data_start"])
        self.assertEqual("2023-10-04T19:00:00", evaluation["validation_data_end"])
        self.assertIn("projected_points", evaluation["metrics"])
        self.assertIn("mae", evaluation["metrics"]["fantasy_points"])
        self.assertIn("rmse", evaluation["metrics"]["fantasy_points"])


def training_row(points, rebounds, assists, minutes, fantasy_points, game_time=None):
    return {
        "gameDateTime": game_time,
        "features": {
            "games_played_prior": 2,
            "last_5_points_avg": float(points),
            "season_points_avg": float(points),
            "last_5_rebounds_avg": float(rebounds),
            "last_5_assists_avg": float(assists),
            "last_5_minutes_avg": float(minutes),
        },
        "targets": {
            "points": points,
            "rebounds": rebounds,
            "assists": assists,
            "minutes": minutes,
            "fantasyPoints": fantasy_points,
        },
    }


if __name__ == "__main__":
    unittest.main()
