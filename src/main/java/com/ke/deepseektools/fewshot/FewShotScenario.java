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
        List<LlmPromptTemplate> prompts,
        String schemaCode,
        LlmOutputSchema outputSchema,
        List<FewShotExample> examples,
        boolean active) {

    public FewShotScenario(
            String code,
            String name,
            String description,
            String inputLabel,
            String systemInstruction,
            String outputContract,
            String toolProfile,
            String promptCode,
            LlmPromptTemplate mainPrompt,
            List<LlmPromptTemplate> prompts,
            String schemaCode,
            LlmOutputSchema outputSchema,
            List<FewShotExample> examples) {
        this(code, name, description, inputLabel, systemInstruction, outputContract, toolProfile, promptCode,
                mainPrompt, prompts, schemaCode, outputSchema, examples, true);
    }

    public static final String NO_TOOLS = "none";

    public FewShotScenario withExamples(List<FewShotExample> newExamples) {
        return new FewShotScenario(code, name, description, inputLabel, systemInstruction, outputContract,
                toolProfile, promptCode, mainPrompt, prompts == null ? List.of() : List.copyOf(prompts),
                schemaCode, outputSchema, List.copyOf(newExamples), active);
    }

    public FewShotScenario withMainPrompt(LlmPromptTemplate newMainPrompt) {
        return new FewShotScenario(code, name, description, inputLabel, systemInstruction, outputContract,
                toolProfile, promptCode, newMainPrompt, prompts == null ? List.of() : List.copyOf(prompts),
                schemaCode, outputSchema,
                examples == null ? List.of() : List.copyOf(examples), active);
    }

    public FewShotScenario withPrompts(List<LlmPromptTemplate> newPrompts) {
        return new FewShotScenario(code, name, description, inputLabel, systemInstruction, outputContract,
                toolProfile, promptCode, mainPrompt, List.copyOf(newPrompts), schemaCode, outputSchema,
                examples == null ? List.of() : List.copyOf(examples), active);
    }

    public FewShotScenario withOutputSchema(LlmOutputSchema newOutputSchema) {
        return new FewShotScenario(code, name, description, inputLabel, systemInstruction, outputContract,
                toolProfile, promptCode, mainPrompt, prompts == null ? List.of() : List.copyOf(prompts),
                schemaCode, newOutputSchema,
                examples == null ? List.of() : List.copyOf(examples), active);
    }
}
