import {
  Activity,
  BarChart3,
  CalendarDays,
  ClipboardList,
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
  { to: '/predict/player', label: 'Player', icon: Target },
  { to: '/predict/fantasy', label: 'Fantasy', icon: Sparkles },
  { to: '/predict/game-score', label: 'Game Score', icon: Trophy },
  { to: '/model', label: 'Model', icon: Gauge },
  { to: '/history', label: 'History', icon: History },
];

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
          <span className="brand-kicker">pre-game intelligence</span>
          <h1>NBA Premier Predictor</h1>
        </div>
        <div className="hero-copy">
          <p>Leakage-safe projections for player stats, fantasy output, and matchup scores.</p>
          <div className="hero-actions">
            <IconLink to="/dashboard" icon={Activity}>
              Enter Dashboard
            </IconLink>
            <IconLink to="/predict/game-score" icon={Trophy} variant="ghost">
              Game Score
            </IconLink>
          </div>
        </div>
        <div className="hero-visual" aria-label="Dunk silhouette">
          <img src={dunkSilhouette} alt="" />
          <div className="score-strip">
            <span>RIDGE BASELINE</span>
            <strong>PRE TIPOFF</strong>
          </div>
        </div>
      </section>

      <section className="landing-grid" aria-label="Platform snapshot">
        <MetricTile label="Player" value="PTS REB AST" />
        <MetricTile label="Fantasy" value="FLOOR CEIL RISK" />
        <MetricTile label="Team" value="HOME AWAY DIFF" />
        <MetricTile label="Model" value="MAE RMSE VERSION" />
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
        <header className="topbar">
          <span>Spring Boot API</span>
          <span>PostgreSQL Source of Truth</span>
          <span>FastAPI Internal ML</span>
        </header>
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
    <Page title="Prediction Dashboard" eyebrow="command center">
      <div className="dashboard-grid">
        <StatusPanel title="API Health" value={health.data?.status || 'checking'} error={health.error} icon={Activity} />
        <StatusPanel
          title="Player Model"
          value={metrics.data?.playerModelVersion || versionName(versions.data?.activeModel)}
          subvalue={`${compactNumber(metrics.data?.playerTrainedRows, 0)} rows`}
          error={metrics.error || versions.error}
          icon={Target}
        />
        <StatusPanel
          title="Game Score Model"
          value={metrics.data?.gameScoreModelVersion || versionName(versions.data?.gameScoreModel)}
          subvalue={`${compactNumber(metrics.data?.gameScoreTrainedRows, 0)} rows`}
          error={metrics.error || versions.error}
          icon={Trophy}
        />
      </div>

      <section className="panel panel-wide">
        <PanelHeader title="Recent Prediction Confidence" icon={LineChart} />
        <ChartFrame empty={!historyRows.length} emptyLabel={history.error || 'No prediction history yet'}>
          <ResponsiveContainer width="100%" height="100%">
            <ReLineChart data={historyRows.map((row, index) => ({ ...row, index: index + 1 }))}>
              <CartesianGrid stroke="rgba(255,255,255,.08)" vertical={false} />
              <XAxis dataKey="index" stroke="#8d929f" tickLine={false} />
              <YAxis stroke="#8d929f" tickLine={false} domain={[0, 1]} />
              <Tooltip contentStyle={tooltipStyle} />
              <Line type="monotone" dataKey="confidenceScore" stroke="#f77f00" strokeWidth={2} dot={{ r: 3 }} />
            </ReLineChart>
          </ResponsiveContainer>
        </ChartFrame>
      </section>

      <section className="split">
        <MetricsBlock title="Player Holdout" evaluation={metrics.data?.playerBaseline} />
        <MetricsBlock title="Game Score Holdout" evaluation={metrics.data?.gameScoreBaseline} />
      </section>
    </Page>
  );
}

function PlayersPage() {
  return (
    <EntityList
      title="Players"
      endpoint="/api/players"
      searchPlaceholder="Search players"
      columns={[
        ['fullName', 'Player'],
        ['position', 'Pos'],
        ['fromYear', 'From'],
        ['toYear', 'To'],
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
      searchPlaceholder="Search teams"
      columns={[
        ['fullName', 'Team'],
        ['abbreviation', 'Abbr'],
        ['league', 'League'],
        ['seasonFounded', 'Founded'],
      ]}
      rowLink={(row) => `/teams/${row.id}`}
    />
  );
}

function GamesPage() {
  const [season, setSeason] = useState('2023');
  const [teamId, setTeamId] = useState('');
  const [page, setPage] = useState(0);
  const query = new URLSearchParams({ page, size: 20 });
  if (season) query.set('season', season);
  if (teamId) query.set('teamId', teamId);
  const result = useApi(`/api/games?${query.toString()}`);
  const rows = pageItems(result.data);

  return (
    <Page title="Games" eyebrow="schedule and results">
      <section className="panel">
        <div className="toolbar">
          <Field label="Season">
            <input value={season} onChange={(event) => setSeason(event.target.value)} inputMode="numeric" />
          </Field>
          <Field label="Team ID">
            <input value={teamId} onChange={(event) => setTeamId(event.target.value)} inputMode="numeric" />
          </Field>
          <button className="icon-button" type="button" onClick={() => setPage(0)}>
            <Search size={17} />
            <span>Search</span>
          </button>
        </div>
        <DataTable
          rows={rows}
          columns={[
            ['gameDate', 'Date'],
            ['awayTeamName', 'Away'],
            ['homeTeamName', 'Home'],
            ['awayScore', 'Away Pts'],
            ['homeScore', 'Home Pts'],
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
          <div className="dashboard-grid">
            <StatusPanel title="Position" value={data.player.position || 'N/A'} icon={Users} />
            <StatusPanel title="Season Points" value={compactNumber(data.averages?.points)} icon={Target} />
            <StatusPanel title="Season Minutes" value={compactNumber(data.averages?.minutes)} icon={Gauge} />
          </div>
          <section className="panel panel-wide">
            <PanelHeader title="Recent Scoring" icon={BarChart3} />
            <ChartFrame empty={!recentGames.length} emptyLabel="No recent games">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={recentGames}>
                  <CartesianGrid stroke="rgba(255,255,255,.08)" vertical={false} />
                  <XAxis dataKey="gameId" stroke="#8d929f" tickLine={false} />
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
          <div className="dashboard-grid">
            <StatusPanel title="Abbreviation" value={data.team.abbreviation} icon={ShieldCheck} />
            <StatusPanel title="League" value={data.team.league} icon={Trophy} />
            <StatusPanel title="Founded" value={data.team.seasonFounded} icon={CalendarDays} />
          </div>
          <section className="panel panel-wide">
            <PanelHeader title="Recent Team Scores" icon={BarChart3} />
            <ChartFrame empty={!recentGames.length} emptyLabel="No recent games">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={recentGames}>
                  <CartesianGrid stroke="rgba(255,255,255,.08)" vertical={false} />
                  <XAxis dataKey="gameId" stroke="#8d929f" tickLine={false} />
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
  const [gameId, setGameId] = useState(searchParams.get('gameId') || '22300003');
  const [playerId, setPlayerId] = useState(searchParams.get('playerId') || '201939');
  const [teamId, setTeamId] = useState('');
  const [snapshot, setSnapshot] = useState(null);
  const [prediction, setPrediction] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const title = mode === 'fantasy' ? 'Fantasy Prediction' : 'Player Prediction';

  async function loadSnapshot() {
    setLoading(true);
    setError('');
    setPrediction(null);
    try {
      const nextSnapshot = await apiGet(
        `/api/features/player-snapshots/latest?gameId=${encodeURIComponent(gameId)}&playerId=${encodeURIComponent(playerId)}`,
      );
      setSnapshot(nextSnapshot);
      setTeamId(nextSnapshot.teamId || '');
    } catch (err) {
      setError(err.message);
      setSnapshot(null);
    } finally {
      setLoading(false);
    }
  }

  async function submitPrediction() {
    if (!snapshot?.features) {
      setError('Load a feature snapshot first');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const response = await apiPost(`/api/predictions/${mode}`, {
        gameId: numberOrNull(gameId),
        playerId: numberOrNull(playerId),
        teamId: numberOrNull(teamId),
        dataCutoffTime: snapshot.dataCutoffTime,
        features: snapshot.features,
      });
      setPrediction(response);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <Page title={title} eyebrow="pre-game feature snapshot">
      <section className="panel">
        <div className="toolbar">
          <Field label="Game ID">
            <input value={gameId} onChange={(event) => setGameId(event.target.value)} inputMode="numeric" />
          </Field>
          <Field label="Player ID">
            <input value={playerId} onChange={(event) => setPlayerId(event.target.value)} inputMode="numeric" />
          </Field>
          <button className="icon-button" type="button" onClick={loadSnapshot} disabled={loading}>
            {loading ? <Loader2 size={17} className="spin" /> : <ClipboardList size={17} />}
            <span>Load Snapshot</span>
          </button>
          <button className="icon-button primary" type="button" onClick={submitPrediction} disabled={loading || !snapshot}>
            <Target size={17} />
            <span>Predict</span>
          </button>
        </div>
        <ErrorBanner message={error} />
        <SnapshotSummary snapshot={snapshot} />
      </section>
      <PredictionResult prediction={prediction} fantasy={mode === 'fantasy'} />
    </Page>
  );
}

function GameScorePredictionPage() {
  const [searchParams] = useSearchParams();
  const [gameId, setGameId] = useState(searchParams.get('gameId') || '22300003');
  const [snapshot, setSnapshot] = useState(null);
  const [prediction, setPrediction] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function loadSnapshot() {
    setLoading(true);
    setError('');
    setPrediction(null);
    try {
      setSnapshot(await apiGet(`/api/features/game-snapshots/latest?gameId=${encodeURIComponent(gameId)}`));
    } catch (err) {
      setError(err.message);
      setSnapshot(null);
    } finally {
      setLoading(false);
    }
  }

  async function submitPrediction() {
    if (!snapshot?.features) {
      setError('Load a feature snapshot first');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const response = await apiPost('/api/predictions/game-score', {
        gameId: numberOrNull(gameId),
        homeTeamId: snapshot.homeTeamId,
        awayTeamId: snapshot.awayTeamId,
        dataCutoffTime: snapshot.dataCutoffTime,
        features: snapshot.features,
      });
      setPrediction(response);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <Page title="Game Score Prediction" eyebrow="matchup model">
      <section className="panel">
        <div className="toolbar">
          <Field label="Game ID">
            <input value={gameId} onChange={(event) => setGameId(event.target.value)} inputMode="numeric" />
          </Field>
          <button className="icon-button" type="button" onClick={loadSnapshot} disabled={loading}>
            {loading ? <Loader2 size={17} className="spin" /> : <ClipboardList size={17} />}
            <span>Load Snapshot</span>
          </button>
          <button className="icon-button primary" type="button" onClick={submitPrediction} disabled={loading || !snapshot}>
            <Trophy size={17} />
            <span>Predict</span>
          </button>
        </div>
        <ErrorBanner message={error} />
        <SnapshotSummary snapshot={snapshot} />
      </section>
      <GameScoreResult prediction={prediction} />
    </Page>
  );
}

function ModelPage() {
  const metrics = useApi('/api/model/metrics');
  const versions = useApi('/api/model/versions');

  return (
    <Page title="Model Metrics" eyebrow="evaluation">
      <ErrorBanner message={metrics.error || versions.error} />
      <div className="dashboard-grid">
        <StatusPanel
          title="Active Player"
          value={metrics.data?.playerModelVersion || versionName(versions.data?.activeModel)}
          subvalue={`${compactNumber(metrics.data?.playerTrainedRows, 0)} rows`}
          icon={Target}
        />
        <StatusPanel
          title="Active Game Score"
          value={metrics.data?.gameScoreModelVersion || versionName(versions.data?.gameScoreModel)}
          subvalue={`${compactNumber(metrics.data?.gameScoreTrainedRows, 0)} rows`}
          icon={Trophy}
        />
      </div>
      <section className="split">
        <MetricsBlock title="Player Stat Accuracy" evaluation={metrics.data?.playerBaseline} />
        <MetricsBlock title="Game Score Accuracy" evaluation={metrics.data?.gameScoreBaseline} />
      </section>
      <section className="panel panel-wide">
        <PanelHeader title="Model Version Payload" icon={Gauge} />
        <JsonBlock value={versions.data} />
      </section>
    </Page>
  );
}

function HistoryPage() {
  const result = useApi('/api/predictions/history?limit=50');
  const rows = Array.isArray(result.data) ? result.data : [];

  return (
    <Page title="Prediction History" eyebrow="stored outputs">
      <section className="panel">
        <DataTable
          rows={rows}
          columns={[
            ['predictionType', 'Type'],
            ['gameId', 'Game'],
            ['playerId', 'Player'],
            ['modelVersion', 'Model'],
            ['confidenceScore', 'Conf'],
            ['requestedAt', 'Requested'],
          ]}
          empty={result.error || 'No prediction history yet'}
        />
      </section>
    </Page>
  );
}

function EntityList({ title, endpoint, searchPlaceholder, columns, rowLink }) {
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const search = new URLSearchParams({ page, size: 20 });
  if (query) search.set('query', query);
  const result = useApi(`${endpoint}?${search.toString()}`);
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
          empty={result.error || 'No rows found'}
        />
        <Pager page={page} setPage={setPage} last={result.data?.last} />
      </section>
    </Page>
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
              <strong>MAE {compactNumber(row.mae)}</strong>
              <em>RMSE {compactNumber(row.rmse)}</em>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState label="No evaluation metrics loaded" />
      )}
      {baselineRows.length > 0 && (
        <div className="baseline-strip">
          {baselineRows.slice(0, 3).map((row) => (
            <span key={`${row.group}-${row.name}`}>
              {labelize(row.group)} {labelize(row.name)} MAE {compactNumber(row.mae)}
            </span>
          ))}
        </div>
      )}
    </section>
  );
}

function PredictionResult({ prediction, fantasy }) {
  if (!prediction) return null;
  return (
    <section className="panel panel-wide result-panel">
      <PanelHeader title="Prediction Result" icon={Target} />
      <div className="result-grid">
        <MetricTile label="Points" value={compactNumber(prediction.projectedPoints)} />
        <MetricTile label="Rebounds" value={compactNumber(prediction.projectedRebounds)} />
        <MetricTile label="Assists" value={compactNumber(prediction.projectedAssists)} />
        <MetricTile label="Minutes" value={compactNumber(prediction.projectedMinutes)} />
        <MetricTile label="Fantasy" value={compactNumber(prediction.fantasyPoints)} />
        <MetricTile label="Confidence" value={percent(prediction.confidenceScore)} />
      </div>
      {fantasy && (
        <div className="result-band">
          <span>Floor {compactNumber(prediction.fantasyFloor)}</span>
          <strong>Ceiling {compactNumber(prediction.fantasyCeiling)}</strong>
          <span>Risk {prediction.riskLevel || 'N/A'}</span>
        </div>
      )}
      <FooterMeta prediction={prediction} />
    </section>
  );
}

function GameScoreResult({ prediction }) {
  if (!prediction) return null;
  return (
    <section className="panel panel-wide result-panel">
      <PanelHeader title="Game Score Result" icon={Trophy} />
      <div className="scoreboard">
        <div>
          <span>Home</span>
          <strong>{compactNumber(prediction.homeTeamScore)}</strong>
          <em>{prediction.homeTeamId}</em>
        </div>
        <div>
          <span>Away</span>
          <strong>{compactNumber(prediction.awayTeamScore)}</strong>
          <em>{prediction.awayTeamId}</em>
        </div>
        <div>
          <span>Diff</span>
          <strong>{compactNumber(prediction.pointDifferential)}</strong>
          <em>{prediction.predictedWinnerTeamId ? `Winner ${prediction.predictedWinnerTeamId}` : 'Tie'}</em>
        </div>
        <div>
          <span>Confidence</span>
          <strong>{percent(prediction.confidenceScore)}</strong>
          <em>{prediction.modelVersion}</em>
        </div>
      </div>
      <FooterMeta prediction={prediction} />
    </section>
  );
}

function SnapshotSummary({ snapshot }) {
  if (!snapshot) {
    return <EmptyState label="No feature snapshot loaded" />;
  }
  const previewKeys = Object.keys(snapshot.features || {}).slice(0, 10);
  return (
    <div className="snapshot-summary">
      <div>
        <span>Snapshot</span>
        <strong>#{snapshot.snapshotId}</strong>
      </div>
      <div>
        <span>Cutoff</span>
        <strong>{formatDateTime(snapshot.dataCutoffTime)}</strong>
      </div>
      <div>
        <span>Home</span>
        <strong>{snapshot.homeTeamId || 'N/A'}</strong>
      </div>
      <div>
        <span>Away</span>
        <strong>{snapshot.awayTeamId || 'N/A'}</strong>
      </div>
      <div className="feature-chip-row">
        {previewKeys.map((key) => (
          <span key={key}>{labelize(key)}</span>
        ))}
      </div>
    </div>
  );
}

function GameLogTable({ rows }) {
  return (
    <section className="panel panel-wide">
      <PanelHeader title="Recent Game Log" icon={ClipboardList} />
      <DataTable
        rows={rows}
        columns={[
          ['gameDateTimeEst', 'Date'],
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
              {columns.map(([key]) => (
                <td key={key}>{formatCell(row[key])}</td>
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

function StatusPanel({ title, value, subvalue, error, icon: Icon }) {
  return (
    <section className={`panel status-panel ${error ? 'panel-error' : ''}`}>
      <PanelHeader title={title} icon={Icon} />
      <strong>{error ? 'Offline' : value || 'N/A'}</strong>
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

function FooterMeta({ prediction }) {
  return (
    <div className="footer-meta">
      <span>Prediction #{prediction.predictionId || 'N/A'}</span>
      <span>{prediction.modelVersion || 'No model version'}</span>
      <span>{compactNumber(prediction.trainedRows, 0)} trained rows</span>
    </div>
  );
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
  const [state, setState] = useState({ data: null, error: '', loading: true });

  useEffect(() => {
    let active = true;
    setState({ data: null, error: '', loading: true });
    apiGet(path)
      .then((data) => {
        if (active) setState({ data, error: '', loading: false });
      })
      .catch((err) => {
        if (active) setState({ data: null, error: err.message, loading: false });
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

function versionName(value) {
  return value?.versionName || value?.modelVersion || value || 'N/A';
}

function labelize(value) {
  return String(value || '')
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function numberOrNull(value) {
  if (value === '' || value === null || value === undefined) return null;
  return Number(value);
}

function formatCell(value) {
  if (value === null || value === undefined || value === '') return 'N/A';
  if (typeof value === 'number') return Number.isInteger(value) ? value : compactNumber(value, 2);
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (typeof value === 'string' && value.includes('T')) return formatDateTime(value);
  return value;
}

function formatDateTime(value) {
  if (!value) return 'N/A';
  return String(value).replace('T', ' ').replace(/\.\d+$/, '');
}

const tooltipStyle = {
  background: '#0b0d11',
  border: '1px solid rgba(255,255,255,.18)',
  borderRadius: 6,
  color: '#f4f4f5',
};

export default App;
