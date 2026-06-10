package com.ke.deepseektools.fewshot;

import java.util.List;

public record LlmPromptTemplate(
        String promptCode,
        String codeType,
        String templateType,
        String userPrompt,
        int priority,
        boolean active,
        String systemPrompt,
        int mailType,
        List<FewShotExample> examples) {

    public LlmPromptTemplate withExamples(List<FewShotExample> newExamples) {
        return new LlmPromptTemplate(promptCode, codeType, templateType, userPrompt, priority, active,
                systemPrompt, mailType, List.copyOf(newExamples));
    }
}
