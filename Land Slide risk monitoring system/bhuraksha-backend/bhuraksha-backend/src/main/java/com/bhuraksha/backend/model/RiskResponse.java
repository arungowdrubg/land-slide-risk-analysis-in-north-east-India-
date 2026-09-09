package com.bhuraksha.backend.model;

/**
 * Response shape returned by GET /api/risk?place=...
 * Field names intentionally match what the frontend (bhuraksha-frontend.html)
 * currently computes client-side with placeholder logic, so swapping the
 * frontend from mock data to this real endpoint is a drop-in change later.
 */
public class RiskResponse {

    private String place;
    private String district;
    private String season;
    private String riskLevel;      // "LOW" | "MODERATE" | "HIGH"
    private int riskScore;         // 0-100, higher = more risk
    private double recentRainfallMm;
    private String slopeRange;
    private String advisory;
    private String emailAlertStatus;

    public RiskResponse() {}

    public RiskResponse(String place, String district, String season, String riskLevel,
                         int riskScore, double recentRainfallMm, String slopeRange, String advisory) {
        this.place = place;
        this.district = district;
        this.season = season;
        this.riskLevel = riskLevel;
        this.riskScore = riskScore;
        this.recentRainfallMm = recentRainfallMm;
        this.slopeRange = slopeRange;
        this.advisory = advisory;
    }

    public String getPlace() { return place; }
    public void setPlace(String place) { this.place = place; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }

    public double getRecentRainfallMm() { return recentRainfallMm; }
    public void setRecentRainfallMm(double recentRainfallMm) { this.recentRainfallMm = recentRainfallMm; }

    public String getSlopeRange() { return slopeRange; }
    public void setSlopeRange(String slopeRange) { this.slopeRange = slopeRange; }

    public String getAdvisory() { return advisory; }
    public void setAdvisory(String advisory) { this.advisory = advisory; }

    /**
     * Optional delivery state for the configured moderate/high-risk email alert.
     * This reports delivery to the SMTP server, not whether a person has read it.
     */
    public String getEmailAlertStatus() { return emailAlertStatus; }
    public void setEmailAlertStatus(String emailAlertStatus) { this.emailAlertStatus = emailAlertStatus; }
}
