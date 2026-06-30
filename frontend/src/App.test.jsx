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
  'Unknown',
  'PREGAME READ',
  'MISS RATE',
  'FLOOR CEILING',
  'PTS REB AST',
  'Not listed',
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
    expect(screen.getByRole('link', { name: /Make a Pick/i })).toHaveAttribute('href', '/predict/player');
    expect(screen.getByRole('link', { name: /Browse Players/i })).toHaveAttribute('href', '/players');
    expect(screen.getByText(/Make game or player predictions/i)).toBeInTheDocument();
    expect(screen.getByAltText(/Larry Bird and Magic Johnson/i)).toBeInTheDocument();
    expect(screen.getByAltText(/Kobe Bryant and Michael Jordan/i)).toBeInTheDocument();
    expect(screen.getByAltText(/LeBron James and Dwyane Wade/i)).toBeInTheDocument();
    expect(document.querySelectorAll('.legend-card-backdrop')).toHaveLength(3);
  });

  test('dashboard route redirects to players without exposing implementation names', async () => {
    window.history.pushState({}, '', '/dashboard');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Players' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Dashboard/i })).not.toBeInTheDocument();
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

  test('shows final season for retired players', async () => {
    const user = userEvent.setup();
    window.history.pushState({}, '', '/players');

    render(<App />);

    await user.type(screen.getByLabelText(/Search by player name/i), 'Michael');

    expect(await screen.findByText('Michael Jordan')).toBeInTheDocument();
    expect(screen.getByText('2002-03')).toBeInTheDocument();
    expect(screen.queryByText('Present')).not.toBeInTheDocument();
  });

  test('teams page asks for current NBA teams by default', async () => {
    window.history.pushState({}, '', '/teams');

    render(<App />);

    expect(await screen.findByText('Golden State Warriors')).toBeInTheDocument();
    expect(screen.getByText('Los Angeles Lakers')).toBeInTheDocument();
    expect(screen.getByText('ATL')).toBeInTheDocument();
    expect(screen.getByText('TOR')).toBeInTheDocument();
    expect(screen.queryByText('Championships')).not.toBeInTheDocument();
    expect(screen.queryByText('Sheboygan Red Skins')).not.toBeInTheDocument();
    expect(window.fetch.mock.calls.some(([url]) => String(url).includes('currentOnly=true'))).toBe(true);
  });

  test('team detail shows selected-season regular-season record', async () => {
    window.history.pushState({}, '', '/teams/1610612744');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Golden State Warriors' })).toBeInTheDocument();
    expect(document.body).toHaveTextContent('12-5');
    expect(document.body).toHaveTextContent('71%');
    expect(document.body).toHaveTextContent('2-0');
    expect(screen.queryByText('Through')).not.toBeInTheDocument();
  });

  test('box score does not label everyone bench when lineup data is missing', async () => {
    window.history.pushState({}, '', '/games/12300001');

    render(<App />);

    expect(await screen.findByRole('heading', { name: /Los Angeles Lakers at Golden State Warriors/i })).toBeInTheDocument();
    expect(screen.getAllByText('Lineup pending').length).toBeGreaterThan(0);
    expect(screen.queryByText('Bench')).not.toBeInTheDocument();
  });

  test('submits a player prediction through searchable choices', async () => {
    const user = userEvent.setup();
    window.history.pushState({}, '', '/predict/player');

    render(<App />);

    await user.type(screen.getByLabelText(/Player/i), 'Steph');
    await user.click(await screen.findByRole('button', { name: /Stephen Curry/i }));
    await user.click(await screen.findByRole('button', { name: /Los Angeles Lakers/i }));
    await user.click(screen.getByRole('button', { name: /Predict Player Line/i }));

    expect(await screen.findByText('Expected Stat Line')).toBeInTheDocument();
    expect(screen.getByText('28')).toBeInTheDocument();
    expect(screen.getByText('50%')).toBeInTheDocument();
    expect(screen.getByText('Steals')).toBeInTheDocument();
    expect(screen.getByText('Blocks')).toBeInTheDocument();
    expect(screen.getByText('Turnovers')).toBeInTheDocument();
    expect(screen.getByText(/2 STL/i)).toBeInTheDocument();
    expect(screen.getByText(/1 BLK/i)).toBeInTheDocument();
    expect(screen.getByText(/3 TO/i)).toBeInTheDocument();
    expect(screen.queryByText('Fantasy Points')).not.toBeInTheDocument();
    expect(screen.queryByText('Confidence')).not.toBeInTheDocument();
    expect(screen.queryByText('How this was calculated')).not.toBeInTheDocument();
    expect(window.fetch.mock.calls.some(([url]) => String(url).includes('/api/features/player-snapshots/ensure'))).toBe(true);
    expect(window.fetch).toHaveBeenCalledWith('/api/predictions/player', expect.objectContaining({ method: 'POST' }));
  });

  test('fantasy prediction has fantasy-specific result copy', async () => {
    const user = userEvent.setup();
    window.history.pushState({}, '', '/predict/fantasy');

    render(<App />);

    expect(screen.getByRole('option', { name: /Search a player first/i })).toBeInTheDocument();
    await user.type(screen.getByLabelText(/Player/i), 'Steph');
    await user.click(await screen.findByRole('button', { name: /Stephen Curry/i }));
    await user.click(await screen.findByRole('button', { name: /Los Angeles Lakers/i }));
    await user.click(screen.getByRole('button', { name: /Predict Fantasy/i }));

    expect(await screen.findByText('Fantasy Outlook')).toBeInTheDocument();
    expect(screen.getByText('Fantasy Points')).toBeInTheDocument();
    expect(screen.getByText('Floor')).toBeInTheDocument();
    expect(screen.getByText('Ceiling')).toBeInTheDocument();
    expect(screen.queryByText('How this was calculated')).not.toBeInTheDocument();
  });

  test('submits a game-score prediction through a readable matchup', async () => {
    const user = userEvent.setup();
    window.history.pushState({}, '', '/predict/game-score');

    render(<App />);

    await user.click(await screen.findByRole('button', { name: /Los Angeles Lakers at Golden State Warriors/i }));
    await user.click(screen.getByRole('button', { name: /Predict Score/i }));

    expect(await screen.findByText('Projected Score')).toBeInTheDocument();
    expect(screen.getAllByText('Golden State Warriors').length).toBeGreaterThan(0);
    expect(screen.getByText('By 3')).toBeInTheDocument();
    expect(screen.getByText('203')).toBeInTheDocument();
    expect(window.fetch).toHaveBeenCalledWith('/api/predictions/game-score', expect.objectContaining({ method: 'POST' }));
  });

  test('game picker uses season labels and hides raw game ids', async () => {
    window.history.pushState({}, '', '/predict/game-score');

    render(<App />);

    expect(await screen.findByRole('option', { name: '2023-2024' })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: /Los Angeles Lakers at Golden State Warriors/i })).toBeInTheDocument();
    expect(screen.queryByText('12300001')).not.toBeInTheDocument();
  });

  test('standings page shows next-season projections without raw ids', async () => {
    window.history.pushState({}, '', '/standings');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Standings Projection' })).toBeInTheDocument();
    expect(await screen.findByRole('option', { name: '2026-2027' })).toBeInTheDocument();
    expect(await screen.findByText('Western Conference')).toBeInTheDocument();
    expect(screen.getByText('Golden State Warriors')).toBeInTheDocument();
    expect(screen.getAllByText('Projected Record')).toHaveLength(2);
    expect(screen.getAllByText('Range Of Wins')).toHaveLength(2);
    expect(screen.getAllByText('Previous Season Record')).toHaveLength(2);
    expect(screen.getByText('50-32')).toBeInTheDocument();
    expect(screen.getByText('43-56')).toBeInTheDocument();
    expect(screen.getByText('48-34')).toBeInTheDocument();
    expect(screen.queryByText('Not released')).not.toBeInTheDocument();
    expect(screen.queryByText('Using team strength')).not.toBeInTheDocument();
    expect(screen.queryByText('Run Range')).not.toBeInTheDocument();
    expect(screen.queryByText('Latest Range Run')).not.toBeInTheDocument();
    expect(screen.queryByText('Details')).not.toBeInTheDocument();
    expect(screen.queryByText('GSW')).not.toBeInTheDocument();
    expect(screen.queryByText('1610612744')).not.toBeInTheDocument();
  });

  test('team projection page explains roster movement naturally', async () => {
    window.history.pushState({}, '', '/teams/1610612744/projection?season=2026');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Golden State Warriors' })).toBeInTheDocument();
    expect(screen.getByText('Projected Record')).toBeInTheDocument();
    expect(screen.getByText('Why The Projection Moved')).toBeInTheDocument();
    expect(screen.getByText('Previous season record: 48-34')).toBeInTheDocument();
    expect(screen.getByText('Roster Movement')).toBeInTheDocument();
    expect(screen.getByText('1 confirmed roster addition')).toBeInTheDocument();
  });

  test('accuracy page does not expose advanced JSON details', async () => {
    const user = userEvent.setup();
    window.history.pushState({}, '', '/model');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Accuracy' })).toBeInTheDocument();
    expect(await screen.findByText('Recent Pick Checks')).toBeInTheDocument();
    expect(await screen.findByText('Model Updates')).toBeInTheDocument();
    expect(screen.getByText('Recent Training Runs')).toBeInTheDocument();
    expect(screen.getByText('2014 to 2025')).toBeInTheDocument();
    expect(screen.getByText('Recent Decisions')).toBeInTheDocument();
    expect(screen.getByText('Improved 5.2 to 4.8')).toBeInTheDocument();
    expect(screen.queryByText(/artifacts/i)).not.toBeInTheDocument();
    expect(screen.getByText('Accuracy Watchlist')).toBeInTheDocument();
    expect(screen.getByText('Needs attention')).toBeInTheDocument();
    expect(screen.getByText(/Points average miss 9.4/i)).toBeInTheDocument();
    expect(screen.getByText(/Average miss 4.2/i)).toBeInTheDocument();
    expect(screen.getByText(/Player Stat Points missed by 4.2/i)).toBeInTheDocument();
    expect(screen.queryByText('Advanced model info')).not.toBeInTheDocument();
    expect(screen.queryByText('Version names')).not.toBeInTheDocument();
    expect(screen.queryByText(/player-baseline-v1/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/player-baseline-v2-20260629120000/i)).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Update completed games/i }));
    expect(window.fetch).toHaveBeenCalledWith('/api/model/prediction-errors/refresh', expect.objectContaining({ method: 'POST' }));
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
    if (path.includes('Michael')) {
      return jsonResponse(page([
        {
          id: 893,
          fullName: 'Michael Jordan',
          position: 'G',
          fromYear: 1984,
          toYear: 2002,
          active: false,
        },
      ]));
    }
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

  if (path === '/api/seasons') {
    return jsonResponse([
      {
        seasonStartYear: 2023,
        label: '2023-2024',
        gameCount: 1,
        mostRecentGameDate: '2024-01-15',
      },
    ]);
  }

  if (path === '/api/players/201939/seasons') {
    return jsonResponse([
      {
        seasonStartYear: 2023,
        label: '2023-2024',
        gameCount: 1,
        mostRecentGameDate: '2024-01-15',
      },
    ]);
  }

  if (path === '/api/teams/1610612744/seasons') {
    return jsonResponse([
      {
        seasonStartYear: 2023,
        label: '2023-2024',
        gameCount: 1,
        mostRecentGameDate: '2024-01-15',
      },
    ]);
  }

  if (path.startsWith('/api/players/201939/games')) {
    return jsonResponse(page([
      {
        gameId: 12300001,
        gameDateTimeEst: '2024-01-15T22:00:00',
        teamId: 1610612744,
        teamName: 'Golden State Warriors',
        opponentTeamId: 1610612747,
        opponentTeamName: 'Los Angeles Lakers',
        teamScore: 120,
        opponentScore: 115,
        home: true,
        win: true,
        recordAfterGame: '2-0',
        minutes: 35.5,
        points: 32,
        rebounds: 5,
        assists: 7,
        fieldGoalPercentage: 0.5,
        steals: 2,
        blocks: 1,
        turnovers: 3,
        plusMinus: 8,
      },
    ]));
  }

  if (path.startsWith('/api/features/player-snapshots/ensure')) {
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
      modelVersion: 'player-baseline-v2',
      trainedRows: 100,
      gameId: 12300001,
      playerId: 201939,
      teamId: 1610612744,
      projectedPoints: 28.4,
      projectedRebounds: 5.1,
      projectedAssists: 6.9,
      projectedMinutes: 34.2,
      projectedSteals: 1.9,
      projectedBlocks: 0.4,
      projectedTurnovers: 3.1,
      projectedFieldGoalsMade: 10.3,
      projectedFieldGoalsAttempted: 20.6,
      projectedFieldGoalPercentage: 0.5,
      fantasyPoints: 44.8,
      fantasyFloor: 35.1,
      fantasyCeiling: 54.3,
      confidenceScore: 0.74,
      riskLevel: 'medium',
      factors: [],
    });
  }

  if (path === '/api/predictions/fantasy' && options.method === 'POST') {
    return jsonResponse({
      predictionId: 11,
      modelVersion: 'player-baseline-v2',
      trainedRows: 100,
      gameId: 12300001,
      playerId: 201939,
      teamId: 1610612744,
      projectedPoints: 28.4,
      projectedRebounds: 5.1,
      projectedAssists: 6.9,
      projectedMinutes: 34.2,
      projectedSteals: 1.9,
      projectedBlocks: 0.4,
      projectedTurnovers: 3.1,
      projectedFieldGoalsMade: 10.3,
      projectedFieldGoalsAttempted: 20.6,
      projectedFieldGoalPercentage: 0.5,
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

  if (path === '/api/games/12300001') {
    return jsonResponse({
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
    });
  }

  if (path === '/api/games/12300001/box-score') {
    return jsonResponse({
      game: {
        id: 12300001,
        seasonStartYear: 2023,
        gameDateTimeEst: '2024-01-15T22:00:00',
        homeTeamName: 'Golden State Warriors',
        awayTeamName: 'Los Angeles Lakers',
        homeScore: 120,
        awayScore: 115,
      },
      homeTeam: {
        teamName: 'Golden State Warriors',
        teamScore: 120,
        rebounds: 44,
        assists: 29,
        steals: 8,
        blocks: 6,
        turnovers: 12,
      },
      awayTeam: {
        teamName: 'Los Angeles Lakers',
        teamScore: 115,
        rebounds: 42,
        assists: 25,
        steals: 7,
        blocks: 4,
        turnovers: 13,
      },
      homePlayers: [{ playerName: 'Stephen Curry', minutes: 35.5, points: 32, rebounds: 5, assists: 7 }],
      awayPlayers: [{ playerName: 'LeBron James', minutes: 36, points: 28, rebounds: 8, assists: 9 }],
    });
  }

  if (path.startsWith('/api/features/game-snapshots/ensure')) {
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
      homeTeamScore: 103.4,
      awayTeamScore: 99.6,
      predictedWinnerTeamId: 1610612744,
      pointDifferential: 3.8,
      confidenceScore: 0.63,
      factors: [],
    });
  }

  if (path.startsWith('/api/teams?')) {
    return jsonResponse(page([
      {
        id: 1610612737,
        fullName: 'Atlanta Hawks',
        abbreviation: 'ATL',
        seasonFounded: 1968,
        seasonActiveTill: 2100,
        league: 'NBA',
      },
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
      {
        id: 1610612761,
        fullName: 'Toronto Raptors',
        abbreviation: 'TOR',
        seasonFounded: 1995,
        seasonActiveTill: 2100,
        league: 'NBA',
      },
    ]));
  }

  if (path.startsWith('/api/teams/1610612744/dashboard')) {
    return jsonResponse({
      team: {
        id: 1610612744,
        fullName: 'Golden State Warriors',
        abbreviation: 'GSW',
        seasonFounded: 1946,
        seasonActiveTill: 2100,
        league: 'NBA',
      },
      regularSeasonRecord: {
        wins: 12,
        losses: 5,
        winPercentage: 0.706,
      },
      recentGames: [],
    });
  }

  if (path.startsWith('/api/teams/1610612744/games')) {
    return jsonResponse(page([
      {
        gameId: 12300001,
        gameDateTimeEst: '2024-01-15T22:00:00',
        opponentTeamName: 'Los Angeles Lakers',
        win: true,
        teamScore: 120,
        opponentScore: 115,
        recordAfterGame: '2-0',
        rebounds: 44,
        assists: 29,
        steals: 8,
        blocks: 6,
        turnovers: 12,
      },
    ]));
  }

  if (path === '/api/standings/projections/2026') {
    return jsonResponse(standingsPayload());
  }

  if (path === '/api/standings/simulate?season=2026&runs=1000' && options.method === 'POST') {
    return jsonResponse({
      simulationRunId: 12,
      seasonStartYear: 2026,
      runCount: 1000,
      scheduleAvailable: false,
      generatedAt: '2026-06-29T16:00:00Z',
      notes: 'Schedule-free Monte Carlo projection using team strength and roster context',
      projectedRecords: standingsPayload().westernConference,
    });
  }

  if (path === '/api/teams/1610612744/projection?season=2026') {
    return jsonResponse(standingsPayload().westernConference[0]);
  }

  if (path === '/api/teams/1610612744/roster-impact?season=2026') {
    return jsonResponse({
      seasonStartYear: 2026,
      teamId: 1610612744,
      teamName: 'Golden State Warriors',
      playersAdded: 1,
      playersLost: 0,
      rookieCount: 1,
      injuryFlagCount: 0,
      incomingMinutes: 28.4,
      outgoingMinutes: 0,
      rosterImpactScore: 1.2,
      rosterTurnoverScore: 0.13,
      explanations: ['1 confirmed roster addition', '1 rookie addition'],
    });
  }

  if (path === '/api/model/metrics') {
    return jsonResponse({
      modelVersion: 'player-baseline-v2',
      trainedRows: 6000,
      playerBaseline: {
        metrics: {
          projected_points: { mae: 4.59, rmse: 6.1 },
          fantasy_points: { mae: 7.85, rmse: 9.2, hit_rate: 0.68, hit_threshold: 8 },
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
          away_team_score: { mae: 10.06, rmse: 13.1, hit_rate: 0.61, hit_threshold: 10 },
        },
      },
    });
  }

  if (path === '/api/model/evaluate' && options.method === 'POST') {
    return jsonResponse({
      playerBaseline: {
        metrics: {
          projected_points: { mae: 4.59, rmse: 6.1, hit_rate: 0.72, hit_threshold: 5 },
        },
      },
      gameScoreBaseline: {
        metrics: {
          home_team_score: { mae: 9.52, rmse: 12.4, hit_rate: 0.64, hit_threshold: 10 },
        },
      },
    });
  }

  if (path.startsWith('/api/model/monitoring')) {
    return jsonResponse(modelMonitoringPayload());
  }

  if (path === '/api/model/prediction-errors/refresh' && options.method === 'POST') {
    return jsonResponse(modelMonitoringPayload());
  }

  if (path === '/api/model/versions/active') {
    return jsonResponse([
      {
        id: 5,
        versionName: 'player-baseline-v2-20260629120000',
        targetVariable: 'player_stat_fantasy',
        trainedAt: '2026-06-29T12:00:00Z',
        active: true,
      },
      {
        id: 6,
        versionName: 'game-score-baseline-v2-20260629120000',
        targetVariable: 'game_score',
        trainedAt: '2026-06-29T12:00:00Z',
        active: true,
      },
    ]);
  }

  if (path === '/api/model/training-runs') {
    return jsonResponse([
      {
        id: 3,
        startedAt: '2026-06-29T11:58:00Z',
        finishedAt: '2026-06-29T12:04:00Z',
        status: 'completed',
        trainingDataRange: '2014-2025',
      },
    ]);
  }

  if (path === '/api/model/promotion-history') {
    return jsonResponse([
      {
        id: 4,
        promoted: true,
        previousMae: 5.2,
        candidateMae: 4.8,
        promotedAt: '2026-06-29T12:05:00Z',
      },
    ]);
  }

  if (path === '/api/model/versions') {
    return jsonResponse({
      activeModel: { versionName: 'player-baseline-v2' },
      gameScoreModel: { versionName: 'game-score-baseline-v1' },
    });
  }

  if (path === '/api/predictions/history?limit=8' || path === '/api/predictions/history?limit=50') {
    return jsonResponse([]);
  }

  return jsonResponse({});
}

function standingsPayload() {
  return {
    seasonStartYear: 2026,
    seasonLabel: '2026-2027',
    generatedAt: '2026-06-29T16:00:00Z',
    scheduleAvailable: false,
    projectionMethod: 'Schedule-free team strength projection',
    easternConference: [
      {
        teamId: 1610612737,
        teamName: 'Atlanta Hawks',
        abbreviation: 'ATL',
        conference: 'Eastern',
        projectedSeed: 1,
        projectedWins: 45.1,
        projectedLosses: 36.9,
        lowWins: 38,
        medianWins: 45,
        highWins: 51,
        playoffProbability: 0.72,
        strengthRating: 2.4,
        rosterImpactScore: 0.2,
        rosterTurnoverScore: 0.1,
        injuryRiskScore: 0,
        sourceSeasonStartYear: 2025,
        sourceSeasonLabel: '2025-2026',
        topReasons: ['Previous season record: 43-39'],
        uncertaintyFactors: ['Normal season variance'],
      },
    ],
    westernConference: [
      {
        teamId: 1610612744,
        teamName: 'Golden State Warriors',
        abbreviation: 'GSW',
        conference: 'Western',
        projectedSeed: 1,
        projectedWins: 50.4,
        projectedLosses: 31.6,
        lowWins: 43,
        medianWins: 50,
        highWins: 56,
        playoffProbability: 0.84,
        strengthRating: 5.2,
        rosterImpactScore: 1.2,
        rosterTurnoverScore: 0.13,
        injuryRiskScore: 0,
        sourceSeasonStartYear: 2025,
        sourceSeasonLabel: '2025-2026',
        topReasons: ['Previous season record: 48-34', 'Roster movement impact: +1.2 rating points'],
        uncertaintyFactors: ['Roster turnover adds projection uncertainty'],
      },
      {
        teamId: 1610612747,
        teamName: 'Los Angeles Lakers',
        abbreviation: 'LAL',
        conference: 'Western',
        projectedSeed: 2,
        projectedWins: 46.2,
        projectedLosses: 35.8,
        lowWins: 39,
        medianWins: 46,
        highWins: 52,
        playoffProbability: 0.76,
        strengthRating: 3.1,
        rosterImpactScore: -0.4,
        rosterTurnoverScore: 0.2,
        injuryRiskScore: 0.1,
        sourceSeasonStartYear: 2025,
        sourceSeasonLabel: '2025-2026',
        topReasons: ['Previous season record: 45-37'],
        uncertaintyFactors: ['Normal season variance'],
      },
    ],
  };
}

function modelMonitoringPayload() {
  return {
    totalErrors: 2,
    targetSummaries: [
      {
        targetVariable: 'points',
        predictionCount: 2,
        averageMiss: 4.2,
        rmse: 5.1,
      },
    ],
    driftIndicators: [
      {
        targetVariable: 'points',
        sampleSize: 5,
        averageMiss: 9.4,
        watchThreshold: 8,
        status: 'needs_attention',
      },
    ],
    recentErrors: [
      {
        id: 1,
        predictionType: 'player_stat',
        targetVariable: 'points',
        absoluteError: 4.2,
      },
    ],
  };
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
