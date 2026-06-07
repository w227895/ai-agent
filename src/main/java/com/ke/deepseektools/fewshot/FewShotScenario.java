package com.ke.deepseektools.fewshot;

import java.util.List;

public record FewShotScenario(
        String code,
        String name,
        String description,
        String inputLabel,
        String systemInstruction,
        String outputContract,
        String toolProfile,
        String promptCode,
        LlmPromptTemplate mainPrompt,
        String schemaCode,
        LlmOutputSchema outputSchema,
        List<FewShotExample> examples) {

    public static final String NO_TOOLS = "none";

    public FewShotScenario withExamples(List<FewShotExample> newExamples) {
        return new FewShotScenario(code, name, description, inputLabel, systemInstruction, outputContract,
                toolProfile, promptCode, mainPrompt, schemaCode, outputSchema, List.copyOf(newExamples));
    }

    public FewShotScenario withMainPrompt(LlmPromptTemplate newMainPrompt) {
        return new FewShotScenario(code, name, description, inputLabel, systemInstruction, outputContract,
                toolProfile, promptCode, newMainPrompt, schemaCode, outputSchema,
                examples == null ? List.of() : List.copyOf(examples));
    }

    public FewShotScenario withOutputSchema(LlmOutputSchema newOutputSchema) {
        return new FewShotScenario(code, name, description, inputLabel, systemInstruction, outputContract,
                toolProfile, promptCode, mainPrompt, schemaCode, newOutputSchema,
                examples == null ? List.of() : List.copyOf(examples));
    }
}
