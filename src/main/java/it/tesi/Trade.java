package it.tesi;

import java.util.Arrays;
import java.util.Locale;

public class Trade {
    private String portfolio;
    private int dealNumber;
    private String assetType;
    private String optionStyle;
    private String riskFactorDelta;
    private String riskFactorVega;
    private String underlying;
    private int bucket;
    private double optionType;
    private String currency;
    private double amount;
    private double volatility;
    private double[] strikes;
    private double underlyingPrice;
    private double maturity;
    private double[] exerciseDates;
    private double riskFreeRate;
    private double value;
    private double delta;
    private double vega;
    private double curvatureRiskPlus;
    private double curvatureRiskMinus; 


    public Trade(String portfolio, int dealNumber, String assetType, String optionStyle,
                 String riskFactorDelta, String riskFactorVega, String underlying, int bucket, double optionType, String currency,
                 double amount, double volatility, double[] strikes,
                 double underlyingPrice, double maturity, double[] exerciseDates, double riskFreeRate,
                 double value, double delta, double vega, double curvatureRiskPlus, double curvatureRiskMinus) { 
        this.portfolio = portfolio;
        this.dealNumber = dealNumber;
        this.assetType = assetType;
        this.optionStyle = optionStyle;
        this.riskFactorDelta = riskFactorDelta;
        this.riskFactorVega = riskFactorVega;
        this.underlying = underlying;
        this.bucket = bucket;
        this.optionType = optionType;
        this.currency = currency;
        this.amount = amount;
        this.volatility = volatility;
        this.strikes = strikes;
        this.underlyingPrice = underlyingPrice;
        this.maturity = maturity;
        this.exerciseDates = exerciseDates;
        this.riskFreeRate = riskFreeRate;
        this.value = value;
        this.delta = delta;
        this.vega = vega;
        this.curvatureRiskPlus = curvatureRiskPlus;
        this.curvatureRiskMinus = curvatureRiskMinus;
    }
    
    public String getPortfolio() {
        return portfolio;
    }
    
    public int getDealNumber() {
        return dealNumber;
    }
    
    public String getAssetType() {
        return assetType;
    }

    public String getOptionStyle() {
        return optionStyle;
    }

    public String getRiskFactorDelta() {
        return riskFactorDelta;
    }
    
    public String getRiskFactorVega() {
        return riskFactorVega;
    }
    
    public String getUnderlying() {
        return underlying;
    }

    public int getBucket() {
        return bucket;
    }

    public double getOptionType() {
        return optionType;
    }
    
    public String getCurrency() {
        return currency;
    }

    public double getAmount() {
        return amount;
    }
    
    public double getVolatility() {
        return volatility;
    }

    public double[] getStrikes() {
        return strikes;
    }
    
    public double getUnderlyingPrice() {
        return underlyingPrice;
    }

    public double getMaturity() {
        return maturity;
    }

    public double[] getExerciseDates() {
        return exerciseDates;
    }

    public double getRiskFreeRate() {
        return riskFreeRate;
    }

    public double getValue() {
    	return value;
    }

    public double getDelta() {
        return delta;
    }

    public double getVega() {
        return vega;
    }
    
	public double getCurvatureRiskPlus() {
        return curvatureRiskPlus;
    }

    public double getCurvatureRiskMinus() {
        return curvatureRiskMinus;
    }

    public void setCurvatureRisk(double plus, double minus) {
        this.curvatureRiskPlus = plus;
        this.curvatureRiskMinus = minus;
    }



    @Override
    public String toString() {
        return String.format(Locale.US, 
                "Trade [Portfolio=%s, DealNumber=%d, AssetType=%s, OptionStyle=%s, RiskFactorDelta=%s, " +
                "RiskFactorVega=%s, Underlying=%s, Bucket=%d, OptionType=%.2f, Currency=%s, Amount=%.2f, " +
                "Volatility=%.2f, Strikes=%s, UnderlyingPrice=%.2f, Maturity=%.6f, " +
                "ExerciseDates=%s, RiskFreeRate=%.3f, Value =%.6f, Delta=%.6f, Vega=%.6f, " +
                "CurvatureRiskPlus=%.6f, CurvatureRiskMinus=%.6f]",
                portfolio, dealNumber, assetType, optionStyle, riskFactorDelta, riskFactorVega, underlying, bucket,
                optionType, currency, amount, volatility, Arrays.toString(strikes), underlyingPrice, maturity,
                Arrays.toString(exerciseDates), riskFreeRate, value, delta, vega, curvatureRiskPlus, curvatureRiskMinus);
    }
}
