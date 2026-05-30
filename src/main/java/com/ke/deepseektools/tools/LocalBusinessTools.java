package com.ke.deepseektools.tools;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.ke.deepseektools.service.OpenMeteoWeatherService;
import com.ke.deepseektools.service.OpenMeteoWeatherService.WeatherReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class LocalBusinessTools {

    private static final Logger log = LoggerFactory.getLogger(LocalBusinessTools.class);

    private final OpenMeteoWeatherService weatherService;

    public LocalBusinessTools(OpenMeteoWeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @Tool(description = "调用 Open-Meteo 真实天气接口，查询指定城市明天的天气和出行建议。适合天气、温度、穿衣、出差、旅行建议类问题。")
    public WeatherReport getCityWeather(
            @ToolParam(description = "城市名称，例如北京、上海、深圳") String city) {

        log.info("Tool called: getCityWeather(city={})", city);
        return weatherService.queryTomorrowWeather(city);
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

    public record OrderQuote(
            BigDecimal unitPrice,
            int quantity,
            BigDecimal discountRate,
            BigDecimal subtotal,
            BigDecimal saved,
            BigDecimal total) {
    }
}
