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
        List<FewShotExample> examples) {

    public static final String NO_TOOLS = "none";

    public FewShotScenario withExamples(List<FewShotExample> newExamples) {
        return new FewShotScenario(code, name, description, inputLabel, systemInstruction, outputContract,
                toolProfile, List.copyOf(newExamples));
    }
}
