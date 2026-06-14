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
                new DictionaryItem("4", "短信"));
    }

    public static List<DictionaryItem> templateTypes() {
        return List.of(
                new DictionaryItem("1", "航变解析模板"),
                new DictionaryItem("2", "字段提取模板"));
    }

    public static List<DictionaryItem> mailTypes() {
        return List.of(
                new DictionaryItem("0", "类型识别"),
                new DictionaryItem("1", "航变字段提取"),
                new DictionaryItem("2", "退票字段提取"),
                new DictionaryItem("3", "改期字段提取"));
    }

    public static List<LlmPromptScenario> scenarios() {
        return List.of(
                new LlmPromptScenario(null, "email_type_detect", "邮箱类型识别", "2", "邮箱", "1", "航变解析模板", 0,
                        "类型识别", "邮件第一段：识别邮件类型", true, null, null),
                new LlmPromptScenario(null, "email_flight_change_extract", "邮箱航变字段提取", "2", "邮箱", "2", "字段提取模板", 1,
                        "航变字段提取", "邮件第二段：提取航变字段", true, null, null),
                new LlmPromptScenario(null, "email_refund_extract", "邮箱退票字段提取", "2", "邮箱", "2", "字段提取模板", 2,
                        "退票字段提取", "邮件第二段：提取退票字段", true, null, null),
                new LlmPromptScenario(null, "email_change_extract", "邮箱改期字段提取", "2", "邮箱", "2", "字段提取模板", 3,
                        "改期字段提取", "邮件第二段：提取改期字段", true, null, null),
                new LlmPromptScenario(null, "sms_type_detect", "短信类型识别", "4", "短信", "1", "航变解析模板", 0,
                        "类型识别", "短信第一段：识别短信类型", true, null, null),
                new LlmPromptScenario(null, "sms_flight_change_extract", "短信航变字段提取", "4", "短信", "2", "字段提取模板", 1,
                        "航变字段提取", "短信第二段：提取航变字段", true, null, null),
                new LlmPromptScenario(null, "supplier_type_detect", "供应商类型识别", "1", "供应商", "1", "航变解析模板", 0,
                        "类型识别", "供应商维度识别", true, null, null));
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
