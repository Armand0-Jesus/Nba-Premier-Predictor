package com.armandorodriguez.nba_premier_predictor.service;

import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
class TrustedNewsContextAdapter {

    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern STRONG_TRANSACTION = Pattern.compile(
            "\\b(traded|trade|acquired|acquire|sent|deal|signs|signed|signing|agrees|joins|waived|waives|released|releases)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RUMOR_LANGUAGE = Pattern.compile(
            "\\b(could|may|might|interested|interest|targeting|linked|rumor|rumour|pursue|monitoring)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REMOVAL_LANGUAGE = Pattern.compile(
            "\\b(waived|waives|released|releases)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SIGNING_LANGUAGE = Pattern.compile(
            "\\b(signs|signed|signing|agrees|joins)\\b",
            Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    TrustedNewsContextAdapter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    String toContextJson(String sourceUrl, String feedXml) {
        List<PlayerName> players = players();
        List<TeamAlias> teamAliases = teamAliases();
        ObjectNode root = objectMapper.createObjectNode();
        root.put("source", sourceName(sourceUrl));
        root.put("sourceUrl", sourceUrl);
        root.put("sourceStatus", statusFor(sourceUrl));
        root.put("confidence", confidenceFor(sourceUrl));
        ArrayNode transactions = root.putArray("transactions");

        for (FeedItem item : feedItems(feedXml)) {
            TransactionCandidate candidate = candidate(item, sourceUrl, players, teamAliases);
            if (candidate != null) {
                ObjectNode transaction = transactions.addObject();
                transaction.put("playerId", candidate.playerId());
                putNullable(transaction, "fromTeamId", candidate.fromTeamId());
                putNullable(transaction, "toTeamId", candidate.toTeamId());
                transaction.put("transactionType", candidate.transactionType());
                transaction.put("transactionDate", candidate.transactionDate().toString());
                transaction.put("source", sourceName(sourceUrl));
                transaction.put("sourceUrl", candidate.link());
                transaction.put("sourceStatus", candidate.sourceStatus());
                transaction.put("confidence", candidate.confidence());
                transaction.put("affectsProjection", candidate.affectsProjection());
                transaction.put("reportedAt", candidate.reportedAt().toString());
                transaction.put("notes", candidate.title());
            }
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not build trusted news context", ex);
        }
    }

    private TransactionCandidate candidate(
            FeedItem item,
            String sourceUrl,
            List<PlayerName> players,
            List<TeamAlias> teamAliases) {
        String text = item.searchText();
        if (!STRONG_TRANSACTION.matcher(text).find()) {
            return null;
        }
        PlayerName player = firstPlayer(text, players);
        if (player == null) {
            return null;
        }
        List<TeamMention> teams = teamMentions(text, teamAliases);
        if (teams.isEmpty()) {
            return null;
        }
        boolean removal = REMOVAL_LANGUAGE.matcher(text).find();
        TeamMention destination = removal ? null : destinationTeam(text, teams);
        TeamMention sourceTeam = sourceTeam(text, teams);
        Long latestTeamId = latestTeamId(player.id());
        Long fromTeamId = sourceTeam == null ? latestTeamId : sourceTeam.teamId();
        Long toTeamId = destination == null ? null : destination.teamId();
        if (fromTeamId != null && fromTeamId.equals(toTeamId)) {
            fromTeamId = null;
        }
        if (toTeamId == null && fromTeamId == null) {
            return null;
        }
        boolean rumor = RUMOR_LANGUAGE.matcher(text).find();
        String sourceStatus = rumor ? "rumor" : statusFor(sourceUrl);
        BigDecimal confidence = rumor ? new BigDecimal("0.3500") : BigDecimal.valueOf(confidenceFor(sourceUrl));
        String type = removal ? "waived" : SIGNING_LANGUAGE.matcher(text).find() ? "signing" : "trade";
        Instant reportedAt = item.publishedAt() == null ? Instant.now() : item.publishedAt();
        return new TransactionCandidate(
                player.id(),
                fromTeamId,
                toTeamId,
                type,
                LocalDate.ofInstant(reportedAt, ZoneOffset.UTC),
                item.link() == null ? sourceUrl : item.link(),
                sourceStatus,
                confidence,
                !rumor,
                reportedAt,
                item.title());
    }

    private List<FeedItem> feedItems(String feedXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            disableExternalXml(factory);
            var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(feedXml)));
            var nodes = document.getElementsByTagName("item");
            List<FeedItem> items = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                items.add(feedItem((Element) nodes.item(i), false));
            }
            if (!items.isEmpty()) {
                return items;
            }
            nodes = document.getElementsByTagName("entry");
            for (int i = 0; i < nodes.getLength(); i++) {
                items.add(feedItem((Element) nodes.item(i), true));
            }
            return items;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Trusted news source did not return valid RSS or Atom XML", ex);
        }
    }

    private static void disableExternalXml(DocumentBuilderFactory factory) throws ParserConfigurationException {
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    }

    private FeedItem feedItem(Element element, boolean atom) {
        String title = text(element, "title");
        String summary = firstNonBlank(text(element, "description"), text(element, "summary"), text(element, "content"));
        String link = atom ? atomLink(element) : text(element, "link");
        Instant publishedAt = parseInstant(firstNonBlank(text(element, "pubDate"), text(element, "published"), text(element, "updated")));
        return new FeedItem(title, strip(summary), link, publishedAt);
    }

    private static String atomLink(Element element) {
        var links = element.getElementsByTagName("link");
        if (links.getLength() == 0) {
            return null;
        }
        Node node = links.item(0);
        if (node instanceof Element link && link.hasAttribute("href")) {
            return link.getAttribute("href");
        }
        return node.getTextContent();
    }

    private List<PlayerName> players() {
        return jdbcTemplate.queryForList("""
                select player_id, trim(concat(coalesce(first_name, ''), ' ', coalesce(last_name, ''))) as full_name
                from players
                where nba_flag = true
                """).stream()
                .map(row -> new PlayerName(longValue(row.get("player_id")), String.valueOf(row.get("full_name"))))
                .filter(player -> player.id() != null && player.name() != null && !player.name().isBlank())
                .sorted(Comparator.comparingInt((PlayerName player) -> player.normalizedName().length()).reversed())
                .toList();
    }

    private List<TeamAlias> teamAliases() {
        Map<String, TeamAlias> aliases = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList("""
                select team_id, city, name, abbreviation
                from teams
                where team_id in (1610612737,1610612738,1610612739,1610612740,1610612741,
                                  1610612742,1610612743,1610612744,1610612745,1610612746,
                                  1610612747,1610612748,1610612749,1610612750,1610612751,
                                  1610612752,1610612753,1610612754,1610612755,1610612756,
                                  1610612757,1610612758,1610612759,1610612760,1610612761,
                                  1610612762,1610612763,1610612764,1610612765,1610612766)
                """)) {
            Long teamId = longValue(row.get("team_id"));
            String city = stringValue(row.get("city"));
            String name = stringValue(row.get("name"));
            String abbreviation = stringValue(row.get("abbreviation"));
            addAlias(aliases, counts, teamId, city + " " + name);
            addAlias(aliases, counts, teamId, name);
            addAlias(aliases, counts, teamId, abbreviation);
            addCommonAliases(aliases, counts, teamId, city, name);
        }
        return aliases.entrySet().stream()
                .filter(entry -> counts.getOrDefault(entry.getKey(), 0) == 1)
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparingInt((TeamAlias alias) -> alias.alias().length()).reversed())
                .toList();
    }

    private static void addCommonAliases(Map<String, TeamAlias> aliases, Map<String, Integer> counts, Long teamId, String city, String name) {
        String full = normalize(city + " " + name);
        if ("portland trail blazers".equals(full)) {
            addAlias(aliases, counts, teamId, "blazers");
            addAlias(aliases, counts, teamId, "trail blazers");
        } else if ("minnesota timberwolves".equals(full)) {
            addAlias(aliases, counts, teamId, "wolves");
        } else if ("new york knicks".equals(full)) {
            addAlias(aliases, counts, teamId, "knicks");
        } else if ("dallas mavericks".equals(full)) {
            addAlias(aliases, counts, teamId, "mavs");
        } else if ("golden state warriors".equals(full)) {
            addAlias(aliases, counts, teamId, "dubs");
        }
    }

    private static void addAlias(Map<String, TeamAlias> aliases, Map<String, Integer> counts, Long teamId, String alias) {
        String normalized = normalize(alias);
        if (teamId == null || normalized.isBlank()) {
            return;
        }
        counts.merge(normalized, 1, Integer::sum);
        aliases.putIfAbsent(normalized, new TeamAlias(teamId, normalized));
    }

    private static PlayerName firstPlayer(String text, List<PlayerName> players) {
        return players.stream()
                .filter(player -> containsPhrase(text, player.normalizedName()))
                .findFirst()
                .orElse(null);
    }

    private static List<TeamMention> teamMentions(String text, List<TeamAlias> aliases) {
        Map<Long, TeamMention> byTeam = new LinkedHashMap<>();
        for (TeamAlias alias : aliases) {
            int index = phraseIndex(text, alias.alias());
            if (index >= 0) {
                byTeam.putIfAbsent(alias.teamId(), new TeamMention(alias.teamId(), index));
            }
        }
        return byTeam.values().stream()
                .sorted(Comparator.comparingInt(TeamMention::index))
                .toList();
    }

    private static TeamMention destinationTeam(String text, List<TeamMention> mentions) {
        int actionIndex = actionIndex(text);
        int toIndex = text.indexOf(" to ", actionIndex);
        if (toIndex >= 0) {
            TeamMention afterTo = mentions.stream()
                    .filter(mention -> mention.index() > toIndex)
                    .findFirst()
                    .orElse(null);
            if (afterTo != null) {
                return afterTo;
            }
        }
        int withIndex = text.indexOf(" with ", actionIndex);
        if (withIndex >= 0) {
            TeamMention afterWith = mentions.stream()
                    .filter(mention -> mention.index() > withIndex)
                    .findFirst()
                    .orElse(null);
            if (afterWith != null) {
                return afterWith;
            }
        }
        return mentions.stream()
                .filter(mention -> mention.index() > actionIndex)
                .findFirst()
                .orElse(mentions.getFirst());
    }

    private static TeamMention sourceTeam(String text, List<TeamMention> mentions) {
        int fromIndex = text.indexOf(" from ");
        if (fromIndex < 0) {
            return null;
        }
        return mentions.stream()
                .filter(mention -> mention.index() > fromIndex)
                .findFirst()
                .orElse(null);
    }

    private Long latestTeamId(Long playerId) {
        try {
            return jdbcTemplate.queryForObject("""
                    select s.team_id
                    from player_game_stats s
                    join games g on g.game_id = s.game_id
                    where s.player_id = ?
                      and s.team_id is not null
                    order by g.game_date desc nulls last, g.game_date_time_est desc nulls last
                    fetch first 1 row only
                    """, Long.class, playerId);
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private static int actionIndex(String text) {
        var matcher = STRONG_TRANSACTION.matcher(text);
        return matcher.find() ? matcher.start() : 0;
    }

    private static String statusFor(String sourceUrl) {
        String host = host(sourceUrl);
        return host.endsWith("nba.com") ? "official" : "trusted_report";
    }

    private static double confidenceFor(String sourceUrl) {
        return "official".equals(statusFor(sourceUrl)) ? 1.0 : 0.85;
    }

    private static String sourceName(String sourceUrl) {
        String host = host(sourceUrl);
        if (host.contains("espn")) {
            return "ESPN";
        }
        if (host.contains("bleacherreport")) {
            return "Bleacher Report";
        }
        if (host.contains("nba.com")) {
            return "NBA.com";
        }
        return host.isBlank() ? "Trusted NBA news" : host;
    }

    private static String host(String sourceUrl) {
        try {
            return URI.create(sourceUrl).getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static boolean containsPhrase(String text, String phrase) {
        return phraseIndex(text, phrase) >= 0;
    }

    private static int phraseIndex(String text, String phrase) {
        return (" " + text + " ").indexOf(" " + phrase + " ");
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return WHITESPACE.matcher(value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ")).replaceAll(" ").trim();
    }

    private static String strip(String value) {
        return value == null ? "" : TAGS.matcher(value).replaceAll(" ");
    }

    private static String text(Element element, String tagName) {
        var nodes = element.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.RFC_1123_DATE_TIME, DateTimeFormatter.ISO_OFFSET_DATE_TIME)) {
            try {
                return ZonedDateTime.parse(value, formatter).toInstant();
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static String stringValue(Object value) {
        return Objects.toString(value, "");
    }

    private static void putNullable(ObjectNode node, String field, Long value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private record FeedItem(String title, String summary, String link, Instant publishedAt) {
        String searchText() {
            return normalize((title == null ? "" : title) + " " + (summary == null ? "" : summary));
        }
    }

    private record PlayerName(Long id, String name) {
        String normalizedName() {
            return normalize(name);
        }
    }

    private record TeamAlias(Long teamId, String alias) {
    }

    private record TeamMention(Long teamId, int index) {
    }

    private record TransactionCandidate(
            Long playerId,
            Long fromTeamId,
            Long toTeamId,
            String transactionType,
            LocalDate transactionDate,
            String link,
            String sourceStatus,
            BigDecimal confidence,
            boolean affectsProjection,
            Instant reportedAt,
            String title) {
    }
}
