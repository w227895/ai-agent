package com.ke.deepseektools.fewshot;

import java.time.LocalDateTime;

public record FewShotFailureCase(
        Long id,
        String scenarioCode,
        String input,
        String actualOutput,
        String expectedOutput,
        String problemNote,
        LocalDateTime createdAt) {
}
