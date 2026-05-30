package com.ke.deepseektools.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OpenMeteoWeatherService {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Map<String, Coordinates> CITY_COORDINATE_FALLBACKS = Map.of(
            "北京", new Coordinates("北京", 39.9042, 116.4074),
            "上海", new Coordinates("上海", 31.2304, 121.4737),
            "深圳", new Coordinates("深圳", 22.5431, 114.0579));

    private final RestClient restClient;

    public OpenMeteoWeatherService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public WeatherReport queryTomorrowWeather(String city) {
        Coordinates coordinates = resolveCoordinates(city);
        ForecastResponse forecast = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.open-meteo.com")
                        .path("/v1/forecast")
                        .queryParam("latitude", coordinates.latitude())
                        .queryParam("longitude", coordinates.longitude())
                        .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                        .queryParam("timezone", "Asia/Shanghai")
                        .queryParam("forecast_days", 3)
                        .build())
                .retrieve()
                .body(ForecastResponse.class);

        if (forecast == null || forecast.daily() == null || forecast.daily().time() == null) {
            throw new IllegalStateException("Open-Meteo forecast response is empty");
        }

        LocalDate targetDate = LocalDate.now(CHINA_ZONE).plusDays(1);
        int index = forecast.daily().time().indexOf(targetDate.toString());
        if (index < 0) {
            index = Math.min(1, forecast.daily().time().size() - 1);
        }

        Integer weatherCode = valueAt(forecast.daily().weatherCode(), index);
        Double maxTemperature = valueAt(forecast.daily().temperatureMax(), index);
        Double minTemperature = valueAt(forecast.daily().temperatureMin(), index);
        Integer precipitationProbability = valueAt(forecast.daily().precipitationProbabilityMax(), index);

        return new WeatherReport(
                coordinates.name(),
                targetDate,
                describeWeatherCode(weatherCode),
                minTemperature,
                maxTemperature,
                precipitationProbability,
                buildAdvice(weatherCode, minTemperature, maxTemperature, precipitationProbability),
                "Open-Meteo");
    }

    private Coordinates resolveCoordinates(String city) {
        String normalizedCity = normalizeCity(city);
        GeocodingResponse geocoding = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("geocoding-api.open-meteo.com")
                        .path("/v1/search")
                        .queryParam("name", normalizedCity)
                        .queryParam("count", 1)
                        .queryParam("language", "zh")
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .body(GeocodingResponse.class);

        if (geocoding != null && geocoding.results() != null && !geocoding.results().isEmpty()) {
            GeocodingResult result = geocoding.results().get(0);
            return new Coordinates(result.name(), result.latitude(), result.longitude());
        }

        Coordinates fallback = CITY_COORDINATE_FALLBACKS.get(normalizedCity);
        if (fallback != null) {
            return fallback;
        }

        throw new IllegalArgumentException("未找到城市经纬度: " + city);
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
        return value.isBlank() ? "北京" : value;
    }

    private String describeWeatherCode(Integer code) {
        if (code == null) {
            return "未知";
        }
        return switch (code) {
            case 0 -> "晴";
            case 1, 2 -> "少云";
            case 3 -> "阴";
            case 45, 48 -> "雾";
            case 51, 53, 55 -> "毛毛雨";
            case 56, 57 -> "冻毛毛雨";
            case 61, 63, 65 -> "雨";
            case 66, 67 -> "冻雨";
            case 71, 73, 75 -> "雪";
            case 77 -> "雪粒";
            case 80, 81, 82 -> "阵雨";
            case 85, 86 -> "阵雪";
            case 95 -> "雷暴";
            case 96, 99 -> "雷暴伴冰雹";
            default -> "未知天气码 " + code;
        };
    }

    private String buildAdvice(Integer weatherCode, Double minTemperature, Double maxTemperature, Integer precipitationProbability) {
        if (weatherCode != null && weatherCode >= 61 && weatherCode <= 82) {
            return "有降水可能，建议带伞并预留通勤时间。";
        }
        if (precipitationProbability != null && precipitationProbability >= 50) {
            return "降水概率较高，建议带伞。";
        }
        if (maxTemperature != null && maxTemperature >= 30) {
            return "气温较高，注意防晒和补水。";
        }
        if (minTemperature != null && minTemperature <= 10) {
            return "早晚偏冷，建议加一件外套。";
        }
        return "天气整体适合出行，按日常通勤准备即可。";
    }

    private <T> T valueAt(List<T> values, int index) {
        if (values == null || values.isEmpty() || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    public record WeatherReport(
            String city,
            LocalDate date,
            String weather,
            Double minTemperatureCelsius,
            Double maxTemperatureCelsius,
            Integer precipitationProbability,
            String advice,
            String source) {
    }

    private record Coordinates(String name, double latitude, double longitude) {
    }

    private record GeocodingResponse(List<GeocodingResult> results) {
    }

    private record GeocodingResult(String name, double latitude, double longitude) {
    }

    private record ForecastResponse(DailyForecast daily) {
    }

    private record DailyForecast(
            List<String> time,
            @com.fasterxml.jackson.annotation.JsonProperty("weather_code") List<Integer> weatherCode,
            @com.fasterxml.jackson.annotation.JsonProperty("temperature_2m_max") List<Double> temperatureMax,
            @com.fasterxml.jackson.annotation.JsonProperty("temperature_2m_min") List<Double> temperatureMin,
            @com.fasterxml.jackson.annotation.JsonProperty("precipitation_probability_max") List<Integer> precipitationProbabilityMax) {
    }
}
