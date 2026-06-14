package com.ke.deepseektools.prompt;

import java.util.List;

public final class PromptDictionaries {

    private PromptDictionaries() {
    }

    public static DictionaryResult all() {
        return new DictionaryResult(codeTypes(), templateTypes(), mailTypes(), scenarios());
    }

    public static List<DictionaryItem> codeTypes() {
        return List.of(
                new DictionaryItem("1", "供应商"),
                new DictionaryItem("2", "邮箱"),
                new DictionaryItem("3", "短信"),
                new DictionaryItem("4", "短信"),
                new DictionaryItem("after-sale", "售后"));
    }

    public static List<DictionaryItem> templateTypes() {
        return List.of(
                new DictionaryItem("1", "类型识别"),
                new DictionaryItem("2", "字段提取"),
                new DictionaryItem("3", "售后退票"),
                new DictionaryItem("4", "售后短信"),
                new DictionaryItem("5", "售后改期"),
                new DictionaryItem("6", "售后补充"));
    }

    public static List<DictionaryItem> mailTypes() {
        return List.of(
                new DictionaryItem("0", "类型识别"),
                new DictionaryItem("1", "航变字段提取"),
                new DictionaryItem("2", "退票/售后"),
                new DictionaryItem("3", "改期字段提取"),
                new DictionaryItem("9", "售后补充"),
                new DictionaryItem("10", "售后补充"));
    }

    public static List<LlmPromptScenario> scenarios() {
        return List.of(
                new LlmPromptScenario(null, "flight_email", "航变邮件识别",
                        "邮件和供应商维度的航变识别、航变字段提取提示词", true, null, null),
                new LlmPromptScenario(null, "flight_sms", "航变短信识别",
                        "短信渠道的航变识别、航变字段提取提示词", true, null, null),
                new LlmPromptScenario(null, "after_sale_email", "售后邮件识别",
                        "退票、改期、售后补充等邮件类售后提示词", true, null, null));
    }

    public record DictionaryResult(
            List<DictionaryItem> codeTypes,
            List<DictionaryItem> templateTypes,
            List<DictionaryItem> mailTypes,
            List<LlmPromptScenario> scenarios) {
    }

    public record DictionaryItem(String value, String label) {
    }
}
