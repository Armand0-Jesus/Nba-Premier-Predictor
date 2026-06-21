package com.armandorodriguez.nba_premier_predictor.ingestion;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CsvRowReader {

    private CsvRowReader() {
    }

    static void read(BufferedReader reader, CsvRowConsumer consumer) throws IOException {
        String headerLine = reader.readLine();
        if (headerLine == null) {
            return;
        }

        List<String> headers = parseLine(headerLine);
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            columns.put(headers.get(i), i);
        }

        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                consumer.accept(new CsvRow(columns, parseLine(line)));
            }
        }
    }

    static List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        values.add(current.toString());
        return values;
    }

    @FunctionalInterface
    interface CsvRowConsumer {
        void accept(CsvRow row) throws IOException;
    }
}
