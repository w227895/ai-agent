package com.ke.deepseektools.prompt;

import java.time.LocalDateTime;

public record LlmOutputSchema(
        Long id,
        String schemaCode,
        String schemaName,
        Long sceneId,
        String schemaContent,
        String promptFragment,
        String sampleOutput,
        String description,
        boolean active,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
