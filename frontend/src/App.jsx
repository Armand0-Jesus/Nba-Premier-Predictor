import {
  Activity,
  BarChart3,
  CalendarDays,
  Gauge,
  History,
  Home,
  LineChart,
  Loader2,
  Search,
  ShieldCheck,
  Sparkles,
  Target,
  Trophy,
  Users,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import {
  BrowserRouter,
  Link,
  NavLink,
  Outlet,
  Route,
  Routes,
  useParams,
  useSearchParams,
} from 'react-router-dom';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart as ReLineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import dunkSilhouette from './assets/dunk-silhouette.jpg';
import { apiGet, apiPost, compactNumber, pageItems, percent } from './api.js';

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: Home },
  { to: '/players', label: 'Players', icon: Users },
  { to: '/teams', label: 'Teams', icon: ShieldCheck },
  { to: '/games', label: 'Games', icon: CalendarDays },
  { to: '/predict/player', label: 'Player Pick', icon: Target },
  { to: '/predict/fantasy', label: 'Fantasy Pick', icon: Sparkles },
  { to: '/predict/game-score', label: 'Score Pick', icon: Trophy },
  { to: '/model', label: 'Accuracy', icon: Gauge },
  { to: '/history', label: 'History', icon: History },
];

const seasonOptions = Array.from({ length: 12 }, (_, index) => 2025 - index);
const defaultSeason = '2023';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route element={<AppShell />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/players" element={<PlayersPage />} />
          <Route path="/players/:playerId" element={<PlayerDetailPage />} />
          <Route path="/teams" element={<TeamsPage />} />
          <Route path="/teams/:teamId" element={<TeamDetailPage />} />
          <Route path="/games" element={<GamesPage />} />
          <Route path="/predict/player" element={<PlayerPredictionPage mode="player" />} />
          <Route path="/predict/fantasy" element={<PlayerPredictionPage mode="fantasy" />} />
          <Route path="/predict/game-score" element={<GameScorePredictionPage />} />
          <Route path="/model" element={<ModelPage />} />
          <Route path="/history" element={<HistoryPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

function Landing() {
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => setLoaded(true), 760);
    return () => window.clearTimeout(timer);
  }, []);

  return (
    <main className="landing">
      <div className={`loader-screen ${loaded ? 'loader-screen--done' : ''}`} aria-hidden={loaded}>
        <img src={dunkSilhouette} alt="" className="loader-dunk" />
        <span className="loader-mark">NBA PREMIER</span>
      </div>

      <section className="landing-hero">
        <div className="brand-lockup">
          <span className="brand-kicker">before tipoff</span>
          <h1>NBA Premier Predictor</h1>
        </div>
        <div className="hero-copy">
          <p>Predict player lines, fantasy output and game scores using what was known before tipoff.</p>
          <div className="hero-actions">
            <IconLink to="/dashboard" icon={Activity}>
              Open Dashboard
            </IconLink>
            <IconLink to="/predict/player" icon={Target} variant="ghost">
              Make a Pick
            </IconLink>
          </div>
        </div>
        <div className="hero-visual" aria-label="Dunk silhouette">
          <img src={dunkSilhouette} alt="" />
          <div className="score-strip">
            <span>PREGAME READ</span>
            <strong>READY</strong>
          </div>
        </div>
      </section>

      <section className="landing-grid" aria-label="Platform snapshot">
        <MetricTile label="Player" value="PTS REB AST" />
        <MetricTile label="Fantasy" value="FLOOR CEILING" />
        <MetricTile label="Score" value="MARGIN FAVORITE" />
        <MetricTile label="Accuracy" value="MISS RATE" />
      </section>
    </main>
  );
}

function AppShell() {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Link to="/" className="side-brand" aria-label="NBA Premier Predictor home">
          <span>NPP</span>
          <strong>NBA Premier Predictor</strong>
        </Link>
        <nav className="side-nav" aria-label="Primary navigation">
          {navItems.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} className={({ isActive }) => (isActive ? 'active' : undefined)}>
              <Icon size={18} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="content-shell">
        <Outlet />
      </div>
    </div>
  );
}

function Dashboard() {
  const metrics = useApi('/api/model/metrics');
  const versions = useApi('/api/model/versions');
  const history = useApi('/api/predictions/history?limit=8');
  const health = useApi('/actuator/health');
  const historyRows = Array.isArray(history.data) ? history.data : [];

  return (
    <Page title="Dashboard" eyebrow="game night">
      <div className="dashboard-grid">
        <StatusPanel
          title="App Status"
          value={health.data?.status === 'UP' ? 'Ready' : 'Checking'}
          error={health.error && 'Connection problem'}
          icon={Activity}
        />
        <StatusPanel
          title="Player Picks"
          value={metrics.error ? null : 'Ready'}
          subvalue={`${compactNumber(playerTrainingRows(metrics.data), 0)} examples learned`}
          error={metrics.error || versions.error ? 'Player picks need a refresh' : ''}
          icon={Target}
        />
        <StatusPanel
          title="Score Picks"
          value={metrics.error ? null : 'Ready'}
          subvalue={`${compactNumber(metrics.data?.gameScoreTrainedRows, 0)} examples learned`}
          error={metrics.error || versions.error ? 'Score picks need a refresh' : ''}
          icon={Trophy}
        />
      </div>

      <section className="panel panel-wide">
        <PanelHeader title="Recent Confidence" icon={LineChart} />
        <ChartFrame empty={!historyRows.length} emptyLabel={history.error || 'No recent picks yet'}>
          <ResponsiveContainer width="100%" height="100%">
            <ReLineChart data={historyRows.map((row, index) => ({ ...row, index: index + 1 }))}>
              <CartesianGrid stroke="rgba(255,255,255,.08)" vertical={false} />
              <XAxis dataKey="index" stroke="#8d929f" tickLine={false} />
              <YAxis stroke="#8d929f" tickLine={false} domain={[0, 1]} tickFormatter={(value) => `${value * 100}%`} />
              <Tooltip contentStyle={tooltipStyle} formatter={(value) => percent(value)} />
              <Line type="monotone" dataKey="confidenceScore" stroke="#f77f00" strokeWidth={2} dot={{ r: 3 }} />
            </ReLineChart>
          </ResponsiveContainer>
        </ChartFrame>
      </section>

      <section className="split">
        <MetricsBlock title="Player Accuracy" evaluation={metrics.data?.playerBaseline} />
        <MetricsBlock title="Score Accuracy" evaluation={metrics.data?.gameScoreBaseline} />
      </section>

      <AdvancedDetails title="Technical details">
        <JsonBlock value={versions.data} />
      </AdvancedDetails>
    </Page>
  );
}

function PlayersPage() {
  return (
    <EntityList
      title="Players"
      endpoint="/api/players"
      searchPlaceholder="Search by player name"
      requireQuery
      emptyBeforeSearch="Search for a player to begin"
      columns={[
        ['fullName', 'Player', (_, row) => <PlayerIdentity player={row} />],
        ['position', 'Role', readablePosition],
        ['fromYear', 'From'],
        ['toYear', 'Through', playerEndYear],
      ]}
      rowLink={(row) => `/players/${row.id}`}
    />
  );
}

function TeamsPage() {
  return (
    <EntityList
      title="Teams"
      endpoint="/api/teams"
      searchPlaceholder="Search current teams"
      baseParams={{ currentOnly: 'true', size: '30' }}
      columns={[
        ['fullName', 'Team', (_, row) => <TeamIdentity team={row} />],
        ['abbreviation', 'Abbr'],
        ['seasonFounded', 'Founded'],
        ['seasonActiveTill', 'Through', teamEndYear],
      ]}
      rowLink={(row) => `/teams/${row.id}`}
    />
  );
}

function GamesPage() {
  const [season, setSeason] = useState(defaultSeason);
  const [page, setPage] = useState(0);
  const query = new URLSearchParams({ page, size: 30 });
  if (season) query.set('season', season);
  const result = useApi(`/api/games?${query.toString()}`);
  const rows = pageItems(result.data);

  return (
    <Page title="Games" eyebrow="schedule">
      <section className="panel">
        <div className="toolbar">
          <Field label="Season">
            <SeasonSelect value={season} onChange={(value) => {
              setSeason(value);
              setPage(0);
            }} />
          </Field>
          <button className="icon-button" type="button" onClick={() => setPage(0)}>
            <Search size={17} />
            <span>Search</span>
          </button>
        </div>
        <DataTable
          rows={rows}
          columns={[
            ['gameDateTimeEst', 'Date', formatShortDate],
            ['id', 'Matchup', (_, row) => <MatchupLabel game={row} />],
            ['homeScore', 'Score', (_, row) => scoreText(row)],
          ]}
          action={(row) => (
            <Link to={`/predict/game-score?gameId=${row.id}`} className="mini-link">
              Predict
            </Link>
          )}
          empty={result.error || 'No games found'}
        />
        <Pager page={page} setPage={setPage} last={result.data?.last} />
      </section>
    </Page>
  );
}

function PlayerDetailPage() {
  const { playerId } = useParams();
  const result = useApi(`/api/players/${playerId}/dashboard`);
  const data = result.data;
  const recentGames = data?.recentGames || [];

  return (
    <Page title={data?.player?.fullName || 'Player'} eyebrow="profile">
      <ErrorBanner message={result.error} />
      {data && (
        <>
          <section className="panel identity-panel">
            <PlayerAvatar player={data.player} />
            <div>
              <span>{readablePosition(data.player.position)}</span>
              <strong>{cleanName(data.player.fullName)}</strong>
              <p>{data.player.fromYear || 'Unknown'} to {playerEndYear(data.player.toYear, data.player)}</p>
            </div>
          </section>
          <div className="dashboard-grid">
            <StatusPanel title="Role" value={readablePosition(data.player.position)} icon={Users} />
            <StatusPanel title="Season Points" value={compactNumber(data.averages?.points)} icon={Target} />
            <StatusPanel title="Season Minutes" value={compactNumber(data.averages?.minutes)} icon={Gauge} />
          </div>
          <section className="panel panel-wide">
            <PanelHeader title="Recent Scoring" icon={BarChart3} />
            <ChartFrame empty={!recentGames.length} emptyLabel="No recent games">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={recentGames.map((row) => ({ ...row, label: formatShortDate(row.gameDateTimeEst) }))}>
                  <CartesianGrid stroke="rgba(255,255,255,.08)" vertical={false} />
                  <XAxis dataKey="label" stroke="#8d929f" tickLine={false} />
                  <YAxis stroke="#8d929f" tickLine={false} />
                  <Tooltip contentStyle={tooltipStyle} />
                  <Bar dataKey="points" fill="#f77f00" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </ChartFrame>
          </section>
          <GameLogTable rows={recentGames} />
        </>
      )}
    </Page>
  );
}

function TeamDetailPage() {
  const { teamId } = useParams();
  const result = useApi(`/api/teams/${teamId}/dashboard`);
  const data = result.data;
  const recentGames = data?.recentGames || [];

  return (
    <Page title={data?.team?.fullName || 'Team'} eyebrow="team dashboard">
      <ErrorBanner message={result.error} />
      {data && (
        <>
          <section className="panel identity-panel">
            <TeamLogo team={data.team} />
            <div>
              <span>{data.team.abbreviation || 'NBA'}</span>
              <strong>{cleanName(data.team.fullName)}</strong>
              <p>Founded {data.team.seasonFounded || 'Unknown'}</p>
            </div>
          </section>
          <div className="dashboard-grid">
            <StatusPanel title="Abbreviation" value={data.team.abbreviation} icon={ShieldCheck} />
            <StatusPanel title="Through" value={teamEndYear(data.team.seasonActiveTill)} icon={Trophy} />
            <StatusPanel title="Founded" value={data.team.seasonFounded} icon={CalendarDays} />
          </div>
          <section className="panel panel-wide">
            <PanelHeader title="Recent Team Scores" icon={BarChart3} />
            <ChartFrame empty={!recentGames.length} emptyLabel="No recent games">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={recentGames.map((row) => ({ ...row, label: formatShortDate(row.gameDateTimeEst) }))}>
                  <CartesianGrid stroke="rgba(255,255,255,.08)" vertical={false} />
                  <XAxis dataKey="label" stroke="#8d929f" tickLine={false} />
                  <YAxis stroke="#8d929f" tickLine={false} />
                  <Tooltip contentStyle={tooltipStyle} />
                  <Bar dataKey="teamScore" fill="#f77f00" radius={[4, 4, 0, 0]} />
                  <Bar dataKey="opponentScore" fill="#3a86ff" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </ChartFrame>
          </section>
        </>
      )}
    </Page>
  );
}

function PlayerPredictionPage({ mode }) {
  const [searchParams] = useSearchParams();
  const [season, setSeason] = useState(defaultSeason);
  const [playerQuery, setPlayerQuery] = useState(searchParams.get('player') || '');
  const [selectedPlayer, setSelectedPlayer] = useState(null);
  const [selectedGame, setSelectedGame] = useState(null);
  const [snapshot, setSnapshot] = useState(null);
  const [prediction, setPrediction] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const title = mode === 'fantasy' ? 'Fantasy Pick' : 'Player Pick';
  const searchPath = playerQuery.trim().length >= 2
    ? `/api/players?query=${encodeURIComponent(playerQuery.trim())}&activeOnly=true&size=6`
    : null;
  const playerSearch = useApi(searchPath);
  const gamesPath = selectedPlayer
    ? `/api/players/${selectedPlayer.id}/games?season=${encodeURIComponent(season)}&size=30`
    : null;
  const playerGames = useApi(gamesPath);

  function choosePlayer(player) {
    setSelectedPlayer(player);
    setSelectedGame(null);
    setPrediction(null);
    setSnapshot(null);
    setError('');
  }

  async function submitPrediction() {
    if (!selectedPlayer || !selectedGame) {
      setError('Pick a player and game first');
      return;
    }
    setLoading(true);
    setError('');
    setPrediction(null);
    try {
      const nextSnapshot = await apiGet(
        `/api/features/player-snapshots/latest?gameId=${encodeURIComponent(selectedGame.gameId)}&playerId=${encodeURIComponent(selectedPlayer.id)}`,
      );
      setSnapshot(nextSnapshot);
      const response = await apiPost(`/api/predictions/${mode}`, {
        gameId: selectedGame.gameId,
        playerId: selectedPlayer.id,
        teamId: nextSnapshot.teamId,
        dataCutoffTime: nextSnapshot.dataCutoffTime,
        features: nextSnapshot.features,
      });
      setPrediction(response);
    } catch (err) {
      setError('We could not find enough pregame data for that pick');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Page title={title} eyebrow="choose a player and game">
      <section className="panel workbench">
        <div className="toolbar">
          <Field label="Player">
            <input
              value={playerQuery}
              onChange={(event) => setPlayerQuery(event.target.value)}
              placeholder="Search by name"
            />
          </Field>
          <Field label="Season">
            <SeasonSelect value={season} onChange={(value) => {
              setSeason(value);
              setSelectedGame(null);
              setPrediction(null);
              setSnapshot(null);
            }} />
          </Field>
        </div>

        <ChoiceList
          title="Choose player"
          rows={pageItems(playerSearch.data)}
          loading={playerSearch.loading && Boolean(searchPath)}
          empty={playerQuery.trim().length < 2 ? 'Type at least 2 letters' : playerSearch.error || 'No players found'}
          selectedId={selectedPlayer?.id}
          getId={(row) => row.id}
          getTitle={(row) => cleanName(row.fullName)}
          getMeta={(row) => `${readablePosition(row.position)} - ${playerEndYear(row.toYear, row)}`}
          getImage={(row) => playerHeadshotUrl(row.id)}
          onChoose={choosePlayer}
        />

        <ChoiceList
          title="Choose game"
          rows={pageItems(playerGames.data)}
          loading={playerGames.loading && Boolean(gamesPath)}
          empty={selectedPlayer ? playerGames.error || 'No games found for that season' : 'Choose a player first'}
          selectedId={selectedGame?.gameId}
          getId={(row) => row.gameId}
          getTitle={playerGameTitle}
          getMeta={playerGameMeta}
          onChoose={(game) => {
            setSelectedGame(game);
            setPrediction(null);
            setSnapshot(null);
            setError('');
          }}
        />

        <div className="workbench-actions">
          <button className="icon-button primary" type="button" onClick={submitPrediction} disabled={loading}>
            {loading ? <Loader2 size={17} className="spin" /> : <Target size={17} />}
            <span>{mode === 'fantasy' ? 'Predict Fantasy' : 'Predict Player Line'}</span>
          </button>
        </div>
        <ErrorBanner message={error} />
      </section>
      <PredictionResult prediction={prediction} fantasy={mode === 'fantasy'} />
      <AdvancedPredictionDetails prediction={prediction} snapshot={snapshot} />
    </Page>
  );
}

function GameScorePredictionPage() {
  const [searchParams] = useSearchParams();
  const [season, setSeason] = useState(defaultSeason);
  const [selectedGame, setSelectedGame] = useState(null);
  const [snapshot, setSnapshot] = useState(null);
  const [prediction, setPrediction] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const games = useApi(`/api/games?season=${encodeURIComponent(season)}&size=30`);

  useEffect(() => {
    const gameId = searchParams.get('gameId');
    if (!gameId || selectedGame || !games.data) return;
    const row = pageItems(games.data).find((game) => String(game.id) === String(gameId));
    if (row) setSelectedGame(row);
  }, [games.data, searchParams, selectedGame]);

  async function submitPrediction() {
    if (!selectedGame) {
      setError('Choose a game first');
      return;
    }
    setLoading(true);
    setError('');
    setPrediction(null);
    try {
      const nextSnapshot = await apiGet(`/api/features/game-snapshots/latest?gameId=${encodeURIComponent(selectedGame.id)}`);
      setSnapshot(nextSnapshot);
      const response = await apiPost('/api/predictions/game-score', {
        gameId: selectedGame.id,
        homeTeamId: nextSnapshot.homeTeamId,
        awayTeamId: nextSnapshot.awayTeamId,
        dataCutoffTime: nextSnapshot.dataCutoffTime,
        features: nextSnapshot.features,
      });
      setPrediction(response);
    } catch (err) {
      setError('We could not find enough pregame data for that matchup');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Page title="Score Pick" eyebrow="choose a matchup">
      <section className="panel workbench">
        <div className="toolbar">
          <Field label="Season">
            <SeasonSelect value={season} onChange={(value) => {
              setSeason(value);
              setSelectedGame(null);
              setPrediction(null);
              setSnapshot(null);
            }} />
          </Field>
        </div>
        <ChoiceList
          title="Choose game"
          rows={pageItems(games.data)}
          loading={games.loading}
          empty={games.error || 'No games found'}
          selectedId={selectedGame?.id}
          getId={(row) => row.id}
          getTitle={gameTitle}
          getMeta={gameMeta}
          onChoose={(game) => {
            setSelectedGame(game);
            setPrediction(null);
            setSnapshot(null);
            setError('');
          }}
        />
        <div className="workbench-actions">
          <button className="icon-button primary" type="button" onClick={submitPrediction} disabled={loading}>
            {loading ? <Loader2 size={17} className="spin" /> : <Trophy size={17} />}
            <span>Predict Score</span>
          </button>
        </div>
        <ErrorBanner message={error} />
      </section>
      <GameScoreResult prediction={prediction} game={selectedGame} />
      <AdvancedPredictionDetails prediction={prediction} snapshot={snapshot} />
    </Page>
  );
}

function ModelPage() {
  const metrics = useApi('/api/model/metrics');
  const versions = useApi('/api/model/versions');

  return (
    <Page title="Accuracy" eyebrow="how close it has been">
      <ErrorBanner message={metrics.error || versions.error} />
      <div className="dashboard-grid">
        <StatusPanel
          title="Player Picks"
          value={metrics.error ? null : 'Ready'}
          subvalue={`${compactNumber(playerTrainingRows(metrics.data), 0)} examples`}
          icon={Target}
        />
        <StatusPanel
          title="Score Picks"
          value={metrics.error ? null : 'Ready'}
          subvalue={`${compactNumber(metrics.data?.gameScoreTrainedRows, 0)} examples`}
          icon={Trophy}
        />
      </div>
      <section className="split">
        <MetricsBlock title="Player Accuracy" evaluation={metrics.data?.playerBaseline} />
        <MetricsBlock title="Score Accuracy" evaluation={metrics.data?.gameScoreBaseline} />
      </section>
      <AdvancedDetails title="Advanced model info">
        <JsonBlock value={versions.data} />
      </AdvancedDetails>
    </Page>
  );
}

function HistoryPage() {
  const result = useApi('/api/predictions/history?limit=50');
  const rows = Array.isArray(result.data) ? result.data : [];

  return (
    <Page title="Prediction History" eyebrow="recent picks">
      <section className="panel">
        <DataTable
          rows={rows}
          columns={[
            ['predictionType', 'Pick', labelize],
            ['confidenceScore', 'Confidence', percent],
            ['requestedAt', 'Requested'],
          ]}
          empty={result.error || 'No prediction history yet'}
        />
      </section>
    </Page>
  );
}

function EntityList({
  title,
  endpoint,
  searchPlaceholder,
  columns,
  rowLink,
  baseParams = {},
  requireQuery = false,
  emptyBeforeSearch = 'Search to begin',
}) {
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const hasQuery = query.trim().length > 0;
  const search = new URLSearchParams({ page, size: 20, ...baseParams });
  if (hasQuery) search.set('query', query.trim());
  const path = requireQuery && !hasQuery ? null : `${endpoint}?${search.toString()}`;
  const result = useApi(path);
  const rows = pageItems(result.data);

  return (
    <Page title={title} eyebrow="browse">
      <section className="panel">
        <div className="toolbar">
          <Field label={searchPlaceholder}>
            <input value={query} onChange={(event) => setQuery(event.target.value)} />
          </Field>
          <button className="icon-button" type="button" onClick={() => setPage(0)}>
            <Search size={17} />
            <span>Search</span>
          </button>
        </div>
        <DataTable
          rows={rows}
          columns={columns}
          action={(row) => (
            <Link to={rowLink(row)} className="mini-link">
              Open
            </Link>
          )}
          empty={!path ? emptyBeforeSearch : result.error || 'No rows found'}
        />
        {path && <Pager page={page} setPage={setPage} last={result.data?.last} />}
      </section>
    </Page>
  );
}

function ChoiceList({ title, rows, loading, empty, selectedId, getId, getTitle, getMeta, getImage, onChoose }) {
  return (
    <div className="choice-section">
      <h3>{title}</h3>
      {loading && <EmptyState label="Loading choices" />}
      {!loading && !rows.length && <EmptyState label={empty} />}
      {!loading && rows.length > 0 && (
        <div className="choice-grid">
          {rows.map((row) => {
            const id = getId(row);
            return (
              <button
                className={`choice-card ${String(selectedId) === String(id) ? 'choice-card--selected' : ''}`}
                type="button"
                key={id}
                onClick={() => onChoose(row)}
              >
                {getImage && <AvatarImage src={getImage(row)} label={getTitle(row)} />}
                <span className="choice-copy">
                  <strong>{getTitle(row)}</strong>
                  <span>{getMeta(row)}</span>
                </span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

function MetricsBlock({ title, evaluation }) {
  const rows = metricRows(evaluation?.metrics);
  const baselineRows = baselineMetricRows(evaluation?.baseline_metrics || evaluation?.baselineMetrics);

  return (
    <section className="panel">
      <PanelHeader title={title} icon={Gauge} />
      {rows.length ? (
        <div className="metric-list">
          {rows.map((row) => (
            <div className="metric-row" key={row.name}>
              <span>{labelize(row.name)}</span>
              <strong>Average miss {compactNumber(row.mae)}</strong>
              <em>Typical miss {compactNumber(row.rmse)}</em>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState label="Run an evaluation to see accuracy here" />
      )}
      {baselineRows.length > 0 && (
        <AdvancedDetails title="Compare with simple baselines">
          <div className="baseline-strip">
            {baselineRows.map((row) => (
              <span key={`${row.group}-${row.name}`}>
                {labelize(row.group)} {labelize(row.name)} average miss {compactNumber(row.mae)}
              </span>
            ))}
          </div>
        </AdvancedDetails>
      )}
    </section>
  );
}

function PredictionResult({ prediction, fantasy }) {
  if (!prediction) return null;
  return (
    <section className="panel panel-wide result-panel">
      <PanelHeader title="Expected Stat Line" icon={Target} />
      <div className="result-grid">
        <MetricTile label="Points" value={compactNumber(prediction.projectedPoints)} />
        <MetricTile label="Rebounds" value={compactNumber(prediction.projectedRebounds)} />
        <MetricTile label="Assists" value={compactNumber(prediction.projectedAssists)} />
        <MetricTile label="Minutes" value={compactNumber(prediction.projectedMinutes)} />
        <MetricTile label="Fantasy" value={compactNumber(prediction.fantasyPoints)} />
        <MetricTile label="Confidence" value={percent(prediction.confidenceScore)} />
      </div>
      <div className="plain-read">
        <strong>{prediction.riskLevel ? `${titleCase(prediction.riskLevel)} risk` : 'Risk unknown'}</strong>
        <span>
          This confidence score is a model confidence estimate, not a betting probability.
        </span>
      </div>
      {fantasy && (
        <div className="result-band">
          <span>Floor {compactNumber(prediction.fantasyFloor)}</span>
          <strong>Ceiling {compactNumber(prediction.fantasyCeiling)}</strong>
          <span>Risk {prediction.riskLevel ? titleCase(prediction.riskLevel) : 'Unknown'}</span>
        </div>
      )}
    </section>
  );
}

function GameScoreResult({ prediction, game }) {
  if (!prediction) return null;
  const favorite = favoriteName(prediction, game);
  return (
    <section className="panel panel-wide result-panel">
      <PanelHeader title="Projected Score" icon={Trophy} />
      <div className="scoreboard">
        <div>
          <span>{game?.homeTeamName || 'Home'}</span>
          <strong>{compactNumber(prediction.homeTeamScore)}</strong>
          <em>Home</em>
        </div>
        <div>
          <span>{game?.awayTeamName || 'Away'}</span>
          <strong>{compactNumber(prediction.awayTeamScore)}</strong>
          <em>Away</em>
        </div>
        <div>
          <span>Favored Team</span>
          <strong>{favorite}</strong>
          <em>By {compactNumber(Math.abs(prediction.pointDifferential))}</em>
        </div>
        <div>
          <span>Confidence</span>
          <strong>{percent(prediction.confidenceScore)}</strong>
          <em>Not a betting probability</em>
        </div>
      </div>
    </section>
  );
}

function AdvancedPredictionDetails({ prediction, snapshot }) {
  if (!prediction && !snapshot) return null;
  return (
    <AdvancedDetails title="How this was calculated">
      <div className="snapshot-summary">
        {snapshot && (
          <>
            <MetaBox label="Pregame data" value={`#${snapshot.snapshotId}`} />
            <MetaBox label="Data cutoff" value={formatDateTime(snapshot.dataCutoffTime)} />
            <MetaBox label="Home team" value={snapshot.homeTeamId || 'Unknown'} />
            <MetaBox label="Away team" value={snapshot.awayTeamId || 'Unknown'} />
          </>
        )}
        {prediction && (
          <>
            <MetaBox label="Prediction" value={`#${prediction.predictionId || 'Unknown'}`} />
            <MetaBox label="Version" value={prediction.modelVersion || 'Unknown'} />
            <MetaBox label="Examples" value={compactNumber(prediction.trainedRows, 0)} />
          </>
        )}
      </div>
      <JsonBlock value={{ factors: prediction?.factors, features: snapshot?.features }} />
    </AdvancedDetails>
  );
}

function AdvancedDetails({ title, children }) {
  return (
    <details className="advanced-details">
      <summary>{title}</summary>
      <div className="advanced-details-body">{children}</div>
    </details>
  );
}

function MetaBox({ label, value }) {
  return (
    <div>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function GameLogTable({ rows }) {
  return (
    <section className="panel panel-wide">
      <PanelHeader title="Recent Game Log" icon={BarChart3} />
      <DataTable
        rows={rows}
        columns={[
          ['gameDateTimeEst', 'Date', formatShortDate],
          ['teamName', 'Team'],
          ['points', 'PTS'],
          ['rebounds', 'REB'],
          ['assists', 'AST'],
          ['minutes', 'MIN'],
        ]}
        empty="No recent games"
      />
    </section>
  );
}

function DataTable({ rows, columns, action, empty }) {
  if (!rows?.length) {
    return <EmptyState label={empty} />;
  }
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {columns.map(([, label]) => (
              <th key={label}>{label}</th>
            ))}
            {action && <th>Action</th>}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={row.id || row.gameId || row.predictionId || index}>
              {columns.map((column) => (
                <td key={column[1]}>{formatColumn(row, column)}</td>
              ))}
              {action && <td>{action(row)}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Page({ title, eyebrow, children }) {
  return (
    <main className="page">
      <div className="page-title">
        <span>{eyebrow}</span>
        <h2>{title}</h2>
      </div>
      {children}
    </main>
  );
}

function Field({ label, children }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
    </label>
  );
}

function SeasonSelect({ value, onChange }) {
  return (
    <select value={value} onChange={(event) => onChange(event.target.value)}>
      {seasonOptions.map((season) => (
        <option key={season} value={season}>
          {seasonLabel(season)}
        </option>
      ))}
    </select>
  );
}

function PlayerIdentity({ player }) {
  return (
    <span className="identity-cell">
      <PlayerAvatar player={player} />
      <span>{cleanName(player?.fullName)}</span>
    </span>
  );
}

function TeamIdentity({ team }) {
  return (
    <span className="identity-cell">
      <TeamLogo team={team} />
      <span>{cleanName(team?.fullName)}</span>
    </span>
  );
}

function MatchupLabel({ game }) {
  return (
    <span className="matchup-cell">
      <span className="team-side">
        <TeamLogo team={{ id: game.awayTeamId, fullName: game.awayTeamName }} />
        <span>{cleanName(game.awayTeamName) || 'Away'}</span>
      </span>
      <span className="at-label">at</span>
      <span className="team-side">
        <TeamLogo team={{ id: game.homeTeamId, fullName: game.homeTeamName }} />
        <span>{cleanName(game.homeTeamName) || 'Home'}</span>
      </span>
    </span>
  );
}

function PlayerAvatar({ player }) {
  return <AvatarImage src={playerHeadshotUrl(player?.id)} label={cleanName(player?.fullName)} />;
}

function TeamLogo({ team }) {
  return <AvatarImage src={teamLogoUrl(team?.id)} label={cleanName(team?.fullName)} square />;
}

function AvatarImage({ src, label, square = false }) {
  const [failed, setFailed] = useState(false);
  const initials = initialsFor(label);
  if (!src || failed) {
    return <span className={`avatar ${square ? 'avatar-square' : ''}`}>{initials}</span>;
  }
  return (
    <span className={`avatar ${square ? 'avatar-square' : ''}`}>
      <img src={src} alt="" onError={() => setFailed(true)} loading="lazy" />
    </span>
  );
}

function StatusPanel({ title, value, subvalue, error, icon: Icon }) {
  return (
    <section className={`panel status-panel ${error ? 'panel-error' : ''}`}>
      <PanelHeader title={title} icon={Icon} />
      <strong>{error ? 'Needs attention' : value || 'Unknown'}</strong>
      <span>{error || subvalue || 'Ready'}</span>
    </section>
  );
}

function PanelHeader({ title, icon: Icon }) {
  return (
    <div className="panel-header">
      <Icon size={18} />
      <h3>{title}</h3>
    </div>
  );
}

function IconLink({ to, icon: Icon, children, variant }) {
  return (
    <Link to={to} className={`icon-button ${variant === 'ghost' ? 'ghost' : 'primary'}`}>
      <Icon size={18} />
      <span>{children}</span>
    </Link>
  );
}

function MetricTile({ label, value }) {
  return (
    <div className="metric-tile">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function ChartFrame({ empty, emptyLabel, children }) {
  return <div className="chart-frame">{empty ? <EmptyState label={emptyLabel} /> : children}</div>;
}

function EmptyState({ label }) {
  return <div className="empty-state">{label}</div>;
}

function ErrorBanner({ message }) {
  if (!message) return null;
  return <div className="error-banner">{message}</div>;
}

function friendlyError(error) {
  const message = typeof error === 'string' ? error : error?.message || '';
  if (/status 404/i.test(message)) return 'That data is not available yet. Try a different game.';
  if (/status 5\d\d/i.test(message)) return 'Something went wrong loading that data. Try another option or refresh.';
  return message;
}

function Pager({ page, setPage, last }) {
  return (
    <div className="pager">
      <button type="button" onClick={() => setPage((value) => Math.max(0, value - 1))} disabled={page === 0}>
        Previous
      </button>
      <span>Page {page + 1}</span>
      <button type="button" onClick={() => setPage((value) => value + 1)} disabled={last === true}>
        Next
      </button>
    </div>
  );
}

function JsonBlock({ value }) {
  return <pre className="json-block">{JSON.stringify(value || {}, null, 2)}</pre>;
}

function useApi(path) {
  const [state, setState] = useState({ data: null, error: '', loading: Boolean(path) });

  useEffect(() => {
    if (!path) {
      setState({ data: null, error: '', loading: false });
      return undefined;
    }
    let active = true;
    setState({ data: null, error: '', loading: true });
    apiGet(path)
      .then((data) => {
        if (active) setState({ data, error: '', loading: false });
      })
      .catch((err) => {
        if (active) setState({ data: null, error: friendlyError(err), loading: false });
      });
    return () => {
      active = false;
    };
  }, [path]);

  return state;
}

function metricRows(metrics) {
  if (!metrics || typeof metrics !== 'object') return [];
  return Object.entries(metrics).map(([name, values]) => ({
    name,
    mae: values?.mae,
    rmse: values?.rmse,
  }));
}

function baselineMetricRows(baselines) {
  if (!baselines || typeof baselines !== 'object') return [];
  return Object.entries(baselines).flatMap(([group, metrics]) =>
    metricRows(metrics).map((row) => ({ ...row, group })),
  );
}

function playerTrainingRows(metrics) {
  return metrics?.playerTrainedRows ?? metrics?.trainedRows;
}

function seasonLabel(value) {
  const start = Number(value);
  return Number.isFinite(start) ? `${start}-${start + 1}` : 'Unknown';
}

function formatColumn(row, [key, , formatter]) {
  return formatter ? formatter(row[key], row) : formatCell(row[key]);
}

function playerEndYear(value, row) {
  if (row?.active && (value === null || value === undefined || Number(value) >= currentSeasonStartYear())) {
    return 'Present';
  }
  return value || 'Unknown';
}

function teamEndYear(value) {
  return value && Number(value) < 2100 ? value : 'Present';
}

function readablePosition(value) {
  if (!value) return 'Unknown';
  return String(value)
    .split('/')
    .map((part) => ({ G: 'Guard', F: 'Forward', C: 'Center' })[part] || part)
    .join(' / ');
}

function playerGameTitle(row) {
  const location = row.home ? 'home' : 'away';
  return `${formatShortDate(row.gameDateTimeEst)} ${location} game`;
}

function playerGameMeta(row) {
  const result = row.win === true ? 'Win' : row.win === false ? 'Loss' : 'Result unknown';
  return `${row.teamName || 'Team unknown'} - ${result}`;
}

function gameTitle(row) {
  return `${cleanName(row.awayTeamName) || 'Away'} at ${cleanName(row.homeTeamName) || 'Home'}`;
}

function gameMeta(row) {
  return `${formatShortDate(row.gameDateTimeEst || row.gameDate)} - ${scoreText(row)}`;
}

function scoreText(row) {
  if (row.homeScore === null || row.homeScore === undefined || row.awayScore === null || row.awayScore === undefined) {
    return 'Score pending';
  }
  return `${row.awayScore}-${row.homeScore}`;
}

function favoriteName(prediction, game) {
  if (!prediction.predictedWinnerTeamId) return 'No favorite';
  if (String(prediction.predictedWinnerTeamId) === String(prediction.homeTeamId)) {
    return game?.homeTeamName || 'Home';
  }
  if (String(prediction.predictedWinnerTeamId) === String(prediction.awayTeamId)) {
    return game?.awayTeamName || 'Away';
  }
  return 'Unknown';
}

function labelize(value) {
  return String(value || '')
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function titleCase(value) {
  return String(value || '')
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function cleanName(value) {
  const text = String(value || '').replace(/\s+/g, ' ').trim();
  return text && text.toLowerCase() !== 'null' ? text : 'Unknown';
}

function initialsFor(value) {
  return cleanName(value)
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0])
    .join('')
    .toUpperCase() || 'NBA';
}

function playerHeadshotUrl(playerId) {
  return playerId ? `https://cdn.nba.com/headshots/nba/latest/1040x760/${playerId}.png` : '';
}

function teamLogoUrl(teamId) {
  return teamId ? `https://cdn.nba.com/logos/nba/${teamId}/primary/L/logo.svg` : '';
}

function formatCell(value) {
  if (value === null || value === undefined || value === '') return 'Unknown';
  if (typeof value === 'number') return Number.isInteger(value) ? value : compactNumber(value, 2);
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (typeof value === 'string' && value.includes('T')) return formatDateTime(value);
  return value;
}

function formatDateTime(value) {
  if (!value) return 'Unknown';
  return String(value).replace('T', ' ').replace(/\.\d+$/, '');
}

function formatShortDate(value) {
  if (!value) return 'Unknown';
  const [date] = String(value).split('T');
  return date || 'Unknown';
}

function currentSeasonStartYear() {
  const today = new Date();
  return today.getMonth() >= 9 ? today.getFullYear() : today.getFullYear() - 1;
}

const tooltipStyle = {
  background: '#0b0d11',
  border: '1px solid rgba(255,255,255,.18)',
  borderRadius: 6,
  color: '#f4f4f5',
};

export default App;
