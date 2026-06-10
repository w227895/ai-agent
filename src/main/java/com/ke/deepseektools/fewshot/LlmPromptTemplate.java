package com.ke.deepseektools.fewshot;

import java.util.List;

public record LlmPromptTemplate(
        String promptCode,
        String scenarioCode,
        String codeType,
        String templateType,
        String userPrompt,
        int priority,
        boolean active,
        String systemPrompt,
        int mailType,
        List<LlmOutputSchema> outputSchemas,
        List<FewShotExample> examples) {

    public LlmPromptTemplate withExamples(List<FewShotExample> newExamples) {
        return new LlmPromptTemplate(promptCode, scenarioCode, codeType, templateType, userPrompt, priority, active,
                systemPrompt, mailType, outputSchemas == null ? List.of() : List.copyOf(outputSchemas),
                List.copyOf(newExamples));
    }

    public LlmPromptTemplate withOutputSchemas(List<LlmOutputSchema> newOutputSchemas) {
        return new LlmPromptTemplate(promptCode, scenarioCode, codeType, templateType, userPrompt, priority, active,
                systemPrompt, mailType, List.copyOf(newOutputSchemas), examples == null ? List.of() : List.copyOf(examples));
    }
}
