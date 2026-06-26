import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

import App from './App.jsx';

const bannedNormalCopy = [
  'Spring Boot API',
  'PostgreSQL Source of Truth',
  'FastAPI Internal ML',
  'feature snapshot',
  'Load Snapshot',
  'N/A',
];

describe('NBA Premier Predictor frontend', () => {
  beforeEach(() => {
    window.fetch = vi.fn(defaultFetch);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    window.history.pushState({}, '', '/');
  });

  test('renders the landing page with natural copy', () => {
    window.history.pushState({}, '', '/');

    render(<App />);

    expect(screen.getByRole('heading', { name: /NBA Premier Predictor/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Open Dashboard/i })).toHaveAttribute('href', '/dashboard');
    expect(screen.getByText(/player lines, fantasy output and game scores/i)).toBeInTheDocument();
  });

  test('normal dashboard copy does not expose implementation names', async () => {
    window.history.pushState({}, '', '/dashboard');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    for (const phrase of bannedNormalCopy) {
      expect(document.body).not.toHaveTextContent(phrase);
    }
  });

  test('shows Present for active players and readable positions', async () => {
    const user = userEvent.setup();
    window.history.pushState({}, '', '/players');

    render(<App />);

    expect(screen.getByText('Search for a player to begin')).toBeInTheDocument();
    await user.type(screen.getByLabelText(/Search by player name/i), 'Steph');

    expect(await screen.findByText('Stephen Curry')).toBeInTheDocument();
    expect(screen.getByText('Guard')).toBeInTheDocument();
    expect(screen.getByText('Present')).toBeInTheDocument();
  });

  test('teams page asks for current NBA teams by default', async () => {
    window.history.pushState({}, '', '/teams');

    render(<App />);

    expect(await screen.findByText('Golden State Warriors')).toBeInTheDocument();
    expect(screen.getByText('Los Angeles Lakers')).toBeInTheDocument();
    expect(screen.queryByText('Sheboygan Red Skins')).not.toBeInTheDocument();
    expect(window.fetch.mock.calls.some(([url]) => String(url).includes('currentOnly=true'))).toBe(true);
  });

  test('submits a player prediction through searchable choices', async () => {
    const user = userEvent.setup();
    window.history.pushState({}, '', '/predict/player');

    render(<App />);

    await user.type(screen.getByLabelText(/Player/i), 'Steph');
    await user.click(await screen.findByRole('button', { name: /Stephen Curry/i }));
    await user.click(await screen.findByRole('button', { name: /home game/i }));
    await user.click(screen.getByRole('button', { name: /Predict Player Line/i }));

    expect(await screen.findByText('Expected Stat Line')).toBeInTheDocument();
    expect(screen.getByText('28.4')).toBeInTheDocument();
    expect(screen.getByText('74%')).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith('/api/predictions/player', expect.objectContaining({ method: 'POST' }));
  });

  test('submits a game-score prediction through a readable matchup', async () => {
    const user = userEvent.setup();
    window.history.pushState({}, '', '/predict/game-score');

    render(<App />);

    await user.click(await screen.findByRole('button', { name: /Los Angeles Lakers at Golden State Warriors/i }));
    await user.click(screen.getByRole('button', { name: /Predict Score/i }));

    expect(await screen.findByText('Projected Score')).toBeInTheDocument();
    expect(screen.getAllByText('Golden State Warriors').length).toBeGreaterThan(0);
    expect(screen.getByText('63%')).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith('/api/predictions/game-score', expect.objectContaining({ method: 'POST' }));
  });

  test('advanced model details are collapsed by default', async () => {
    const user = userEvent.setup();
    window.history.pushState({}, '', '/model');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Accuracy' })).toBeInTheDocument();
    expect(screen.getByText('Advanced model info')).toBeInTheDocument();
    expect(screen.getByText(/player-baseline-v1/i)).not.toBeVisible();

    await user.click(screen.getByText('Advanced model info'));

    expect(screen.getByText(/player-baseline-v1/i)).toBeVisible();
  });

  test('renders landing route at mobile and desktop widths', () => {
    window.history.pushState({}, '', '/');
    setViewport(390, 844);
    const { unmount } = render(<App />);

    expect(screen.getByRole('heading', { name: /NBA Premier Predictor/i })).toBeInTheDocument();
    unmount();

    setViewport(1440, 900);
    render(<App />);

    expect(screen.getByRole('link', { name: /Make a Pick/i })).toBeInTheDocument();
  });
});

function defaultFetch(path, options = {}) {
  if (path === '/actuator/health') {
    return jsonResponse({ status: 'UP' });
  }

  if (path.startsWith('/api/players?')) {
    return jsonResponse(page([
      {
        id: 201939,
        fullName: 'Stephen Curry',
        position: 'G',
        fromYear: 2009,
        toYear: 2026,
        active: true,
      },
    ]));
  }

  if (path.startsWith('/api/players/201939/games')) {
    return jsonResponse(page([
      {
        gameId: 12300001,
        gameDateTimeEst: '2024-01-15T22:00:00',
        teamId: 1610612744,
        teamName: 'Golden State Warriors',
        opponentTeamId: 1610612747,
        home: true,
        win: true,
        minutes: 35.5,
        points: 32,
        rebounds: 5,
        assists: 7,
      },
    ]));
  }

  if (path.startsWith('/api/features/player-snapshots/latest')) {
    return jsonResponse({
      snapshotId: 42,
      snapshotType: 'player',
      gameId: 12300001,
      playerId: 201939,
      teamId: 1610612744,
      homeTeamId: 1610612744,
      awayTeamId: 1610612747,
      snapshotTime: '2024-01-15T21:59:59',
      dataCutoffTime: '2024-01-15T21:59:59',
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
      gameId: 12300001,
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

  if (path.startsWith('/api/games?')) {
    return jsonResponse(page([
      {
        id: 12300001,
        seasonStartYear: 2023,
        gameDateTimeEst: '2024-01-15T22:00:00',
        gameDate: '2024-01-15',
        homeTeamId: 1610612744,
        homeTeamName: 'Golden State Warriors',
        awayTeamId: 1610612747,
        awayTeamName: 'Los Angeles Lakers',
        homeScore: 120,
        awayScore: 115,
      },
    ]));
  }

  if (path.startsWith('/api/features/game-snapshots/latest')) {
    return jsonResponse({
      snapshotId: 77,
      snapshotType: 'game',
      gameId: 12300001,
      homeTeamId: 1610612744,
      awayTeamId: 1610612747,
      snapshotTime: '2024-01-15T21:59:59',
      dataCutoffTime: '2024-01-15T21:59:59',
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
      gameId: 12300001,
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

  if (path.startsWith('/api/teams?')) {
    return jsonResponse(page([
      {
        id: 1610612744,
        fullName: 'Golden State Warriors',
        abbreviation: 'GSW',
        seasonFounded: 1946,
        seasonActiveTill: 2100,
        league: 'NBA',
      },
      {
        id: 1610612747,
        fullName: 'Los Angeles Lakers',
        abbreviation: 'LAL',
        seasonFounded: 1948,
        seasonActiveTill: 2100,
        league: 'NBA',
      },
    ]));
  }

  if (path === '/api/model/metrics') {
    return jsonResponse({
      modelVersion: 'player-baseline-v1',
      trainedRows: 6000,
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

  if (path === '/api/predictions/history?limit=8' || path === '/api/predictions/history?limit=50') {
    return jsonResponse([]);
  }

  return jsonResponse({});
}

function page(content) {
  return {
    content,
    empty: content.length === 0,
    first: true,
    last: true,
    number: 0,
    numberOfElements: content.length,
    size: content.length,
    totalElements: content.length,
    totalPages: 1,
  };
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
