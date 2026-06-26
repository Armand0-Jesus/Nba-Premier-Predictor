import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

import App from './App.jsx';

describe('NBA Premier Predictor frontend', () => {
  beforeEach(() => {
    window.fetch = vi.fn(defaultFetch);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    window.history.pushState({}, '', '/');
  });

  test('renders the cinematic landing page', () => {
    window.history.pushState({}, '', '/');

    render(<App />);

    expect(screen.getByRole('heading', { name: /NBA Premier Predictor/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Enter Dashboard/i })).toHaveAttribute('href', '/dashboard');
  });

  test('renders the landing route at mobile and desktop widths', () => {
    window.history.pushState({}, '', '/');
    setViewport(390, 844);
    const { unmount } = render(<App />);

    expect(screen.getByRole('heading', { name: /NBA Premier Predictor/i })).toBeInTheDocument();
    unmount();

    setViewport(1440, 900);
    render(<App />);

    expect(screen.getByRole('link', { name: /Game Score/i })).toBeInTheDocument();
  });

  test('submits a player prediction from a loaded snapshot', async () => {
    const user = userEvent.setup();
    window.history.pushState({}, '', '/predict/player');

    render(<App />);

    await user.click(screen.getByRole('button', { name: /Load Snapshot/i }));
    expect(await screen.findByText('#42')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^Predict$/i }));

    expect(await screen.findByText('Prediction Result')).toBeInTheDocument();
    expect(screen.getByText('28.4')).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith('/api/predictions/player', expect.objectContaining({ method: 'POST' }));
  });

  test('submits a game-score prediction from a loaded snapshot', async () => {
    const user = userEvent.setup();
    window.history.pushState({}, '', '/predict/game-score');

    render(<App />);

    await user.click(screen.getByRole('button', { name: /Load Snapshot/i }));
    expect(await screen.findByText('#77')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^Predict$/i }));

    expect(await screen.findByText('Game Score Result')).toBeInTheDocument();
    expect(screen.getByText('112.8')).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith('/api/predictions/game-score', expect.objectContaining({ method: 'POST' }));
  });

  test('renders model metrics and baseline comparison', async () => {
    window.history.pushState({}, '', '/model');

    render(<App />);

    expect(await screen.findByText('Model Metrics')).toBeInTheDocument();
    expect(screen.getByText(/MAE 4.6/i)).toBeInTheDocument();
    expect(screen.getByText(/Feature Average Projected Points MAE 5.2/i)).toBeInTheDocument();
  });

  test('shows API errors clearly', async () => {
    window.fetch = vi.fn((path) => {
      if (path === '/api/model/metrics') {
        return jsonResponse({ message: 'Could not load model metrics' }, false, 502);
      }
      return defaultFetch(path);
    });
    window.history.pushState({}, '', '/model');

    render(<App />);

    expect(await screen.findByText('Could not load model metrics')).toBeInTheDocument();
  });
});

function defaultFetch(path, options = {}) {
  if (path.startsWith('/api/features/player-snapshots/latest')) {
    return jsonResponse({
      snapshotId: 42,
      snapshotType: 'player',
      gameId: 22300003,
      playerId: 201939,
      teamId: 1610612744,
      homeTeamId: 1610612744,
      awayTeamId: 1610612747,
      snapshotTime: '2024-01-05T21:59:59',
      dataCutoffTime: '2024-01-05T21:59:59',
      features: {
        games_played_prior: 2,
        last_5_points_avg: 24.2,
        age_at_game: 35,
      },
    });
  }

  if (path === '/api/predictions/player' && options.method === 'POST') {
    return jsonResponse({
      predictionId: 9,
      modelVersion: 'player-baseline-v1',
      trainedRows: 100,
      gameId: 22300003,
      playerId: 201939,
      teamId: 1610612744,
      projectedPoints: 28.4,
      projectedRebounds: 5.1,
      projectedAssists: 6.9,
      projectedMinutes: 34.2,
      fantasyPoints: 44.8,
      fantasyFloor: 35.1,
      fantasyCeiling: 54.3,
      confidenceScore: 0.74,
      riskLevel: 'medium',
      factors: [],
    });
  }

  if (path.startsWith('/api/features/game-snapshots/latest')) {
    return jsonResponse({
      snapshotId: 77,
      snapshotType: 'game',
      gameId: 22300003,
      homeTeamId: 1610612744,
      awayTeamId: 1610612747,
      snapshotTime: '2024-01-05T21:59:59',
      dataCutoffTime: '2024-01-05T21:59:59',
      features: {
        home_team_id: 1610612744,
        away_team_id: 1610612747,
        season_point_differential_delta: 5.4,
      },
    });
  }

  if (path === '/api/predictions/game-score' && options.method === 'POST') {
    return jsonResponse({
      predictionId: 10,
      modelVersion: 'game-score-baseline-v1',
      trainedRows: 80,
      gameId: 22300003,
      homeTeamId: 1610612744,
      awayTeamId: 1610612747,
      homeTeamScore: 112.8,
      awayTeamScore: 108.1,
      predictedWinnerTeamId: 1610612744,
      pointDifferential: 4.7,
      confidenceScore: 0.63,
      factors: [],
    });
  }

  if (path === '/api/model/metrics') {
    return jsonResponse({
      playerModelVersion: 'player-baseline-v1',
      playerTrainedRows: 6000,
      playerBaseline: {
        metrics: {
          projected_points: { mae: 4.59, rmse: 6.1 },
        },
        baseline_metrics: {
          feature_average: {
            projected_points: { mae: 5.2, rmse: 6.8 },
          },
        },
      },
      gameScoreModelVersion: 'game-score-baseline-v1',
      gameScoreTrainedRows: 277,
      gameScoreBaseline: {
        metrics: {
          home_team_score: { mae: 9.52, rmse: 12.4 },
        },
      },
    });
  }

  if (path === '/api/model/versions') {
    return jsonResponse({
      activeModel: { versionName: 'player-baseline-v1' },
      gameScoreModel: { versionName: 'game-score-baseline-v1' },
    });
  }

  return jsonResponse({});
}

function jsonResponse(payload, ok = true, status = ok ? 200 : 500) {
  return Promise.resolve({
    ok,
    status,
    text: () => Promise.resolve(JSON.stringify(payload)),
  });
}

function setViewport(width, height) {
  Object.defineProperty(window, 'innerWidth', { configurable: true, writable: true, value: width });
  Object.defineProperty(window, 'innerHeight', { configurable: true, writable: true, value: height });
  window.dispatchEvent(new Event('resize'));
}
