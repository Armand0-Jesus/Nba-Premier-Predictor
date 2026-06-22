import shutil
import unittest
from pathlib import Path
from unittest.mock import patch

from app import main


class MainEndpointTests(unittest.TestCase):

    def test_phase_three_routes_are_registered(self):
        paths = {route.path for route in main.app.routes}

        self.assertIn("/train/player", paths)
        self.assertIn("/evaluate/player-baseline", paths)
        self.assertIn("/predict/player", paths)
        self.assertIn("/predict/player-stats", paths)
        self.assertIn("/predict/fantasy", paths)
        self.assertIn("/model/metrics", paths)
        self.assertIn("/model/versions", paths)

    def test_evaluate_player_baseline_stores_latest_metrics(self):
        rows = [
            training_row(10, 4, 3, 28, 19.3, "2023-10-01T19:00:00"),
            training_row(12, 5, 4, 29, 23.0, "2023-10-02T19:00:00"),
            training_row(20, 6, 5, 32, 36.7, "2023-10-03T19:00:00"),
            training_row(24, 8, 7, 34, 44.1, "2023-10-04T19:00:00"),
        ]

        with patch.object(main, "fetch_player_training_rows", return_value=rows):
            response = main.evaluate_player_baseline(season=2023, limit=4, train_ratio=0.75)

        self.assertEqual(3, response.train_rows)
        self.assertEqual(1, response.test_rows)
        self.assertIn("fantasy_points", response.metrics)
        self.assertEqual(response.metrics, main.model_metrics()["playerBaseline"]["metrics"])

    def test_train_player_baseline_updates_loaded_model(self):
        rows = [
            training_row(10, 4, 3, 28, 19.3),
            training_row(20, 6, 5, 32, 36.7),
            training_row(24, 8, 7, 34, 44.1),
        ]

        artifact_dir = Path("unit-test-artifacts")
        shutil.rmtree(artifact_dir, ignore_errors=True)
        artifact_path = artifact_dir / "player_baseline.joblib"
        try:
            with patch.object(main, "MODEL_ARTIFACT_PATH", artifact_path):
                with patch.object(main, "fetch_player_training_rows", return_value=rows):
                    response = main.train_player_baseline(season=2023, limit=3)

            self.assertEqual(3, response.trained_rows)
            self.assertTrue(artifact_path.exists())
            self.assertEqual(3, main.app.state.player_model.trained_rows)
        finally:
            shutil.rmtree(artifact_dir, ignore_errors=True)


def training_row(points, rebounds, assists, minutes, fantasy_points, game_time=None):
    return {
        "gameDateTime": game_time,
        "features": {
            "games_played_prior": 6,
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
