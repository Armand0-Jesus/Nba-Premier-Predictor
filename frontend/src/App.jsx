import {
  BarChart3,
  CalendarDays,
  Gauge,
  History,
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
  Navigate,
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
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import dunkSilhouette from './assets/dunk-silhouette.jpg';
import kobeJordan from './assets/kobe-jordan.jpg';
import larryMagic from './assets/larry-magic.jpg';
import lebronWade from './assets/lebron-wade.jpg';
import { apiGet, apiPost, compactNumber, pageItems, percent } from './api.js';

const navItems = [
  { to: '/players', label: 'Players', icon: Users },
  { to: '/teams', label: 'Teams', icon: ShieldCheck },
  { to: '/games', label: 'Games', icon: CalendarDays },
  { to: '/predict/player', label: 'Player Pick', icon: Target },
  { to: '/predict/fantasy', label: 'Fantasy Pick', icon: Sparkles },
  { to: '/predict/game-score', label: 'Score Pick', icon: Trophy },
  { to: '/model', label: 'Accuracy', icon: Gauge },
  { to: '/history', label: 'History', icon: History },
];

const nameCorrections = {
  'JamesOn Curry': 'Jameson Curry',
};

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route element={<AppShell />}>
          <Route path="/dashboard" element={<Navigate to="/players" replace />} />
          <Route path="/players" element={<PlayersPage />} />
          <Route path="/players/:playerId" element={<PlayerDetailPage />} />
          <Route path="/teams" element={<TeamsPage />} />
          <Route path="/teams/:teamId" element={<TeamDetailPage />} />
          <Route path="/games" element={<GamesPage />} />
          <Route path="/games/:gameId" element={<GameDetailPage />} />
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
          <h1>NBA Premier Predictor</h1>
        </div>
        <div className="hero-copy">
          <p>Make game or player predictions, see future season predictions or explore and find whatever historic data you want!</p>
          <div className="hero-actions">
            <IconLink to="/predict/player" icon={Target}>Make a Pick</IconLink>
            <IconLink to="/players" icon={Users} variant="ghost">
              Browse Players
            </IconLink>
            <IconLink to="/games" icon={CalendarDays} variant="ghost">
              Games
            </IconLink>
          </div>
        </div>
        <div className="hero-visual" aria-label="Dunk silhouette">
          <img src={dunkSilhouette} alt="" />
        </div>
      </section>

      <section className="legend-collage" aria-label="NBA eras">
        <article className="legend-card legend-card-tall">
          <span className="legend-card-backdrop" style={{ backgroundImage: `url(${larryMagic})` }} />
          <img src={larryMagic} alt="Larry Bird and Magic Johnson fighting for position" />
        </article>
        <article className="legend-card legend-card-wide">
          <span className="legend-card-backdrop" style={{ backgroundImage: `url(${kobeJordan})` }} />
          <img src={kobeJordan} alt="Kobe Bryant and Michael Jordan during a game" />
        </article>
        <article className="legend-card">
          <span className="legend-card-backdrop" style={{ backgroundImage: `url(${lebronWade})` }} />
          <img src={lebronWade} alt="LeBron James and Dwyane Wade in transition" />
        </article>
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
        ['fromYear', 'From', playerStartYear],
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
      ]}
      rowLink={(row) => `/teams/${row.id}`}
    />
  );
}

function GamesPage() {
  const seasons = useApi('/api/seasons');
  const seasonRows = pageItems(seasons.data);
  const [season, setSeason] = useState('');
  const [queryText, setQueryText] = useState('');
  const [queryValue, setQueryValue] = useState('');
  const [page, setPage] = useState(0);
  useEffect(() => {
    if (!season && seasonRows.length) {
      setSeason(String(seasonRows[0].seasonStartYear));
    }
  }, [season, seasonRows]);
  const query = new URLSearchParams({ page, size: 25 });
  if (season) query.set('season', season);
  if (queryValue) query.set('query', queryValue);
  const result = useApi(season ? `/api/games?${query.toString()}` : null);
  const rows = pageItems(result.data);

  return (
    <Page title="Games" eyebrow="browse">
      <section className="panel">
        <div className="toolbar">
          <Field label="Season">
            <SeasonSelect seasons={seasonRows} value={season} onChange={(value) => {
              setSeason(value);
              setPage(0);
            }} />
          </Field>
          <Field label="Find a game">
            <input
              value={queryText}
              onChange={(event) => setQueryText(event.target.value)}
              placeholder="Team, date or matchup"
            />
          </Field>
          <button className="icon-button" type="button" onClick={() => {
            setQueryValue(queryText.trim());
            setPage(0);
          }}>
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
            <span className="action-stack">
              <Link to={`/games/${row.id}`} className="mini-link">Box score</Link>
              <Link to={`/predict/game-score?gameId=${row.id}`} className="mini-link">Predict</Link>
            </span>
          )}
          empty={season ? result.error || 'No games found' : seasons.error || 'Loading seasons'}
        />
        <Pager page={page} setPage={setPage} last={result.data?.last} />
      </section>
    </Page>
  );
}

function PlayerDetailPage() {
  const { playerId } = useParams();
  const seasons = useApi(`/api/players/${playerId}/seasons`);
  const seasonRows = pageItems(seasons.data);
  const [season, setSeason] = useState('');
  const [queryText, setQueryText] = useState('');
  const [queryValue, setQueryValue] = useState('');
  const [page, setPage] = useState(0);
  useEffect(() => {
    if (!season && seasonRows.length) {
      setSeason(String(seasonRows[0].seasonStartYear));
    }
  }, [season, seasonRows]);
  const result = useApi(season ? `/api/players/${playerId}/dashboard?season=${encodeURIComponent(season)}` : `/api/players/${playerId}/dashboard`);
  const gameParams = new URLSearchParams({ page, size: 25 });
  if (season) gameParams.set('season', season);
  if (queryValue) gameParams.set('query', queryValue);
  const games = useApi(season ? `/api/players/${playerId}/games?${gameParams.toString()}` : null);
  const data = result.data;
  const gameRows = pageItems(games.data);

  return (
    <Page title={data?.player?.fullName || 'Player'} eyebrow="profile">
      <ErrorBanner message={result.error || seasons.error || games.error} />
      {data && (
        <>
          <section className="panel identity-panel">
            <PlayerAvatar player={data.player} />
            <div>
              <span>{readablePosition(data.player.position)}</span>
              <strong>{cleanName(data.player.fullName) || 'Player not listed'}</strong>
              <p>{playerStartYear(data.player.fromYear)} to {playerEndYear(data.player.toYear, data.player)}</p>
            </div>
          </section>
          <div className="dashboard-grid">
            <StatusPanel title="Team" value={seasonTeamText(data.seasonTeams)} icon={Users} />
            <StatusPanel title="Season Points Per Game" value={compactNumber(data.averages?.points)} icon={Target} />
            <StatusPanel title="Season Minutes Per Game" value={compactNumber(data.averages?.minutes)} icon={Gauge} />
          </div>
          <section className="panel panel-wide">
            <PanelHeader title="Recent Scoring" icon={BarChart3} />
            <ChartFrame empty={!gameRows.length} emptyLabel="No games found">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={gameRows.slice().reverse().map((row) => ({ ...row, label: formatShortDate(row.gameDateTimeEst) }))}>
                  <CartesianGrid stroke="rgba(255,255,255,.08)" vertical={false} />
                  <XAxis dataKey="label" stroke="#8d929f" tickLine={false} />
                  <YAxis stroke="#8d929f" tickLine={false} />
                  <Tooltip contentStyle={tooltipStyle} />
                  <Bar dataKey="points" fill="#f77f00" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </ChartFrame>
          </section>
          <section className="panel panel-wide">
            <div className="toolbar">
              <Field label="Season">
                <SeasonSelect seasons={seasonRows} value={season} onChange={(value) => {
                  setSeason(value);
                  setPage(0);
                }} />
              </Field>
              <Field label="Find a game">
                <input
                  value={queryText}
                  onChange={(event) => setQueryText(event.target.value)}
                  placeholder="Opponent, date or matchup"
                />
              </Field>
              <button className="icon-button" type="button" onClick={() => {
                setQueryValue(queryText.trim());
                setPage(0);
              }}>
                <Search size={17} />
                <span>Search</span>
              </button>
            </div>
            <GameLogTable rows={gameRows} />
            <Pager page={page} setPage={setPage} last={games.data?.last} />
          </section>
        </>
      )}
    </Page>
  );
}

function TeamDetailPage() {
  const { teamId } = useParams();
  const seasons = useApi(`/api/teams/${teamId}/seasons`);
  const seasonRows = pageItems(seasons.data);
  const [season, setSeason] = useState('');
  const [queryText, setQueryText] = useState('');
  const [queryValue, setQueryValue] = useState('');
  const [page, setPage] = useState(0);
  useEffect(() => {
    if (!season && seasonRows.length) {
      setSeason(String(seasonRows[0].seasonStartYear));
    }
  }, [season, seasonRows]);
  const result = useApi(season ? `/api/teams/${teamId}/dashboard?season=${encodeURIComponent(season)}` : `/api/teams/${teamId}/dashboard`);
  const gameParams = new URLSearchParams({ page, size: 25 });
  if (season) gameParams.set('season', season);
  if (queryValue) gameParams.set('query', queryValue);
  const games = useApi(season ? `/api/teams/${teamId}/games?${gameParams.toString()}` : null);
  const data = result.data;
  const gameRows = pageItems(games.data);

  return (
    <Page title={data?.team?.fullName || 'Team'} eyebrow="team dashboard">
      <ErrorBanner message={result.error || seasons.error || games.error} />
      {data && (
        <>
          <section className="panel identity-panel">
            <TeamLogo team={data.team} />
            <div>
              <span>{data.team.abbreviation || 'NBA'}</span>
              <strong>{cleanName(data.team.fullName) || 'Team not listed'}</strong>
              <p>Founded {data.team.seasonFounded || 'Year not listed'}</p>
            </div>
          </section>
          <div className="dashboard-grid">
            <StatusPanel title="Record" value={teamRecordText(data.regularSeasonRecord)} subvalue={seasonLabel(season)} icon={Trophy} />
            <StatusPanel title="Win Rate" value={teamWinRateText(data.regularSeasonRecord)} subvalue="Regular season" icon={Gauge} />
            <StatusPanel title="Conference" value={data.team.conference || 'Conference not listed'} icon={CalendarDays} />
          </div>
          <section className="panel panel-wide">
            <PanelHeader title="Recent Team Scores" icon={BarChart3} />
            <ChartFrame empty={!gameRows.length} emptyLabel="No games found">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={gameRows.slice().reverse().map((row) => ({ ...row, label: formatShortDate(row.gameDateTimeEst) }))}>
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
          <section className="panel panel-wide">
            <div className="toolbar">
              <Field label="Season">
                <SeasonSelect seasons={seasonRows} value={season} onChange={(value) => {
                  setSeason(value);
                  setPage(0);
                }} />
              </Field>
              <Field label="Find a game">
                <input
                  value={queryText}
                  onChange={(event) => setQueryText(event.target.value)}
                  placeholder="Opponent, date or matchup"
                />
              </Field>
              <button className="icon-button" type="button" onClick={() => {
                setQueryValue(queryText.trim());
                setPage(0);
              }}>
                <Search size={17} />
                <span>Search</span>
              </button>
            </div>
            <TeamGameLogTable rows={gameRows} />
            <Pager page={page} setPage={setPage} last={games.data?.last} />
          </section>
        </>
      )}
    </Page>
  );
}

function GameDetailPage() {
  const { gameId } = useParams();
  const result = useApi(`/api/games/${gameId}/box-score`);
  const data = result.data;
  const game = data?.game;

  return (
    <Page title={game ? gameTitle(game) : 'Game'} eyebrow="box score">
      <ErrorBanner message={result.error} />
      {data && (
        <>
          <section className="panel panel-wide">
            <PanelHeader title="Final Score" icon={Trophy} />
            <div className="scoreboard">
              <div>
                <span>{cleanName(game.awayTeamName) || 'Away'}</span>
                <strong>{scoreNumber(game.awayScore)}</strong>
                <em>Away</em>
              </div>
              <div>
                <span>{cleanName(game.homeTeamName) || 'Home'}</span>
                <strong>{scoreNumber(game.homeScore)}</strong>
                <em>Home</em>
              </div>
              <div>
                <span>Date</span>
                <strong>{formatShortDate(game.gameDateTimeEst || game.gameDate)}</strong>
                <em>{game.gameType || game.gameLabel || 'Game'}</em>
              </div>
            </div>
          </section>

          <section className="panel panel-wide">
            <PanelHeader title="Team Totals" icon={BarChart3} />
            <DataTable
              rows={[data.awayTeam, data.homeTeam].filter(Boolean)}
              columns={[
                ['teamName', 'Team'],
                ['teamScore', 'PTS', statCell],
                ['fieldGoalsMade', 'FGM', statCell],
                ['fieldGoalsAttempted', 'FGA', statCell],
                ['fieldGoalPercentage', 'FG%', fieldGoalText],
                ['rebounds', 'REB', statCell],
                ['assists', 'AST', statCell],
                ['steals', 'STL', statCell],
                ['blocks', 'BLK', statCell],
                ['turnovers', 'TO', statCell],
              ]}
              empty="Team totals are not available"
            />
          </section>

          <section className="panel panel-wide">
            <PanelHeader title={cleanName(game.awayTeamName) || 'Away'} icon={Users} />
            <PlayerBoxScoreTable rows={data.awayPlayers || []} />
          </section>

          <section className="panel panel-wide">
            <PanelHeader title={cleanName(game.homeTeamName) || 'Home'} icon={Users} />
            <PlayerBoxScoreTable rows={data.homePlayers || []} />
          </section>
        </>
      )}
    </Page>
  );
}

function PlayerPredictionPage({ mode }) {
  const [searchParams] = useSearchParams();
  const [season, setSeason] = useState('');
  const [playerQuery, setPlayerQuery] = useState(searchParams.get('player') || '');
  const [selectedPlayer, setSelectedPlayer] = useState(null);
  const [selectedGame, setSelectedGame] = useState(null);
  const [gameQueryText, setGameQueryText] = useState('');
  const [gameQueryValue, setGameQueryValue] = useState('');
  const [gamePage, setGamePage] = useState(0);
  const [snapshot, setSnapshot] = useState(null);
  const [prediction, setPrediction] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const title = mode === 'fantasy' ? 'Fantasy Pick' : 'Player Pick';
  const searchPath = playerQuery.trim().length >= 2
    ? `/api/players?query=${encodeURIComponent(playerQuery.trim())}&size=6`
    : null;
  const playerSearch = useApi(searchPath);
  const seasons = useApi(selectedPlayer ? `/api/players/${selectedPlayer.id}/seasons` : null);
  const seasonRows = pageItems(seasons.data);
  useEffect(() => {
    if (selectedPlayer && !season && seasonRows.length) {
      setSeason(String(seasonRows[0].seasonStartYear));
    }
  }, [selectedPlayer, season, seasonRows]);
  const gameParams = new URLSearchParams({ page: gamePage, size: 12 });
  if (season) gameParams.set('season', season);
  if (gameQueryValue) gameParams.set('query', gameQueryValue);
  const gamesPath = selectedPlayer && season
    ? `/api/players/${selectedPlayer.id}/games?${gameParams.toString()}`
    : null;
  const playerGames = useApi(gamesPath);

  function choosePlayer(player) {
    setSelectedPlayer(player);
    setSeason('');
    setSelectedGame(null);
    setGameQueryText('');
    setGameQueryValue('');
    setGamePage(0);
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
      const nextSnapshot = await apiPost(
        `/api/features/player-snapshots/ensure?gameId=${encodeURIComponent(selectedGame.gameId)}&playerId=${encodeURIComponent(selectedPlayer.id)}`,
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
      setError(friendlyError(err) || 'There is not enough game data for that pick yet');
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
            <SeasonSelect
              seasons={seasonRows}
              value={season}
              disabled={!selectedPlayer || !seasonRows.length}
              placeholder={selectedPlayer ? 'Loading seasons' : 'Search a player first'}
              onChange={(value) => {
              setSeason(value);
              setSelectedGame(null);
              setPrediction(null);
              setSnapshot(null);
              setGamePage(0);
            }} />
          </Field>
          <Field label="Find a game">
            <input
              value={gameQueryText}
              onChange={(event) => setGameQueryText(event.target.value)}
              placeholder="Opponent, date or matchup"
              disabled={!selectedPlayer}
            />
          </Field>
          <button className="icon-button" type="button" onClick={() => {
            setGameQueryValue(gameQueryText.trim());
            setGamePage(0);
          }} disabled={!selectedPlayer}>
            <Search size={17} />
            <span>Search</span>
          </button>
        </div>

        <ChoiceList
          title="Choose player"
          rows={pageItems(playerSearch.data)}
          loading={playerSearch.loading && Boolean(searchPath)}
          empty={playerQuery.trim().length < 2 ? 'Type at least 2 letters' : playerSearch.error || 'No players found'}
          selectedId={selectedPlayer?.id}
          getId={(row) => row.id}
          getTitle={(row) => cleanName(row.fullName) || 'Player not listed'}
          getMeta={(row) => `${readablePosition(row.position)} - ${careerRangeText(row)}`}
          getImage={(row) => playerHeadshotUrl(row.id)}
          onChoose={choosePlayer}
        />

        <ChoiceList
          title="Choose game"
          rows={pageItems(playerGames.data)}
          loading={playerGames.loading && Boolean(gamesPath)}
          empty={selectedPlayer ? playerGames.error || seasons.error || 'No games found for that season' : 'Choose a player first'}
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
        {gamesPath && <Pager page={gamePage} setPage={setGamePage} last={playerGames.data?.last} />}

        <div className="workbench-actions">
          <button className="icon-button primary" type="button" onClick={submitPrediction} disabled={loading}>
            {loading ? <Loader2 size={17} className="spin" /> : <Target size={17} />}
            <span>{mode === 'fantasy' ? 'Predict Fantasy' : 'Predict Player Line'}</span>
          </button>
        </div>
        <ErrorBanner message={error} />
      </section>
      <PredictionResult prediction={prediction} game={selectedGame} fantasy={mode === 'fantasy'} />
    </Page>
  );
}

function GameScorePredictionPage() {
  const [searchParams] = useSearchParams();
  const seasons = useApi('/api/seasons');
  const seasonRows = pageItems(seasons.data);
  const presetGameId = searchParams.get('gameId');
  const presetGame = useApi(presetGameId ? `/api/games/${presetGameId}` : null);
  const [season, setSeason] = useState('');
  const [queryText, setQueryText] = useState('');
  const [queryValue, setQueryValue] = useState('');
  const [page, setPage] = useState(0);
  const [selectedGame, setSelectedGame] = useState(null);
  const [snapshot, setSnapshot] = useState(null);
  const [prediction, setPrediction] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    if (!season && seasonRows.length) {
      setSeason(String(seasonRows[0].seasonStartYear));
    }
  }, [season, seasonRows]);
  useEffect(() => {
    if (presetGame.data && !selectedGame) {
      setSelectedGame(presetGame.data);
      if (presetGame.data.seasonStartYear) setSeason(String(presetGame.data.seasonStartYear));
    }
  }, [presetGame.data, selectedGame]);
  const params = new URLSearchParams({ page, size: 12 });
  if (season) params.set('season', season);
  if (queryValue) params.set('query', queryValue);
  const games = useApi(season ? `/api/games?${params.toString()}` : null);

  useEffect(() => {
    if (!presetGameId || selectedGame || !games.data) return;
    const row = pageItems(games.data).find((game) => String(game.id) === String(presetGameId));
    if (row) setSelectedGame(row);
  }, [games.data, presetGameId, selectedGame]);

  async function submitPrediction() {
    if (!selectedGame) {
      setError('Choose a game first');
      return;
    }
    setLoading(true);
    setError('');
    setPrediction(null);
    try {
      const nextSnapshot = await apiPost(`/api/features/game-snapshots/ensure?gameId=${encodeURIComponent(selectedGame.id)}`);
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
      setError(friendlyError(err) || 'There is not enough game data for that matchup yet');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Page title="Score Pick" eyebrow="choose a matchup">
      <section className="panel workbench">
        <div className="toolbar">
          <Field label="Season">
            <SeasonSelect seasons={seasonRows} value={season} onChange={(value) => {
              setSeason(value);
              setSelectedGame(null);
              setPrediction(null);
              setSnapshot(null);
              setPage(0);
            }} />
          </Field>
          <Field label="Find a game">
            <input
              value={queryText}
              onChange={(event) => setQueryText(event.target.value)}
              placeholder="Team, date or matchup"
            />
          </Field>
          <button className="icon-button" type="button" onClick={() => {
            setQueryValue(queryText.trim());
            setPage(0);
          }}>
            <Search size={17} />
            <span>Search</span>
          </button>
        </div>
        <ChoiceList
          title="Choose game"
          rows={pageItems(games.data)}
          loading={games.loading || presetGame.loading}
          empty={games.error || seasons.error || presetGame.error || 'No games found'}
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
        <Pager page={page} setPage={setPage} last={games.data?.last} />
        <div className="workbench-actions">
          <button className="icon-button primary" type="button" onClick={submitPrediction} disabled={loading}>
            {loading ? <Loader2 size={17} className="spin" /> : <Trophy size={17} />}
            <span>Predict Score</span>
          </button>
        </div>
        <ErrorBanner message={error} />
      </section>
      <GameScoreResult prediction={prediction} game={selectedGame} />
    </Page>
  );
}

function ModelPage() {
  const metrics = useApi('/api/model/metrics');
  const monitoring = useApi('/api/model/monitoring?limit=6');
  const [evaluatedMetrics, setEvaluatedMetrics] = useState(null);
  const [evaluationStarted, setEvaluationStarted] = useState(false);
  const [evaluationError, setEvaluationError] = useState('');
  const [monitoringData, setMonitoringData] = useState(null);
  const [monitoringRefreshError, setMonitoringRefreshError] = useState('');
  const [monitoringRefreshing, setMonitoringRefreshing] = useState(false);
  const displayMetrics = evaluatedMetrics || metrics.data;
  const displayMonitoring = monitoringData || monitoring.data;

  useEffect(() => {
    if (!metrics.data || evaluationStarted || hasAccuracyMetrics(metrics.data)) return;
    setEvaluationStarted(true);
    setEvaluationError('');
    apiPost('/api/model/evaluate')
      .then((data) => setEvaluatedMetrics(data))
      .catch((err) => setEvaluationError(friendlyError(err) || 'Accuracy could not be calculated right now'));
  }, [metrics.data, evaluationStarted]);

  async function refreshMonitoring() {
    setMonitoringRefreshing(true);
    setMonitoringRefreshError('');
    try {
      setMonitoringData(await apiPost('/api/model/prediction-errors/refresh'));
    } catch (err) {
      setMonitoringRefreshError(friendlyError(err) || 'Completed games could not be checked right now');
    } finally {
      setMonitoringRefreshing(false);
    }
  }

  return (
    <Page title="Accuracy" eyebrow="for those who like numbers">
      <ErrorBanner message={metrics.error || evaluationError || monitoring.error || monitoringRefreshError} />
      {!hasAccuracyMetrics(displayMetrics) && !evaluationError && (
        <section className="panel">
          <EmptyState label={evaluationStarted ? 'Calculating accuracy' : 'Loading accuracy'} />
        </section>
      )}
      <div className="dashboard-grid">
        <StatusPanel
          title="Player Picks"
          value={metrics.error ? null : 'Ready'}
          subvalue={`${compactNumber(playerTrainingRows(displayMetrics), 0)} examples`}
          icon={Target}
        />
        <StatusPanel
          title="Score Picks"
          value={metrics.error ? null : 'Ready'}
          subvalue={`${compactNumber(displayMetrics?.gameScoreTrainedRows, 0)} examples`}
          icon={Trophy}
        />
      </div>
      <section className="split">
        <MetricsBlock title="Player Accuracy" evaluation={displayMetrics?.playerBaseline} />
        <MetricsBlock title="Score Accuracy" evaluation={displayMetrics?.gameScoreBaseline} />
      </section>
      <MonitoringBlock
        data={displayMonitoring}
        loading={monitoring.loading}
        refreshing={monitoringRefreshing}
        onRefresh={refreshMonitoring}
      />
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
            ['requestedAt', 'Requested', formatMinuteDateTime],
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
  const emptyLabel = !path ? emptyBeforeSearch : result.loading ? `Loading ${title.toLowerCase()}` : result.error || 'No rows found';

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
              View
            </Link>
          )}
          empty={emptyLabel}
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
              <strong>{hitRateText(row)}</strong>
              <em>Average miss {compactNumber(row.mae)}</em>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState label="Calculating accuracy" />
      )}
      {rows.length > 0 && (
        <AdvancedDetails title="Extra accuracy numbers">
          <div className="baseline-strip">
            {rows.map((row) => (
              <span key={row.name}>
                {labelize(row.name)} RMSE {compactNumber(row.rmse)}
              </span>
            ))}
          </div>
        </AdvancedDetails>
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

function MonitoringBlock({ data, loading, refreshing, onRefresh }) {
  const summaries = Array.isArray(data?.targetSummaries) ? data.targetSummaries.slice(0, 6) : [];
  const recentErrors = Array.isArray(data?.recentErrors) ? data.recentErrors.slice(0, 6) : [];

  return (
    <section className="panel panel-wide">
      <div className="panel-heading">
        <PanelHeader title="Recent Pick Checks" icon={ShieldCheck} />
        <button className="icon-button" type="button" onClick={onRefresh} disabled={refreshing}>
          {refreshing ? <Loader2 size={17} className="spin" /> : <Gauge size={17} />}
          <span>Update completed games</span>
        </button>
      </div>
      {loading && !data && <EmptyState label="Loading recent checks" />}
      {!loading && !summaries.length && !recentErrors.length && (
        <EmptyState label="No completed picks checked yet" />
      )}
      {summaries.length > 0 && (
        <div className="metric-list">
          {summaries.map((row) => (
            <div className="metric-row" key={row.targetVariable}>
              <span>{labelize(row.targetVariable)}</span>
              <strong>Average miss {compactNumber(row.averageMiss)}</strong>
              <em>{compactNumber(row.predictionCount, 0)} checks</em>
            </div>
          ))}
        </div>
      )}
      {recentErrors.length > 0 && (
        <div className="recent-checks">
          <span>Recent misses</span>
          <div className="baseline-strip">
            {recentErrors.map((row) => (
              <span key={row.id}>
                {labelize(row.predictionType)} {labelize(row.targetVariable)} missed by {compactNumber(row.absoluteError)}
              </span>
            ))}
          </div>
        </div>
      )}
    </section>
  );
}

function PredictionResult({ prediction, game, fantasy }) {
  if (!prediction) return null;
  const actualStatLine = game && game.points !== null && game.points !== undefined;

  if (fantasy) {
    return (
      <section className="panel panel-wide result-panel">
        <PanelHeader title="Fantasy Outlook" icon={Sparkles} />
        <div className="result-grid">
          <MetricTile label="Fantasy Points" value={scoreNumber(prediction.fantasyPoints)} />
          <MetricTile label="Floor" value={scoreNumber(prediction.fantasyFloor)} />
          <MetricTile label="Ceiling" value={scoreNumber(prediction.fantasyCeiling)} />
          <MetricTile label="Minutes" value={scoreNumber(prediction.projectedMinutes)} />
        </div>
        <div className="plain-read">
          <strong>{fantasySummary(prediction)}</strong>
          <span>{statLineSummary(prediction)}</span>
        </div>
        {actualStatLine && (
          <div className="result-band">
            <span>Actual stat line</span>
            <strong>{statCell(game.points)} PTS - {statCell(game.rebounds)} REB - {statCell(game.assists)} AST</strong>
            <span>{statCell(game.minutes)} MIN</span>
          </div>
        )}
      </section>
    );
  }

  return (
    <section className="panel panel-wide result-panel">
      <PanelHeader title="Expected Stat Line" icon={Target} />
      <div className="result-grid">
        <MetricTile label="Points" value={scoreNumber(prediction.projectedPoints)} />
        <MetricTile label="Rebounds" value={scoreNumber(prediction.projectedRebounds)} />
        <MetricTile label="Assists" value={scoreNumber(prediction.projectedAssists)} />
        <MetricTile label="Steals" value={scoreNumber(prediction.projectedSteals)} />
        <MetricTile label="Blocks" value={scoreNumber(prediction.projectedBlocks)} />
        <MetricTile label="Turnovers" value={scoreNumber(prediction.projectedTurnovers)} />
        <MetricTile label="Minutes" value={scoreNumber(prediction.projectedMinutes)} />
        <MetricTile label="FG%" value={fieldGoalText(prediction.projectedFieldGoalPercentage)} />
      </div>
      {actualStatLine && (
        <div className="result-band">
          <span>Actual stat line</span>
          <strong>{statCell(game.points)} PTS - {statCell(game.rebounds)} REB - {statCell(game.assists)} AST</strong>
          <span>{statCell(game.minutes)} MIN - {fieldGoalText(game.fieldGoalPercentage)} FG</span>
        </div>
      )}
    </section>
  );
}

function GameScoreResult({ prediction, game }) {
  if (!prediction) return null;
  const projected = legalProjectedScore(prediction);
  const favorite = favoriteName({ ...prediction, predictedWinnerTeamId: projected.predictedWinnerTeamId }, game);
  return (
    <section className="panel panel-wide result-panel">
      <PanelHeader title="Projected Score" icon={Trophy} />
      <div className="scoreboard">
        <div>
          <span>{game?.homeTeamName || 'Home'}</span>
          <strong>{scoreNumber(projected.homeTeamScore)}</strong>
          <em>Home</em>
        </div>
        <div>
          <span>{game?.awayTeamName || 'Away'}</span>
          <strong>{scoreNumber(projected.awayTeamScore)}</strong>
          <em>Away</em>
        </div>
        <div>
          <span>Favored Team</span>
          <strong>{favorite}</strong>
          <em>By {scoreNumber(projected.margin)}</em>
        </div>
        <div>
          <span>Projected Total</span>
          <strong>{scoreNumber(projected.homeTeamScore + projected.awayTeamScore)}</strong>
          <em>Combined points</em>
        </div>
      </div>
      {game && game.homeScore !== null && game.homeScore !== undefined && game.awayScore !== null && game.awayScore !== undefined && (
        <div className="result-band">
          <span>Final score</span>
          <strong>{cleanName(game.awayTeamName) || 'Away'} {scoreNumber(game.awayScore)} - {cleanName(game.homeTeamName) || 'Home'} {scoreNumber(game.homeScore)}</strong>
          <span>{formatShortDate(game.gameDateTimeEst || game.gameDate)}</span>
        </div>
      )}
    </section>
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

function GameLogTable({ rows }) {
  return (
    <>
      <PanelHeader title="Recent Game Log" icon={BarChart3} />
      <DataTable
        rows={rows}
        columns={[
          ['gameDateTimeEst', 'Date', formatShortDate],
          ['opponentTeamName', 'Opponent'],
          ['recordAfterGame', 'Record', formatCell],
          ['win', 'Result', resultText],
          ['teamScore', 'Score', (_, row) => teamScoreText(row)],
          ['points', 'PTS', statCell],
          ['rebounds', 'REB', statCell],
          ['assists', 'AST', statCell],
          ['steals', 'STL', statCell],
          ['blocks', 'BLK', statCell],
          ['turnovers', 'TO', statCell],
          ['plusMinus', '+/-', plusMinusText],
          ['fieldGoalPercentage', 'FG%', fieldGoalText],
          ['minutes', 'MIN', statCell],
        ]}
        empty="No recent games"
      />
    </>
  );
}

function TeamGameLogTable({ rows }) {
  return (
    <>
      <PanelHeader title="Game Log" icon={BarChart3} />
      <DataTable
        rows={rows}
        columns={[
          ['gameDateTimeEst', 'Date', formatShortDate],
          ['opponentTeamName', 'Opponent'],
          ['recordAfterGame', 'Record', formatCell],
          ['win', 'Result', resultText],
          ['teamScore', 'Score', (_, row) => teamScoreText(row)],
          ['rebounds', 'REB', statCell],
          ['assists', 'AST', statCell],
          ['steals', 'STL', statCell],
          ['blocks', 'BLK', statCell],
          ['turnovers', 'TO', statCell],
        ]}
        action={(row) => (
          <Link to={`/games/${row.gameId}`} className="mini-link">
            Box score
          </Link>
        )}
        empty="No games found"
      />
    </>
  );
}

function PlayerBoxScoreTable({ rows }) {
  const hasListedRoles = rows?.some((row) => row.startingPosition);
  return (
    <DataTable
      rows={rows}
      columns={[
        ['playerName', 'Player'],
        ['startingPosition', 'Role', (value) => boxScorePosition(value, hasListedRoles)],
        ['minutes', 'MIN', statCell],
        ['points', 'PTS', statCell],
        ['rebounds', 'REB', statCell],
        ['assists', 'AST', statCell],
        ['steals', 'STL', statCell],
        ['blocks', 'BLK', statCell],
        ['turnovers', 'TO', statCell],
        ['fieldGoalsMade', 'FGM', statCell],
        ['fieldGoalsAttempted', 'FGA', statCell],
        ['fieldGoalPercentage', 'FG%', fieldGoalText],
        ['plusMinus', '+/-', plusMinusText],
      ]}
      empty="Player stats are not available"
    />
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

function SeasonSelect({ seasons = [], value, onChange, placeholder = 'Choose season', disabled = false }) {
  const rows = seasons.length ? seasons : [];
  if (!rows.length) {
    return (
      <select value="" disabled>
        <option value="">{placeholder}</option>
      </select>
    );
  }
  return (
    <select value={value || rows[0]?.seasonStartYear || ''} onChange={(event) => onChange(event.target.value)} disabled={disabled}>
      {rows.map((season) => (
        <option key={season.seasonStartYear} value={season.seasonStartYear}>
          {season.label || seasonLabel(season.seasonStartYear)}
        </option>
      ))}
    </select>
  );
}

function PlayerIdentity({ player }) {
  return (
    <span className="identity-cell">
      <PlayerAvatar player={player} />
      <span>{cleanName(player?.fullName) || 'Player not listed'}</span>
    </span>
  );
}

function TeamIdentity({ team }) {
  return (
    <span className="identity-cell">
      <TeamLogo team={team} />
      <span>{cleanName(team?.fullName) || 'Team not listed'}</span>
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
      <strong>{error ? 'Needs attention' : value || 'Not listed'}</strong>
      {(error || subvalue) && <span>{error || subvalue}</span>}
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
    hitRate: values?.hit_rate ?? values?.hitRate,
    hitThreshold: values?.hit_threshold ?? values?.hitThreshold,
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

function hasAccuracyMetrics(metrics) {
  return Boolean(metrics?.playerBaseline?.metrics || metrics?.gameScoreBaseline?.metrics);
}

function hitRateText(row) {
  if (row.hitRate !== null && row.hitRate !== undefined && Number.isFinite(Number(row.hitRate))) {
    const threshold = row.hitThreshold !== null && row.hitThreshold !== undefined ? ` within ${compactNumber(row.hitThreshold, 0)}` : '';
    return `${percent(row.hitRate)}${threshold}`;
  }
  return `Average miss ${compactNumber(row.mae)}`;
}

function fantasySummary(prediction) {
  const floor = scoreNumber(prediction.fantasyFloor);
  const ceiling = scoreNumber(prediction.fantasyCeiling);
  return `${floor}-${ceiling} point range`;
}

function statLineSummary(prediction) {
  return `${scoreNumber(prediction.projectedPoints)} PTS, ${scoreNumber(prediction.projectedRebounds)} REB, ${scoreNumber(prediction.projectedAssists)} AST`;
}

function fieldGoalText(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return 'After game';
  }
  return `${Math.round(Number(value) * 100)}%`;
}

function teamRecordText(record) {
  if (!record) return 'Record not listed';
  return `${record.wins || 0}-${record.losses || 0}`;
}

function teamWinRateText(record) {
  if (!record) return 'Not listed';
  return percent(record.winPercentage);
}

function seasonLabel(value) {
  if (value === null || value === undefined || value === '') return 'Season not listed';
  const start = Number(value);
  return Number.isFinite(start) ? `${start}-${start + 1}` : 'Season not listed';
}

function compactSeasonLabel(value) {
  if (value === null || value === undefined || value === '') return 'Season not listed';
  const start = Number(value);
  return Number.isFinite(start) ? `${start}-${String(start + 1).slice(-2)}` : 'Season not listed';
}

function formatColumn(row, [key, , formatter]) {
  return formatter ? formatter(row[key], row) : formatCell(row[key]);
}

function playerStartYear(value) {
  return value ? compactSeasonLabel(value) : 'Start not listed';
}

function playerEndYear(value, row) {
  if (row?.active && (value === null || value === undefined || Number(value) >= currentSeasonStartYear())) {
    return 'Active';
  }
  return value ? compactSeasonLabel(value) : 'Final season not listed';
}

function teamEndYear(value) {
  return value && Number(value) < 2100 ? seasonLabel(value) : 'Active';
}

function careerRangeText(row) {
  const start = row?.fromYear || 'Start not listed';
  if (row?.active && (row.toYear === null || row.toYear === undefined || Number(row.toYear) >= currentSeasonStartYear())) {
    return `${start} - Active`;
  }
  const finalYear = Number(row?.toYear);
  return Number.isFinite(finalYear) ? `${start} - ${finalYear + 1}` : `${start} - Final season not listed`;
}

function seasonTeamText(rows) {
  if (!rows?.length) return 'Team not listed';
  return rows.map((row) => cleanName(row.teamName)).filter(Boolean).join(' / ') || 'Team not listed';
}

function readablePosition(value) {
  if (!value) return 'Role not listed';
  return String(value)
    .split('/')
    .map((part) => ({ G: 'Guard', F: 'Forward', C: 'Center' })[part] || part)
    .join(' / ');
}

function boxScorePosition(value, hasListedRoles = true) {
  return value ? readablePosition(value) : hasListedRoles ? 'Bench' : 'Lineup not listed';
}

function playerGameTitle(row) {
  return `${formatShortDate(row.gameDateTimeEst)} vs ${cleanName(row.opponentTeamName) || 'Opponent'}`;
}

function playerGameMeta(row) {
  const result = row.win === true ? 'Win' : row.win === false ? 'Loss' : 'Result pending';
  return `${result} - ${teamScoreText(row)} - ${statCell(row.points)} PTS, ${statCell(row.rebounds)} REB, ${statCell(row.assists)} AST`;
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
  return `${scoreNumber(row.awayScore)}-${scoreNumber(row.homeScore)}`;
}

function favoriteName(prediction, game) {
  if (!prediction.predictedWinnerTeamId) return 'No favorite';
  if (String(prediction.predictedWinnerTeamId) === String(prediction.homeTeamId)) {
    return game?.homeTeamName || 'Home';
  }
  if (String(prediction.predictedWinnerTeamId) === String(prediction.awayTeamId)) {
    return game?.awayTeamName || 'Away';
  }
  return 'No favorite';
}

function legalProjectedScore(prediction) {
  let home = roundedNumber(prediction.homeTeamScore);
  let away = roundedNumber(prediction.awayTeamScore);
  if (home === null || away === null) {
    return {
      homeTeamScore: prediction.homeTeamScore,
      awayTeamScore: prediction.awayTeamScore,
      predictedWinnerTeamId: prediction.predictedWinnerTeamId,
      margin: Math.abs(Number(prediction.pointDifferential) || 0),
    };
  }
  if (home === away) {
    const rawMargin = Number(prediction.pointDifferential) || 0;
    const homeFavored = rawMargin > 0 || String(prediction.predictedWinnerTeamId) === String(prediction.homeTeamId);
    if (homeFavored) home += 1;
    else away += 1;
  }
  const margin = home - away;
  return {
    homeTeamScore: home,
    awayTeamScore: away,
    predictedWinnerTeamId: margin > 0 ? prediction.homeTeamId : prediction.awayTeamId,
    margin: Math.abs(margin),
  };
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
  if (!text || text.toLowerCase() === 'null') return '';
  return nameCorrections[text] || text;
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
  if (value === null || value === undefined || value === '') return 'Not listed';
  if (typeof value === 'number') return Number.isInteger(value) ? value : compactNumber(value, 2);
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(value)) return formatDateTime(value);
  return value;
}

function statCell(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '0';
  return scoreNumber(value);
}

function scoreNumber(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return 'Not listed';
  return String(roundedNumber(value));
}

function roundedNumber(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return null;
  return Math.round(Number(value));
}

function resultText(value) {
  if (value === true) return 'Win';
  if (value === false) return 'Loss';
  return 'Result pending';
}

function teamScoreText(row) {
  if (row.teamScore === null || row.teamScore === undefined || row.opponentScore === null || row.opponentScore === undefined) {
    return 'Score pending';
  }
  return `${scoreNumber(row.teamScore)}-${scoreNumber(row.opponentScore)}`;
}

function plusMinusText(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '0';
  const number = Math.round(Number(value));
  return number > 0 ? `+${number}` : String(number);
}

function formatDateTime(value) {
  if (!value) return 'Date not listed';
  return String(value).replace('T', ' ').replace(/\.\d+$/, '');
}

function formatMinuteDateTime(value) {
  if (!value) return 'Date not listed';
  return formatDateTime(value).slice(0, 16);
}

function formatShortDate(value) {
  if (!value) return 'Date not listed';
  const [date] = String(value).split('T');
  return date || 'Date not listed';
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
