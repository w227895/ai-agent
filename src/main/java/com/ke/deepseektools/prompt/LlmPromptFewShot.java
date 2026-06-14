package com.ke.deepseektools.prompt;

import java.time.LocalDateTime;

public record LlmPromptFewShot(
        Long id,
        Long promptId,
        String title,
        String content,
        int sortOrder,
        boolean active,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
