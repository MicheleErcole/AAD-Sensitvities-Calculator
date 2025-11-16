package it.tesi;

import net.finmath.exception.CalculationException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.TreeSet;

public class Main {
    public static void main(String[] args) {
    	
    	//CSV bucket reading
    	InputStream bucketStream = Main.class.getClassLoader().getResourceAsStream("underlying_bucket_mapping.csv");
        if (bucketStream == null) {
            System.err.println("Errore: Il file di mappatura dei bucket non è stato trovato.");
            return;
        }
        CsvParser.parseUnderlyingBucketCsv(bucketStream);
        
        
    	//CSV trades reading
        InputStream inputStream = Main.class.getClassLoader().getResourceAsStream("sample_trades.csv");

        if (inputStream == null) {
            System.err.println("Errore: Il file CSV non è stato trovato nella cartella resources.");
            return;
        }

        List<Trade> trades = CsvParser.parseCsvTrade(inputStream);

        if (trades.isEmpty()) {
            System.out.println("Nessun trade valido trovato nel file CSV.");
            return;
        }
        

        System.out.println("=== TEST DEL PARSING DEL CSV ===");
        trades.forEach(System.out::println);

        int numberOfPaths = 100000;
        int numberOfTimeSteps = 100;
        double deltaT = 0.1;
        int seed = 1234;

        AADPricer pricer = new AADPricer(numberOfPaths, numberOfTimeSteps, deltaT, seed);

        try {
            System.out.println("\n=== CALCOLO DELLE SENSITIVITIES ===");
            List<Trade> updatedTrades = pricer.priceAndCalculateGreeks(trades);

            Map<String, Map<Integer, Double>> netSensitivitiesDelta =
                    SensitivityAggregator.calculateNetSensitivitiesDelta(updatedTrades);
            Map<String, Map<Integer, Double>> netSensitivitiesVega =
                    SensitivityAggregator.calculateNetSensitivitiesVega(updatedTrades);
            
            System.out.println("\n=== NET SENSITIVITIES DELTA ===");
            netSensitivitiesDelta.forEach((riskFactorDelta, bucketMap) ->
                            bucketMap.forEach((bucket, value) ->
                                    System.out.printf("RiskFactor: %s | Bucket: %d | Net Delta: %.6f\n",
                                            riskFactorDelta, bucket, value)
                            ));

            System.out.println("\n=== NET SENSITIVITIES VEGA ===");
            netSensitivitiesVega.forEach((riskFactorVega, bucketMap) ->
                            bucketMap.forEach((bucket, value) ->
                                    System.out.printf("RiskFactor: %s | Bucket: %d | Net Vega: %.6f\n",
                                            riskFactorVega, bucket, value)
                            ));
            
            Map<String, Map<Integer, Double>> weightedSensitivitiesDelta =
                    SensitivityAggregator.calculateWeightedSensitivitiesDelta(netSensitivitiesDelta);
            Map<String, Map<Integer, Double>> weightedSensitivitiesVega =
                    SensitivityAggregator.calculateWeightedSensitivitiesVega(netSensitivitiesVega);
            
            System.out.println("\n=== WEIGHTED SENSITIVITIES DELTA ===");
            weightedSensitivitiesDelta.forEach((riskFactorDelta, bucketMap) ->
                            bucketMap.forEach((bucket, value) ->
                                    System.out.printf("RiskFactor: %s | Bucket: %d | Weighted Delta: %.6f\n",
                                            riskFactorDelta, bucket, value)
                            ));

            System.out.println("\n=== WEIGHTED SENSITIVITIES VEGA ===");
            weightedSensitivitiesVega.forEach((riskFactorVega, bucketMap) ->
                            bucketMap.forEach((bucket, value) ->
                                    System.out.printf("RiskFactor: %s | Bucket: %d | Weighted Vega: %.6f\n",
                                    		riskFactorVega, bucket, value)
                            ));
            
            System.out.println("\n=== CURVATURE RISK CALCULATION ===");
            CurvatureRiskCalculator curvatureRiskCalculator = new CurvatureRiskCalculator(numberOfPaths, numberOfTimeSteps, deltaT, seed);
            Map<String, Map<Integer, Double[]>> curvatureRisk = curvatureRiskCalculator.calculateCurvatureRisk(updatedTrades);
            curvatureRisk.forEach((riskFactorDelta, bucketMap) ->
            bucketMap.forEach((bucket, values) ->
                    System.out.printf("RiskFactor: %s |Bucket: %d | CVR +: %.6f | CVR -: %.6f\n",
                    		riskFactorDelta, bucket, values[0], values[1])
            ));
            
            List<Trade> finalUpdatedTrades = new ArrayList<>();
            for (Trade trade : updatedTrades) {
                Double[] curvatureValues = curvatureRisk
                        .getOrDefault(trade.getRiskFactorDelta(), new HashMap<>())
                        .getOrDefault(trade.getBucket(), new Double[]{0.0, 0.0});

                Trade updatedTrade = new Trade(
                        trade.getPortfolio(), trade.getDealNumber(), trade.getAssetType(), trade.getOptionStyle(),
                        trade.getRiskFactorDelta(), trade.getRiskFactorVega(),trade.getUnderlying(), trade.getBucket(), 
                        trade.getOptionType(), trade.getCurrency(), trade.getAmount(), trade.getVolatility(),
                        trade.getStrikes(), trade.getUnderlyingPrice(), trade.getMaturity(), trade.getExerciseDates(),
                        trade.getRiskFreeRate(), trade.getValue(), trade.getDelta(), trade.getVega(),
                        curvatureValues[0], curvatureValues[1]
                );

                finalUpdatedTrades.add(updatedTrade);
            }

            System.out.println("\n=== UPDATED TRADES WITH DELTA, VEGA, CVR^+ e CVR^-===");
            finalUpdatedTrades.forEach(System.out::println);
            
            System.out.println("\n=== RECAP TRADE: features and risk factors ===");
            System.out.printf("%-12s | %-15s | %-6s | %-17s | %-17s\n", 
                              "DealNumber", "Underlying", "Bucket", "RiskFactorDelta", "RiskFactorVega");
            System.out.println("-------------------------------------------------------------------------");

            for (Trade trade : finalUpdatedTrades) {
                System.out.printf("%-12d | %-15s | %-6d | %-17s | %-17s\n",
                                  trade.getDealNumber(),
                                  trade.getUnderlying(),
                                  trade.getBucket(),
                                  trade.getRiskFactorDelta(),
                                  trade.getRiskFactorVega());
            }
            
            //Intra-bucket Aggregation for Delta, Vega and Curvature Risk among three scenarios
            Map<Integer, Double> capitalByBucketDeltaMedium = SensitivityAggregator.aggregateIntraBucketDelta(weightedSensitivitiesDelta, "medium");
            Map<Integer, Double> capitalByBucketDeltaHigh   = SensitivityAggregator.aggregateIntraBucketDelta(weightedSensitivitiesDelta, "high");
            Map<Integer, Double> capitalByBucketDeltaLow    = SensitivityAggregator.aggregateIntraBucketDelta(weightedSensitivitiesDelta, "low");

            Map<String, Map<Integer, Double>> optionMaturities = SensitivityAggregator.extractOptionMaturities(trades);
            Map<Integer, Double> capitalByBucketVegaMedium  = SensitivityAggregator.aggregateIntraBucketVega(weightedSensitivitiesVega, optionMaturities, "medium");
            Map<Integer, Double> capitalByBucketVegaHigh    = SensitivityAggregator.aggregateIntraBucketVega(weightedSensitivitiesVega, optionMaturities, "high");
            Map<Integer, Double> capitalByBucketVegaLow     = SensitivityAggregator.aggregateIntraBucketVega(weightedSensitivitiesVega, optionMaturities, "low");

            Map<Integer, String> selectedScenarioByBucket = new HashMap<>();
            Map<Integer, Double> capitalByBucketCurvatureMedium = SensitivityAggregator.aggregateCurvatureRiskIntraBucket(curvatureRisk, selectedScenarioByBucket, "medium");
            Map<Integer, Double> capitalByBucketCurvatureHigh   = SensitivityAggregator.aggregateCurvatureRiskIntraBucket(curvatureRisk, selectedScenarioByBucket, "high");
            Map<Integer, Double> capitalByBucketCurvatureLow    = SensitivityAggregator.aggregateCurvatureRiskIntraBucket(curvatureRisk, selectedScenarioByBucket, "low");

            
            System.out.println("\n=== INTRA-BUCKET AGGREGATION ===");

            //Delta
            Set<Integer> allBucketsDelta = new TreeSet<>();
            allBucketsDelta.addAll(capitalByBucketDeltaMedium.keySet());
            allBucketsDelta.addAll(capitalByBucketDeltaHigh.keySet());
            allBucketsDelta.addAll(capitalByBucketDeltaLow.keySet());

            System.out.println("\n--- Delta ---");
            for (Integer bucket : allBucketsDelta) {
                System.out.printf("Bucket: %d | Delta (M: %.6f, H: %.6f, L: %.6f)\n",
                    bucket,
                    capitalByBucketDeltaMedium.getOrDefault(bucket, 0.0),
                    capitalByBucketDeltaHigh.getOrDefault(bucket, 0.0),
                    capitalByBucketDeltaLow.getOrDefault(bucket, 0.0)
                );
            }

            //Vega
            Set<Integer> allBucketsVega = new TreeSet<>();
            allBucketsVega.addAll(capitalByBucketVegaMedium.keySet());
            allBucketsVega.addAll(capitalByBucketVegaHigh.keySet());
            allBucketsVega.addAll(capitalByBucketVegaLow.keySet());

            System.out.println("\n--- Vega ---");
            for (Integer bucket : allBucketsVega) {
                System.out.printf("Bucket: %d | Vega (M: %.6f, H: %.6f, L: %.6f)\n",
                    bucket,
                    capitalByBucketVegaMedium.getOrDefault(bucket, 0.0),
                    capitalByBucketVegaHigh.getOrDefault(bucket, 0.0),
                    capitalByBucketVegaLow.getOrDefault(bucket, 0.0)
                );
            }

            //Curvature
            Set<Integer> allBucketsCurvature = new TreeSet<>();
            allBucketsCurvature.addAll(capitalByBucketCurvatureMedium.keySet());
            allBucketsCurvature.addAll(capitalByBucketCurvatureHigh.keySet());
            allBucketsCurvature.addAll(capitalByBucketCurvatureLow.keySet());

            System.out.println("\n--- Curvature ---");
            for (Integer bucket : allBucketsCurvature) {
                System.out.printf("Bucket: %d | Curvature (M: %.6f, H: %.6f, L: %.6f)\n",
                    bucket,
                    capitalByBucketCurvatureMedium.getOrDefault(bucket, 0.0),
                    capitalByBucketCurvatureHigh.getOrDefault(bucket, 0.0),
                    capitalByBucketCurvatureLow.getOrDefault(bucket, 0.0)
                );
            }

            //For Delta, Vega, Curvature find the max intra-bucket
            Map<Integer, Double> capitalByBucketDeltaFinal = new HashMap<>();
            Map<Integer, Double> capitalByBucketVegaFinal = new HashMap<>();
            Map<Integer, Double> capitalByBucketCurvatureFinal = new HashMap<>();
            
            for (Integer bucket : capitalByBucketDeltaMedium.keySet()) {
                double maxDelta = Math.max(Math.max(
                    capitalByBucketDeltaMedium.getOrDefault(bucket, 0.0),
                    capitalByBucketDeltaHigh.getOrDefault(bucket, 0.0)),
                    capitalByBucketDeltaLow.getOrDefault(bucket, 0.0)
                );
                capitalByBucketDeltaFinal.put(bucket, maxDelta);
            }

            for (Integer bucket : capitalByBucketVegaMedium.keySet()) {
                double maxVega = Math.max(Math.max(
                    capitalByBucketVegaMedium.getOrDefault(bucket, 0.0),
                    capitalByBucketVegaHigh.getOrDefault(bucket, 0.0)),
                    capitalByBucketVegaLow.getOrDefault(bucket, 0.0)
                );
                capitalByBucketVegaFinal.put(bucket, maxVega);
            }

            for (Integer bucket : capitalByBucketCurvatureMedium.keySet()) {
                double maxCurvature = Math.max(Math.max(
                    capitalByBucketCurvatureMedium.getOrDefault(bucket, 0.0),
                    capitalByBucketCurvatureHigh.getOrDefault(bucket, 0.0)),
                    capitalByBucketCurvatureLow.getOrDefault(bucket, 0.0)
                );
                capitalByBucketCurvatureFinal.put(bucket, maxCurvature);
            }

            //Inter-bucket Aggregation for Delta, Vega and Curvature Risk across the three scenarios
            double K_deltaMedium = SensitivityAggregator.aggregateInterBucketDelta(capitalByBucketDeltaFinal, weightedSensitivitiesDelta, "medium");
            double K_deltaHigh = SensitivityAggregator.aggregateInterBucketDelta(capitalByBucketDeltaFinal, weightedSensitivitiesDelta, "high");
            double K_deltaLow = SensitivityAggregator.aggregateInterBucketDelta(capitalByBucketDeltaFinal, weightedSensitivitiesDelta, "low");

            double K_vegaMedium = SensitivityAggregator.aggregateInterBucketVega(capitalByBucketVegaFinal, weightedSensitivitiesVega, "medium");
            double K_vegaHigh = SensitivityAggregator.aggregateInterBucketVega(capitalByBucketVegaFinal, weightedSensitivitiesVega, "high");
            double K_vegaLow = SensitivityAggregator.aggregateInterBucketVega(capitalByBucketVegaFinal, weightedSensitivitiesVega, "low");

            double K_curvatureMedium = SensitivityAggregator.aggregateCurvatureRiskInterBucket(capitalByBucketCurvatureFinal, selectedScenarioByBucket, curvatureRisk, "medium");
            double K_curvatureHigh = SensitivityAggregator.aggregateCurvatureRiskInterBucket(capitalByBucketCurvatureFinal, selectedScenarioByBucket, curvatureRisk, "high");
            double K_curvatureLow = SensitivityAggregator.aggregateCurvatureRiskInterBucket(capitalByBucketCurvatureFinal, selectedScenarioByBucket, curvatureRisk, "low");

            
            //Choose of final capital requirement
            double K_finalDelta = Math.max(Math.max(K_deltaMedium, K_deltaHigh), K_deltaLow);
            double K_finalVega = Math.max(Math.max(K_vegaMedium, K_vegaHigh), K_vegaLow);
            double K_finalCurvature = Math.max(Math.max(K_curvatureMedium, K_curvatureHigh), K_curvatureLow);

            System.out.println("\n=== INTER-BUCKET AGGREGATION ===");
            System.out.println("\n--- Delta ---");
            System.out.printf("(M: %.6f, H: %.6f, L: %.6f)\n", K_deltaMedium, K_deltaHigh, K_deltaLow);
            System.out.println("\n--- Vega ---");
            System.out.printf("(M: %.6f, H: %.6f, L: %.6f)\n", K_vegaMedium, K_vegaHigh, K_vegaLow);
            System.out.println("\n--- Curvature ---");
            System.out.printf("(M: %.6f, H: %.6f, L: %.6f)\n", K_curvatureMedium, K_curvatureHigh, K_curvatureLow);


            System.out.println("\n=== FINAL RESULTS ===");  
            System.out.printf("Final Capital Requirement Delta: %.6f\n", K_finalDelta);
            System.out.printf("Final Capital Requirement Vega: %.6f\n", K_finalVega);
            System.out.printf("Final Capital Requirement Curvature: %.6f\n", K_finalCurvature);
            
        } catch (CalculationException e) {
            System.err.println("Errore durante il calcolo delle sensitivities: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=== FINE DEL PROGRAMMA ===");
    }
}