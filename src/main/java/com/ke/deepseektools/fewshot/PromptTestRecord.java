package com.ke.deepseektools.fewshot;

import java.time.LocalDateTime;

public record PromptTestRecord(
        Long id,
        String scenarioCode,
        String scenarioName,
        String promptCode,
        String schemaCode,
        String requestContent,
        String finalPrompt,
        String responseContent,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        long costTime,
        long modelTime,
        long totalTime,
        String status,
        String errorMessage,
        LocalDateTime createTime) {
}
