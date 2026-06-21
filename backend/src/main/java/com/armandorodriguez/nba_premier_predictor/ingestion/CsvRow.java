package com.armandorodriguez.nba_premier_predictor.ingestion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

final class CsvRow {

    private final Map<String, Integer> columns;
    private final List<String> values;

    CsvRow(Map<String, Integer> columns, List<String> values) {
        this.columns = columns;
        this.values = values;
    }

    String string(String column) {
        Integer index = columns.get(column);
        if (index == null || index >= values.size()) {
            return null;
        }
        String value = values.get(index).trim();
        return value.isEmpty() ? null : value;
    }

    Long longValue(String column) {
        String value = string(column);
        return value == null ? null : new BigDecimal(value).longValue();
    }

    Integer intValue(String column) {
        String value = string(column);
        return value == null ? null : new BigDecimal(value).intValue();
    }

    BigDecimal decimalValue(String column) {
        String value = string(column);
        return value == null ? null : new BigDecimal(value);
    }

    Boolean flag(String column) {
        String value = string(column);
        if (value == null) {
            return null;
        }
        return value.equals("1") || value.equalsIgnoreCase("true");
    }
}
