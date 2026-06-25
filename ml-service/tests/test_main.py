import shutil
import unittest
import warnings
from pathlib import Path
from unittest.mock import call, patch

warnings.filterwarnings(
    "ignore",
    message="Using `httpx` with `starlette.testclient` is deprecated.*",
)

from fastapi.testclient import TestClient

from app import main
from app.baseline_model import PlayerBaselineModel
from app.game_score_model import GameScoreBaselineModel


class MainEndpointTests(unittest.TestCase):

    def setUp(self):
        self.previous_model = main.app.state.player_model
        self.previous_metrics = main.app.state.player_metrics
        self.previous_game_score_model = main.app.state.game_score_model
        self.previous_game_score_metrics = main.app.state.game_score_metrics

    def tearDown(self):
        main.app.state.player_model = self.previous_model
        main.app.state.player_metrics = self.previous_metrics
        main.app.state.game_score_model = self.previous_game_score_model
        main.app.state.game_score_metrics = self.previous_game_score_metrics

    def test_phase_three_routes_are_registered(self):
        paths = {route.path for route in main.app.routes}

        self.assertIn("/train/player", paths)
        self.assertIn("/train/game-score", paths)
        self.assertIn("/evaluate/player-baseline", paths)
        self.assertIn("/evaluate/game-score", paths)
        self.assertIn("/predict/player", paths)
        self.assertIn("/predict/player-stats", paths)
        self.assertIn("/predict/fantasy", paths)
        self.assertIn("/predict/game-score", paths)
        self.assertIn("/model/metrics", paths)
        self.assertIn("/model/versions", paths)

    def test_health_reports_player_and_game_score_model_status(self):
        main.app.state.player_model = PlayerBaselineModel(trained_rows=30)
        main.app.state.game_score_model = GameScoreBaselineModel(trained_rows=12)

        with TestClient(main.app) as client:
            response = client.get("/health")

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("ok", body["status"])
        self.assertEqual("trained", body["models"]["player"]["status"])
        self.assertEqual(30, body["models"]["player"]["trainedRows"])
        self.assertEqual("trained", body["models"]["gameScore"]["status"])
        self.assertEqual(12, body["models"]["gameScore"]["trainedRows"])

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
        self.assertIn("feature_average", response.baseline_metrics)
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

    def test_fetch_player_training_rows_pages_backend_requests(self):
        pages = [
            [{"row": 1}, {"row": 2}],
            [{"row": 3}],
        ]

        with patch.object(main, "BACKEND_PAGE_SIZE", 2):
            with patch.object(main, "fetch_player_training_page", side_effect=pages) as fetch_page:
                rows = main.fetch_player_training_rows(season=2023, limit=5)

        self.assertEqual([{"row": 1}, {"row": 2}, {"row": 3}], rows)
        self.assertEqual([
            call(2023, 2, 0),
            call(2023, 2, 2),
        ], fetch_page.mock_calls)

    def test_fetch_game_score_training_rows_pages_backend_requests(self):
        pages = [
            [{"row": 1}, {"row": 2}],
            [{"row": 3}],
        ]

        with patch.object(main, "BACKEND_PAGE_SIZE", 2):
            with patch.object(main, "fetch_game_score_training_page", side_effect=pages) as fetch_page:
                rows = main.fetch_game_score_training_rows(season=2023, limit=5)

        self.assertEqual([{"row": 1}, {"row": 2}, {"row": 3}], rows)
        self.assertEqual([
            call(2023, 2, 0),
            call(2023, 2, 2),
        ], fetch_page.mock_calls)

    def test_predict_player_accepts_valid_request(self):
        main.app.state.player_model = PlayerBaselineModel.fit([
            training_row(10, 4, 3, 28, 19.3),
            training_row(20, 6, 5, 32, 36.7),
            training_row(24, 8, 7, 34, 44.1),
        ])

        with TestClient(main.app) as client:
            response = client.post("/predict/player", json=prediction_request())

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual(201939, body["player_id"])
        self.assertEqual(3, body["trained_rows"])
        self.assertGreater(body["projected_points"], 0)
        self.assertGreater(body["fantasy_points"], body["projected_points"])

    def test_predict_fantasy_accepts_same_request_format(self):
        main.app.state.player_model = PlayerBaselineModel.fit([
            training_row(10, 4, 3, 28, 19.3),
            training_row(20, 6, 5, 32, 36.7),
            training_row(24, 8, 7, 34, 44.1),
        ])

        with TestClient(main.app) as client:
            response = client.post("/predict/fantasy", json=prediction_request())

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual(201939, body["player_id"])
        self.assertIn("fantasy_floor", body)
        self.assertIn("fantasy_ceiling", body)
        self.assertGreater(body["confidence_score"], 0)

    def test_predict_game_score_accepts_valid_request(self):
        main.app.state.game_score_model = GameScoreBaselineModel.fit([
            game_score_training_row(100, 90, "2024-01-01T22:00:00"),
            game_score_training_row(110, 105, "2024-01-03T22:00:00"),
            game_score_training_row(130, 120, "2024-01-05T22:00:00"),
        ])

        with TestClient(main.app) as client:
            response = client.post("/predict/game-score", json=game_score_prediction_request())

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual(22300003, body["game_id"])
        self.assertEqual(1610612744, body["home_team_id"])
        self.assertEqual(1610612747, body["away_team_id"])
        self.assertGreater(body["home_team_score"], 0)
        self.assertGreater(body["away_team_score"], 0)
        self.assertIn(body["predicted_winner_team_id"], [1610612744, 1610612747])

    def test_prediction_request_requires_player_id_and_features(self):
        with TestClient(main.app) as client:
            missing_player = client.post("/predict/player", json={
                "features": {"games_played_prior": 5},
            })
            empty_features = client.post("/predict/player", json={
                "player_id": 201939,
                "features": {},
            })

        self.assertEqual(422, missing_player.status_code)
        self.assertEqual(422, empty_features.status_code)
        self.assertIn("player_id", str(missing_player.json()["detail"]))
        self.assertIn("features", str(empty_features.json()["detail"]))

    def test_game_score_prediction_request_requires_teams_and_features(self):
        with TestClient(main.app) as client:
            missing_team = client.post("/predict/game-score", json={
                "game_id": 22300003,
                "home_team_id": 1610612744,
                "features": {"home_last_5_team_score_avg": 110.0},
            })
            empty_features = client.post("/predict/game-score", json={
                "game_id": 22300003,
                "home_team_id": 1610612744,
                "away_team_id": 1610612747,
                "features": {},
            })

        self.assertEqual(422, missing_team.status_code)
        self.assertEqual(422, empty_features.status_code)
        self.assertIn("away_team_id", str(missing_team.json()["detail"]))
        self.assertIn("features", str(empty_features.json()["detail"]))

    def test_train_player_fetches_backend_rows_and_saves_artifact(self):
        rows = [
            training_row(10, 4, 3, 28, 19.3),
            training_row(20, 6, 5, 32, 36.7),
            training_row(24, 8, 7, 34, 44.1),
        ]
        artifact_dir = Path("unit-test-artifacts")
        shutil.rmtree(artifact_dir, ignore_errors=True)
        artifact_path = artifact_dir / "player_baseline.joblib"
        backend_response = BackendResponse(rows)

        try:
            with patch.object(main, "MODEL_ARTIFACT_PATH", artifact_path):
                with patch.object(main.urllib.request, "urlopen", return_value=backend_response) as urlopen:
                    with TestClient(main.app) as client:
                        response = client.post("/train/player?season=2023&limit=3")

            self.assertEqual(200, response.status_code)
            self.assertEqual(3, response.json()["trained_rows"])
            self.assertTrue(artifact_path.exists())
            self.assertEqual(1, urlopen.call_count)
            requested_url = urlopen.call_args.args[0]
            self.assertIn("/api/training-data/player-stats", requested_url)
            self.assertIn("limit=3", requested_url)
            self.assertIn("offset=0", requested_url)
            self.assertIn("season=2023", requested_url)
        finally:
            shutil.rmtree(artifact_dir, ignore_errors=True)

    def test_train_game_score_fetches_backend_rows_and_saves_artifact(self):
        rows = [
            game_score_training_row(100, 90, "2024-01-01T22:00:00"),
            game_score_training_row(110, 105, "2024-01-03T22:00:00"),
            game_score_training_row(130, 120, "2024-01-05T22:00:00"),
        ]
        artifact_dir = Path("unit-test-artifacts")
        shutil.rmtree(artifact_dir, ignore_errors=True)
        artifact_path = artifact_dir / "game_score_baseline.joblib"
        backend_response = BackendResponse(rows)

        try:
            with patch.object(main, "GAME_SCORE_ARTIFACT_PATH", artifact_path):
                with patch.object(main.urllib.request, "urlopen", return_value=backend_response) as urlopen:
                    with TestClient(main.app) as client:
                        response = client.post("/train/game-score?season=2023&limit=3")

            self.assertEqual(200, response.status_code)
            self.assertEqual(3, response.json()["trained_rows"])
            self.assertTrue(artifact_path.exists())
            requested_url = urlopen.call_args.args[0]
            self.assertIn("/api/training-data/game-scores", requested_url)
            self.assertIn("limit=3", requested_url)
            self.assertIn("offset=0", requested_url)
            self.assertIn("season=2023", requested_url)
        finally:
            shutil.rmtree(artifact_dir, ignore_errors=True)


class BackendResponse:
    status = 200

    def __init__(self, rows):
        self.rows = rows

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return False

    def read(self):
        import json
        return json.dumps(self.rows).encode("utf-8")


def prediction_request():
    return {
        "game_id": 22300001,
        "player_id": 201939,
        "team_id": 1610612744,
        "data_cutoff_time": "2024-01-01T21:59:59",
        "features": {
            "games_played_prior": 6,
            "last_5_points_avg": 18.0,
            "season_points_avg": 18.0,
            "last_5_rebounds_avg": 6.0,
            "last_5_assists_avg": 5.0,
            "last_5_minutes_avg": 31.0,
            "is_home": True,
        },
    }


def game_score_prediction_request():
    return {
        "game_id": 22300003,
        "home_team_id": 1610612744,
        "away_team_id": 1610612747,
        "data_cutoff_time": "2024-01-05T21:59:59",
        "features": {
            "home_games_played_prior": 2,
            "away_games_played_prior": 2,
            "home_last_5_team_score_avg": 105.0,
            "away_last_5_team_score_avg": 97.5,
            "season_point_differential_delta": 15.0,
        },
    }


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


def game_score_training_row(home_score, away_score, game_time):
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
