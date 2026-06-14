package com.ke.deepseektools.prompt;

import java.time.LocalDateTime;

public record LlmPrompt(
        Long id,
        Long sceneId,
        Long outputSchemaId,
        String promptCode,
        String codeType,
        String templateType,
        String userPrompt,
        int priority,
        boolean active,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        String systemPrompt,
        int mailType) {
}
