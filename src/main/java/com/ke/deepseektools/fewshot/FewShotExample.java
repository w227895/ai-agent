package com.ke.deepseektools.fewshot;

import java.util.List;

public record FewShotExample(
        String id,
        String title,
        String input,
        String expectedOutput,
        List<String> tags) {
}
