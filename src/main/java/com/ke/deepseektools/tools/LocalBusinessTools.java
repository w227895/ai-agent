package com.ke.deepseektools.tools;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class LocalBusinessTools {

    private static final Logger log = LoggerFactory.getLogger(LocalBusinessTools.class);

    @Tool(description = "查询指定中国城市的演示天气和出行建议。适合天气、温度、穿衣、出差、旅行建议类问题。")
    public WeatherInfo getCityWeather(
            @ToolParam(description = "城市名称，例如北京、上海、深圳") String city) {

        log.info("Tool called: getCityWeather(city={})", city);

        String normalizedCity = normalizeCity(city);
        return switch (normalizedCity) {
            case "北京" -> new WeatherInfo(normalizedCity, LocalDate.now().plusDays(1), "多云", 23, "早晚偏凉，建议带一件薄外套。");
            case "上海" -> new WeatherInfo(normalizedCity, LocalDate.now().plusDays(1), "小雨", 25, "建议带伞，鞋子选防滑一点的。");
            case "深圳" -> new WeatherInfo(normalizedCity, LocalDate.now().plusDays(1), "晴", 29, "紫外线较强，注意防晒和补水。");
            default -> new WeatherInfo(normalizedCity, LocalDate.now().plusDays(1), "晴转多云", 26, "这是演示数据，实际项目可替换为真实天气 API。");
        };
    }

    @Tool(description = "计算订单总价。适合商品单价、数量、折扣、应付金额、订单金额类问题。")
    public OrderQuote calculateOrderPrice(
            @ToolParam(description = "商品单价，单位元，例如 199") BigDecimal unitPrice,
            @ToolParam(description = "购买数量，例如 3") int quantity,
            @ToolParam(description = "折扣率，8 折传 0.8；不打折传 1") BigDecimal discountRate) {

        log.info("Tool called: calculateOrderPrice(unitPrice={}, quantity={}, discountRate={})",
                unitPrice, quantity, discountRate);

        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal total = subtotal.multiply(discountRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal saved = subtotal.subtract(total).setScale(2, RoundingMode.HALF_UP);

        return new OrderQuote(unitPrice, quantity, discountRate, subtotal, saved, total);
    }

    private String normalizeCity(String city) {
        String value = city == null ? "" : city.trim();
        String lower = value.toLowerCase(Locale.ROOT);

        if (lower.contains("beijing") || value.contains("北京")) {
            return "北京";
        }
        if (lower.contains("shanghai") || value.contains("上海")) {
            return "上海";
        }
        if (lower.contains("shenzhen") || value.contains("深圳")) {
            return "深圳";
        }
        return value.isBlank() ? "未知城市" : value;
    }

    public record WeatherInfo(String city, LocalDate date, String weather, int temperatureCelsius, String advice) {
    }

    public record OrderQuote(
            BigDecimal unitPrice,
            int quantity,
            BigDecimal discountRate,
            BigDecimal subtotal,
            BigDecimal saved,
            BigDecimal total) {
    }
}
