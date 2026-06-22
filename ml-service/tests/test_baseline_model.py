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


def training_row(points, rebounds, assists, minutes, fantasy_points):
    return {
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
