package com.ke.deepseektools.fewshot;

public record LlmPromptTemplate(
        String promptCode,
        String codeType,
        String templateType,
        String userPrompt,
        int priority,
        boolean active,
        String systemPrompt,
        int mailType) {
}
