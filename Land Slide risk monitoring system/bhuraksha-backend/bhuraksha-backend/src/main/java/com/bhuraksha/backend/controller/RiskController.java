package com.bhuraksha.backend.controller;

import com.bhuraksha.backend.model.RiskResponse;
import com.bhuraksha.backend.service.MlRiskClient;
import com.bhuraksha.backend.service.RiskEmailAlertService;
import com.bhuraksha.backend.service.RiskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

/**
 * GET /api/risk?place=Gangtok
 *
 * Returns placeholder risk data today (see RiskService). Once the trained
 * model + dataset are ready, only RiskService needs to change - this
 * controller and the frontend contract stay the same.
 *
 * CORS is open here for local development. Restrict allowed origins before
 * deploying (e.g. your actual frontend domain) instead of "*".
 */
@RestController
@RequestMapping("/api/risk")
@CrossOrigin(origins = "*")
public class RiskController {

    private final RiskService riskService;
    private final RiskEmailAlertService riskEmailAlertService;

    public RiskController(RiskService riskService, RiskEmailAlertService riskEmailAlertService) {
        this.riskService = riskService;
        this.riskEmailAlertService = riskEmailAlertService;
    }

    @GetMapping
    public ResponseEntity<?> getRisk(
            @RequestParam String place,
            @RequestParam(defaultValue = "false") boolean sendAlert) {
        try {
            RiskResponse response = riskService.getRisk(place);
            response.setEmailAlertStatus(sendAlert
                    ? riskEmailAlertService.sendAlertIfNeeded(response)
                    : RiskEmailAlertService.NOT_REQUESTED);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map_of("error", "Unknown place: " + place));
        } catch (MlRiskClient.MlServiceUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map_of("error", ex.getMessage()));
        }
    }

    // Small helper so we don't need an extra import for a one-off map.
    private static java.util.Map<String, String> Map_of(String k, String v) {
        return java.util.Collections.singletonMap(k, v);
    }
}
