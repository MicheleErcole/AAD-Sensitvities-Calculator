package it.tesi;

import org.apache.commons.csv.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CsvParser {
	
	//Bucket Map creation
	private static final Map<String, Integer> underlyingBucketMap = new HashMap<>();

	//CSV reading for bucket selection
	public static void parseUnderlyingBucketCsv(InputStream inputStream) {
	    try (Reader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
	         CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
	                 .builder()
	                 .setHeader()
	                 .setIgnoreHeaderCase(true)
	                 .setTrim(true)
	                 .build())) {

	        for (CSVRecord record : csvParser) {
	            String underlying = record.get("Underlying").trim();
	            int bucket = safeParseInt(record.get("Bucket"));
	            underlyingBucketMap.put(underlying, bucket);
	        }

	    } catch (IOException e) {
	        System.err.println("Errore nella lettura del file CSV dei buckets: " + e.getMessage());
	    }
	}

	//CSV trades reading and creation of an instance of Trade object
	public static List<Trade> parseCsvTrade(InputStream inputStream) {
	    List<Trade> trades = new ArrayList<>();

	    try (Reader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
	         CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder()
	                 .setHeader()
	                 .setIgnoreHeaderCase(true)
	                 .setTrim(true)
	                 .build())) {

	        for (CSVRecord record : csvParser) {

	            String underlying = record.get("Underlying").trim();
	            int bucket = underlyingBucketMap.getOrDefault(underlying, 0); // Se non trovato, assegna 0
                double maturity = safeParseDouble(record.get("Maturity"));

                //RiskFactorDelta e RiskFactorVega assignment
                String riskFactorDelta = "Spot-" + underlying;
                String riskFactorVega = null;

                if (record.get("AssetType").equalsIgnoreCase("Option")) {
                    riskFactorVega = VolatilityInterpolator.getFormattedRiskFactorVega(underlying, maturity);
                }
                
	            Trade trade = new Trade(
	                    record.get("Portfolio"),
	                    safeParseInt(record.get("DealNumber")),
	                    record.get("AssetType"),
	                    record.get("AssetType").equalsIgnoreCase("Stock") ? "Stock" : record.get("OptionStyle"),
	                    riskFactorDelta,
	                    riskFactorVega,
	                    underlying,
	                    bucket,
	                    safeParseDouble(record.get("OptionType")),
	                    record.get("Currency"),
	                    safeParseDouble(record.get("Amount")),
	                    safeParseDouble(record.get("Volatility")),
	                    parseStrikes(record.get("Strikes")),
	                    safeParseDouble(record.get("UnderlyingPrice")),
	                    safeParseDouble(record.get("Maturity")),
	                    parseExerciseDates(record.get("ExerciseDates")),
	                    safeParseDouble(record.get("RiskFreeRate")),
	                    record.isMapped("Value") && !record.get("Value").isEmpty() ? safeParseDouble(record.get("Value")) : 0.0,
	                    record.isMapped("Delta") && !record.get("Delta").isEmpty() ? safeParseDouble(record.get("Delta")) : 0.0,
	                    record.isMapped("Vega") && !record.get("Vega").isEmpty() ? safeParseDouble(record.get("Vega")) : 0.0,
	                    record.isMapped("CurvatureRiskPlus") && !record.get("CurvatureRiskPlus").isEmpty() ? safeParseDouble(record.get("CurvatureRiskPlus")) : 0.0,
	                    record.isMapped("CurvatureRiskMinus") && !record.get("CurvatureRiskMinus").isEmpty() ? safeParseDouble(record.get("CurvatureRiskMinus")) : 0.0
	            		);
	           
	            
	            trades.add(trade);
	        }

	    } catch (IOException e) {
	        System.err.println("Errore nella lettura del file CSV: " + e.getMessage());
	    }

	    return trades;
	}


    // Helper to help with erroneous conversions of Integer
    private static int safeParseInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Helper to help with erroneous conversions of Double
    private static double safeParseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0; 
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // Conversion method for ExerciseDates from String to double[]
    private static double[] parseExerciseDates(String dates) {
        if (dates == null || dates.trim().isEmpty()) {
            return new double[0];
        }
        try {
            return Arrays.stream(dates.split(";"))
                    .mapToDouble(Double::parseDouble)
                    .toArray();
        } catch (NumberFormatException e) {
            return new double[0];
        }
    }

    // Conversion method for Strike from String to double[]
    private static double[] parseStrikes(String strikes) {
        if (strikes == null || strikes.trim().isEmpty()) {
            return new double[0];
        }
        try {
            return Arrays.stream(strikes.split(";"))
                    .mapToDouble(Double::parseDouble)
                    .toArray();
        } catch (NumberFormatException e) {
            return new double[0];
        }
    }

}
