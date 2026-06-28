package com.armandorodriguez.nba_premier_predictor.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.armandorodriguez.nba_premier_predictor.domain.Player;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerAveragesResponse;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerDashboardResponse;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerDetailResponse;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerGameLogResponse;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerSeasonTeamResponse;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerSummaryResponse;
import com.armandorodriguez.nba_premier_predictor.dto.SeasonResponse;
import com.armandorodriguez.nba_premier_predictor.exception.ResourceNotFoundException;
import com.armandorodriguez.nba_premier_predictor.repository.PlayerGameStatsRepository;
import com.armandorodriguez.nba_premier_predictor.repository.PlayerRepository;
import com.armandorodriguez.nba_premier_predictor.util.NbaSeasonResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class PlayerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerService.class);
    private static final String PLAYER_SEARCH_KEY_PREFIX = "playerSearch:";

    private final PlayerRepository playerRepository;
    private final PlayerGameStatsRepository statsRepository;
    private final SeasonService seasonService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean searchCacheEnabled;
    private final java.time.Duration searchCacheTtl;

    public PlayerService(
            PlayerRepository playerRepository,
            PlayerGameStatsRepository statsRepository,
            SeasonService seasonService,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.search-cache.enabled:true}") boolean searchCacheEnabled,
            @Value("${app.search-cache.ttl:5m}") java.time.Duration searchCacheTtl) {
        this.playerRepository = playerRepository;
        this.statsRepository = statsRepository;
        this.seasonService = seasonService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.searchCacheEnabled = searchCacheEnabled;
        this.searchCacheTtl = searchCacheTtl == null || searchCacheTtl.isZero() || searchCacheTtl.isNegative()
                ? java.time.Duration.ofMinutes(5)
                : searchCacheTtl;
    }

    public Page<PlayerSummaryResponse> search(String query, boolean activeOnly, Pageable pageable) {
        String cleanedQuery = clean(query);
        String cacheKey = playerSearchKey(cleanedQuery, activeOnly, pageable);
        if (searchCacheEnabled) {
            Page<PlayerSummaryResponse> cached = cachedPlayerSearch(cacheKey, pageable);
            if (cached != null) {
                return cached;
            }
        }
        Page<PlayerSummaryResponse> page = playerRepository
                .search(cleanedQuery, activeOnly, currentSeasonStartYear(), pageable)
                .map(PlayerSummaryResponse::from);
        cachePlayerSearch(cacheKey, page);
        return page;
    }

    @Cacheable(cacheNames = "playerDetails", key = "#playerId")
    public PlayerDetailResponse get(Long playerId) {
        return PlayerDetailResponse.from(findPlayer(playerId));
    }

    public Page<PlayerGameLogResponse> gameLogs(Long playerId, Integer season, String query, Pageable pageable) {
        findPlayer(playerId);
        return statsRepository.findGameLogs(playerId, season, searchPattern(query), pageable).map(PlayerGameLogResponse::from);
    }

    public List<SeasonResponse> seasons(Long playerId) {
        findPlayer(playerId);
        return seasonService.playerSeasons(playerId);
    }

    @Cacheable(cacheNames = "playerAverages", key = "'v4:' + #playerId + ':' + (#season == null ? 'all' : #season)")
    public PlayerAveragesResponse averages(Long playerId, Integer season) {
        findPlayer(playerId);
        return PlayerAveragesResponse.from(playerId, season, statsRepository.findForAverages(playerId, season));
    }

    @Cacheable(cacheNames = "playerDashboards", key = "'v4:' + #playerId + ':' + (#season == null ? 'all' : #season)")
    public PlayerDashboardResponse dashboard(Long playerId, Integer season) {
        Player player = findPlayer(playerId);
        PlayerAveragesResponse averages = PlayerAveragesResponse.from(playerId, season, statsRepository.findForAverages(playerId, season));
        List<PlayerGameLogResponse> recentGames = statsRepository
                .findGameLogs(playerId, season, null, PageRequest.of(0, 10))
                .map(PlayerGameLogResponse::from)
                .toList();
        List<PlayerSeasonTeamResponse> seasonTeams = statsRepository.findSeasonTeams(playerId, season);
        return new PlayerDashboardResponse(PlayerDetailResponse.from(player), averages, seasonTeams, recentGames);
    }

    private Player findPlayer(Long playerId) {
        return playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player not found: " + playerId));
    }

    private static String clean(String query) {
        return query == null || query.isBlank() ? null : query.trim();
    }

    private static String searchPattern(String query) {
        String cleaned = clean(query);
        return cleaned == null ? null : "%" + cleaned.toLowerCase() + "%";
    }

    private static int currentSeasonStartYear() {
        return NbaSeasonResolver.seasonStartYear(LocalDate.now());
    }

    private Page<PlayerSummaryResponse> cachedPlayerSearch(String cacheKey, Pageable pageable) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, CachedPlayerSearchPage.class).toPage(pageable);
        } catch (JsonProcessingException | DataAccessException ex) {
            LOGGER.debug("Player search cache read failed", ex);
            return null;
        }
    }

    private void cachePlayerSearch(String cacheKey, Page<PlayerSummaryResponse> page) {
        if (!searchCacheEnabled) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(CachedPlayerSearchPage.from(page)),
                    searchCacheTtl);
        } catch (JsonProcessingException | DataAccessException ex) {
            LOGGER.debug("Player search cache write failed", ex);
        }
    }

    private static String playerSearchKey(String query, boolean activeOnly, Pageable pageable) {
        String rawKey = "%s|%s|%d|%d|%s".formatted(
                query == null ? "" : query,
                activeOnly,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort());
        return PLAYER_SEARCH_KEY_PREFIX
                + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(rawKey.getBytes(StandardCharsets.UTF_8));
    }

    public record CachedPlayerSearchPage(
            List<PlayerSummaryResponse> content,
            int pageNumber,
            int pageSize,
            long totalElements) {

        static CachedPlayerSearchPage from(Page<PlayerSummaryResponse> page) {
            return new CachedPlayerSearchPage(
                    page.getContent(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements());
        }

        Page<PlayerSummaryResponse> toPage(Pageable pageable) {
            return new PageImpl<>(content, pageable, totalElements);
        }
    }
}
