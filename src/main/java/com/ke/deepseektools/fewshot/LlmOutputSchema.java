package com.ke.deepseektools.fewshot;

public record LlmOutputSchema(
        String schemaCode,
        String promptCode,
        String schemaName,
        String schemaJson,
        String fieldDescriptionJson,
        String emptyValueRule,
        boolean active) {
}
