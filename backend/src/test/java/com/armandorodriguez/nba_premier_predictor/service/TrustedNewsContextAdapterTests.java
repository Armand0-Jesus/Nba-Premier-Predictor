package com.armandorodriguez.nba_premier_predictor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

class TrustedNewsContextAdapterTests {

    @Test
    void convertsTrustedRssTransactionsIntoStructuredContext() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString()))
                .thenReturn(players(), teams());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(1629630L)))
                .thenReturn(1610612763L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(1628970L)))
                .thenReturn(1610612766L);
        TrustedNewsContextAdapter adapter = new TrustedNewsContextAdapter(
                jdbcTemplate,
                new ObjectMapper().findAndRegisterModules());

        String json = adapter.toContextJson("https://www.espn.com/espn/rss/nba/news", """
                <rss><channel>
                  <item>
                    <title>Ja Morant traded from Grizzlies to Trail Blazers in blockbuster deal</title>
                    <link>https://www.espn.com/nba/story/_/id/1/ja-morant-trade</link>
                    <pubDate>Mon, 29 Jun 2026 18:00:00 GMT</pubDate>
                  </item>
                  <item>
                    <title>Miles Bridges could be traded to Suns as talks continue</title>
                    <link>https://www.espn.com/nba/story/_/id/2/miles-bridges-rumor</link>
                    <pubDate>Mon, 29 Jun 2026 19:00:00 GMT</pubDate>
                  </item>
                </channel></rss>
                """);

        var root = new ObjectMapper().readTree(json);
        assertThat(root.get("source").asText()).isEqualTo("ESPN");
        assertThat(root.get("transactions").size()).isEqualTo(2);

        var morant = root.get("transactions").get(0);
        assertThat(morant.get("playerId").asLong()).isEqualTo(1629630L);
        assertThat(morant.get("fromTeamId").asLong()).isEqualTo(1610612763L);
        assertThat(morant.get("toTeamId").asLong()).isEqualTo(1610612757L);
        assertThat(morant.get("sourceStatus").asText()).isEqualTo("trusted_report");
        assertThat(morant.get("confidence").decimalValue()).isEqualByComparingTo("0.85");
        assertThat(morant.get("affectsProjection").asBoolean()).isTrue();

        var bridges = root.get("transactions").get(1);
        assertThat(bridges.get("playerId").asLong()).isEqualTo(1628970L);
        assertThat(bridges.get("toTeamId").asLong()).isEqualTo(1610612756L);
        assertThat(bridges.get("sourceStatus").asText()).isEqualTo("rumor");
        assertThat(bridges.get("confidence").decimalValue()).isEqualByComparingTo("0.35");
        assertThat(bridges.get("affectsProjection").asBoolean()).isFalse();
    }

    private static List<Map<String, Object>> players() {
        return List.of(
                Map.of("player_id", 1629630L, "full_name", "Ja Morant"),
                Map.of("player_id", 1628970L, "full_name", "Miles Bridges"));
    }

    private static List<Map<String, Object>> teams() {
        return List.of(
                Map.of("team_id", 1610612763L, "city", "Memphis", "name", "Grizzlies", "abbreviation", "MEM"),
                Map.of("team_id", 1610612757L, "city", "Portland", "name", "Trail Blazers", "abbreviation", "POR"),
                Map.of("team_id", 1610612756L, "city", "Phoenix", "name", "Suns", "abbreviation", "PHX"),
                Map.of("team_id", 1610612766L, "city", "Charlotte", "name", "Hornets", "abbreviation", "CHA"));
    }
}
