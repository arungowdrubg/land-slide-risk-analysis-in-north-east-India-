package com.bhuraksha.backend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Small, typed boundary between the Spring API and the Python model service. */
@Component
public class MlRiskClient {

    private final RestClient restClient;

    public MlRiskClient(@Value("${ml.service.base-url:http://localhost:8000}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public MlPrediction getDistrictSummary(String district, int month) {
        try {
            MlPrediction prediction = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/predict/summary")
                            .queryParam("district", district)
                            .queryParam("month", month)
                            .build())
                    .retrieve()
                    .body(MlPrediction.class);
            if (prediction == null) {
                throw new MlServiceUnavailableException("ML service returned an empty prediction.");
            }
            return prediction;
        } catch (RestClientException exception) {
            throw new MlServiceUnavailableException(
                    "ML prediction service is unavailable. Start the service on the configured URL.", exception);
        }
    }

    public record MlPrediction(
            @JsonProperty("risk_level") String riskLevel,
            @JsonProperty("risk_score") int riskScore,
            @JsonProperty("rainfall_mm") double rainfallMm,
            @JsonProperty("slope_range") String slopeRange
    ) {}

    public static class MlServiceUnavailableException extends RuntimeException {
        public MlServiceUnavailableException(String message) {
            super(message);
        }

        public MlServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
