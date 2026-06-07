package com.ke.deepseektools.fewshot;

import java.util.List;

public record PromptOptimizationResult(
        String scenarioCode,
        String analysis,
        String suggestedSystemPrompt,
        String suggestedUserPrompt,
        String suggestedFieldDescriptionJson,
        List<FewShotExample> suggestedExamples,
        String rawResult) {
}
