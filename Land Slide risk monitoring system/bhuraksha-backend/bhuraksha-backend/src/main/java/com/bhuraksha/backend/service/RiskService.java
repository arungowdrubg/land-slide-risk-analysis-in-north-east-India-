package com.bhuraksha.backend.service;

import com.bhuraksha.backend.model.RiskResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Retrieves a model-backed district risk summary while preserving the frontend
 * response contract. The Python service owns feature preprocessing and scoring.
 */
@Service
public class RiskService {

    private record PlaceInfo(String displayDistrict, String modelDistrict) {}

    private final MlRiskClient mlRiskClient;

    public RiskService(MlRiskClient mlRiskClient) {
        this.mlRiskClient = mlRiskClient;
    }

    private static final Map<String, PlaceInfo> SIKKIM_PLACES = Map.ofEntries(
            Map.entry("Gangtok", new PlaceInfo("East Sikkim", "Gangtok")),
            Map.entry("Melli", new PlaceInfo("South Sikkim", "Namchi")),
            Map.entry("Namchi", new PlaceInfo("South Sikkim", "Namchi")),
            Map.entry("Jorethang", new PlaceInfo("South Sikkim", "Namchi")),
            Map.entry("Ravangla", new PlaceInfo("South Sikkim", "Namchi")),
            Map.entry("Gyalshing", new PlaceInfo("West Sikkim", "Gyalshing")),
            Map.entry("Soreng", new PlaceInfo("West Sikkim", "Soreng")),
            Map.entry("Mangan", new PlaceInfo("North Sikkim", "Mangan")),
            Map.entry("Pakyong", new PlaceInfo("East Sikkim", "Pakyong")),
            Map.entry("Rangpo", new PlaceInfo("East Sikkim", "Pakyong"))
    );

    public RiskResponse getRisk(String place) {
        PlaceInfo info = SIKKIM_PLACES.get(place);
        if (info == null) {
            throw new NoSuchElementException("Unknown place: " + place);
        }

        LocalDate today = LocalDate.now();
        MlRiskClient.MlPrediction prediction = mlRiskClient.getDistrictSummary(
                info.modelDistrict(), today.getMonthValue());

        return new RiskResponse(
                place,
                info.displayDistrict(),
                currentSeason(),
                prediction.riskLevel(),
                prediction.riskScore(),
                prediction.rainfallMm(),
                prediction.slopeRange(),
                advisoryFor(prediction.riskLevel())
        );
    }

    private String advisoryFor(String level) {
        return switch (level) {
            case "LOW" -> "Conditions currently appear stable. Continue routine monitoring during monsoon months.";
            case "HIGH" -> "Multiple risk factors are elevated for this area. Exercise caution and follow local authority guidance.";
            default -> "Some risk factors are elevated. Avoid unnecessary travel near slopes after heavy rain.";
        };
    }

    private String currentSeason() {
        int month = LocalDate.now().getMonthValue();
        if (month >= 6 && month <= 9) return "Monsoon";
        if (month >= 3 && month <= 5) return "Pre-monsoon";
        if (month >= 10 && month <= 11) return "Post-monsoon";
        return "Winter";
    }
}
