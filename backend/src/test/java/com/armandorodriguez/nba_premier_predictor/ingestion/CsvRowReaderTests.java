package com.armandorodriguez.nba_premier_predictor.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CsvRowReaderTests {

    @Test
    void parsesQuotedCommasEscapedQuotesAndEmptyCells() {
        List<String> values = CsvRowReader.parseLine("425,\"James Capers, Scott Foster\",\"He said \"\"play\"\"\",,94");

        assertThat(values).containsExactly(
                "425",
                "James Capers, Scott Foster",
                "He said \"play\"",
                "",
                "94");
    }
}
