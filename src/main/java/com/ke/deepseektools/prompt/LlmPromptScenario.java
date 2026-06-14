package com.ke.deepseektools.prompt;

import java.time.LocalDateTime;

public record LlmPromptScenario(
        Long id,
        String sceneCode,
        String sceneName,
        String codeType,
        String codeTypeName,
        String templateType,
        String templateTypeName,
        int mailType,
        String mailTypeName,
        String description,
        boolean active,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
