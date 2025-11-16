package it.tesi;

import java.util.*;
import java.util.stream.Collectors;

public class SensitivityAggregator {

    // Risk weight map for each bucket (for delta and vega)
	private static final Map<Integer, Double[]> RISK_WEIGHTS = new HashMap<>();

	static {
	    //{Bucket, {Delta weight, Vega weight}}
	    RISK_WEIGHTS.put(1, new Double[]{0.55, 0.7778});
	    RISK_WEIGHTS.put(2, new Double[]{0.60, 0.7778});
	    RISK_WEIGHTS.put(3, new Double[]{0.45, 0.7778});
	    RISK_WEIGHTS.put(4, new Double[]{0.55, 0.7778});
	    RISK_WEIGHTS.put(5, new Double[]{0.30, 0.7778});
	    RISK_WEIGHTS.put(6, new Double[]{0.35, 0.7778});
	    RISK_WEIGHTS.put(7, new Double[]{0.40, 0.7778});
	    RISK_WEIGHTS.put(8, new Double[]{0.50, 0.7778});
	    RISK_WEIGHTS.put(9, new Double[]{0.70, 1.0});
	    RISK_WEIGHTS.put(10, new Double[]{0.50, 1.0});
	    RISK_WEIGHTS.put(11, new Double[]{0.70, 1.0});
	    RISK_WEIGHTS.put(12, new Double[]{0.15, 0.7778});
	    RISK_WEIGHTS.put(13, new Double[]{0.25, 0.7778});
	}

    public static Map<Integer, Double[]> getRiskWeights() {
        return RISK_WEIGHTS;
    }


    //Net sensitivities delta
    public static Map<String, Map<Integer, Double>> calculateNetSensitivitiesDelta(List<Trade> trades) {
        return trades.stream()
                .collect(Collectors.groupingBy(
                        Trade::getRiskFactorDelta,
                                Collectors.groupingBy(
                                        Trade::getBucket,
                                        Collectors.summingDouble(Trade::getDelta)
                                )
                        )
                );
    }

    
    //Net sensitivities vega
    public static Map<String, Map<Integer, Double>> calculateNetSensitivitiesVega(List<Trade> trades) {
        return trades.stream()
        		.filter(trade -> trade.getAssetType() != null && !"STOCK".equalsIgnoreCase(trade.getAssetType()))
                .collect(Collectors.groupingBy(
                        Trade::getRiskFactorVega,
                                Collectors.groupingBy(
                                        Trade::getBucket,
                                        Collectors.summingDouble(Trade::getVega) // Somma i valori di Vega
                                )
                        )
                );
    }


    //Weighted sensitivities delta 
    public static Map<String, Map<Integer, Double>> calculateWeightedSensitivitiesDelta(
            Map<String, Map<Integer, Double>> netSensitivitiesDelta) {

        return netSensitivitiesDelta.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, // RiskFactorDelta
                        riskEntry -> riskEntry.getValue().entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey, // Bucket
                                        bucketEntry -> {
                                            double riskWeightDelta = RISK_WEIGHTS.getOrDefault(bucketEntry.getKey(), new Double[]{1.0, 1.0})[0];
                                            return bucketEntry.getValue() * riskWeightDelta;
                                        }
                                ))
                ));
    }


	//Weighted sensitivies vega
	public static Map<String, Map<Integer, Double>> calculateWeightedSensitivitiesVega(
			Map<String, Map<Integer, Double>> netSensitivitiesVega) {

		return netSensitivitiesVega.entrySet().stream()
				.collect(Collectors.toMap(
						Map.Entry::getKey, // Risk Factor
						riskEntry -> riskEntry.getValue().entrySet().stream()
								.collect(Collectors.toMap(
										Map.Entry::getKey, // Bucket
										bucketEntry -> {
											double riskWeightVega = RISK_WEIGHTS.getOrDefault(bucketEntry.getKey(), new Double[] { 1.0, 1.0 })[1];
											return bucketEntry.getValue() * riskWeightVega;
									}

				))));
	}

    //Intra-bucket correlation delta
    public static double getIntraBucketCorrelation(int bucket, String scenario) {
        if (bucket == 11) {
            return 1.0;
        }

        double baseCorrelation;
        if (bucket >= 1 && bucket <= 4) baseCorrelation = 0.15;
        else if (bucket >= 5 && bucket <= 8) baseCorrelation = 0.25;
        else if (bucket == 9) baseCorrelation = 0.075;
        else if (bucket == 10) baseCorrelation = 0.125;
        else if (bucket == 11) baseCorrelation = 1.0;
        else if (bucket == 12 || bucket == 13) baseCorrelation = 0.80;
        else baseCorrelation = 0.15;

        switch (scenario) {
            case "high":
                return Math.min(baseCorrelation * 1.25, 1.0);
            case "low":
                return Math.max(2 * baseCorrelation - 1.0, 0.75 * baseCorrelation);
            default:
                return baseCorrelation;
        }
    }

    //Intra-bucket aggregation delta
    public static Map<Integer, Double> aggregateIntraBucketDelta(
            Map<String, Map<Integer, Double>> weightedSensitivities, String scenario) {
        
        Map<Integer, Double> capitalByBucket = new HashMap<>();

        Map<Integer, List<Double>> sensitivitiesByBucket = new HashMap<>();

        weightedSensitivities.forEach((riskFactorDelta, bucketMap) ->
                bucketMap.forEach((bucket, ws) -> {
                    sensitivitiesByBucket.computeIfAbsent(bucket, k -> new ArrayList<>()).add(ws);
                })
        );

        for (Map.Entry<Integer, List<Double>> entry : sensitivitiesByBucket.entrySet()) {
            int bucket = entry.getKey();
            List<Double> wsList = entry.getValue();

            double sumSquared = 0.0;
            double sumCrossTerms = 0.0;

            for (int i = 0; i < wsList.size(); i++) {
                double WS_k = wsList.get(i);
                sumSquared += WS_k * WS_k;

                for (int j = i + 1; j < wsList.size(); j++) {
                    double WS_l = wsList.get(j);
                    double correlation = getIntraBucketCorrelation(bucket, scenario);
                    sumCrossTerms += correlation * WS_k * WS_l;
                }
            }

            double capitalRequirement = Math.sqrt(Math.max(0, sumSquared + sumCrossTerms));
            capitalByBucket.merge(bucket, capitalRequirement, Double::sum);
        }

        return capitalByBucket;
    }

    
    //Maturity map of the options used into the intra-bucket aggreagtion for vega
    public static Map<String, Map<Integer, Double>> extractOptionMaturities(List<Trade> trades) {
        Map<String, Map<Integer, Double>> optionMaturities = new HashMap<>();

        for (Trade trade : trades) {
            optionMaturities
                .computeIfAbsent(trade.getRiskFactorVega(), k -> new HashMap<>())
                .put(trade.getBucket(), trade.getMaturity());
        }

        return optionMaturities;
    }
    
    // Intra-bucket vega
    public static Map<Integer, Double> aggregateIntraBucketVega( 
            Map<String, Map<Integer, Double>> weightedSensitivities,
            Map<String, Map<Integer, Double>> optionMaturities,
            String scenario) {

        Map<Integer, Double> capitalByBucket = new HashMap<>();
        Map<Integer, List<Double>> sensitivitiesByBucket = new HashMap<>();
        Map<Integer, List<Double>> maturitiesByBucket = new HashMap<>();

        weightedSensitivities.forEach((riskFactorVega, bucketMap) ->
                bucketMap.forEach((bucket, ws) -> {
                	
                        double maturity = optionMaturities.getOrDefault(riskFactorVega, Collections.emptyMap())
                                .getOrDefault(bucket, 1.0);

                        sensitivitiesByBucket.computeIfAbsent(bucket, k -> new ArrayList<>()).add(ws);
                        maturitiesByBucket.computeIfAbsent(bucket, k -> new ArrayList<>()).add(maturity);
                })
        );

        for (Map.Entry<Integer, List<Double>> entry : sensitivitiesByBucket.entrySet()) {
            int bucket = entry.getKey();
            List<Double> wsList = entry.getValue();
            List<Double> maturityList = maturitiesByBucket.get(bucket);

            double sumSquared = 0.0;
            double sumCrossTerms = 0.0;

            for (int i = 0; i < wsList.size(); i++) {
                double WS_k = wsList.get(i);
                double T_k = maturityList.get(i);
                sumSquared += WS_k * WS_k;

                for (int j = i + 1; j < wsList.size(); j++) {
                    double WS_l = wsList.get(j);
                    double T_l = maturityList.get(j);

                    double rho_delta = getIntraBucketCorrelation(bucket, scenario);
                    double rho_maturity = Math.exp(-0.01 * Math.abs(T_k - T_l) / Math.min(T_k, T_l));
                    double rho_kl = Math.min(rho_delta * rho_maturity, 1.0);

                    sumCrossTerms += rho_kl * WS_k * WS_l;
                }
            }

            double capitalRequirement = Math.sqrt(Math.max(0, sumSquared + sumCrossTerms));
            capitalByBucket.put(bucket, capitalRequirement);
        }

        return capitalByBucket;
    }

    //Inter-bucket delta e vega correlations
	private static double getInterBucketCorrelation(int bucketA, int bucketB, String scenario) {
	    double baseCorrelation;

	    if (bucketA == 11 || bucketB == 11) return 0.0;

	    if (bucketA >= 1 && bucketA <= 10 && bucketB >= 1 && bucketB <= 10) baseCorrelation = 0.15;
	    else if ((bucketA == 12 && bucketB == 13) || (bucketA == 13 && bucketB == 12)) baseCorrelation = 0.75;
	    else baseCorrelation = 0.45;

	    switch (scenario) {
	        case "high":
	            return Math.min(baseCorrelation * 1.25, 1.0);
	        case "low":
	            return Math.max(2 * baseCorrelation - 1.0, 0.75 * baseCorrelation);
	        default: // Scenario "medium"
	            return baseCorrelation;
	    }
	}

	//Inter-bucket delta e vega aggregation
	private static double calculateInterBucketAggregation(Map<Integer, Double> capitalByBucket,
			Map<String, Map<Integer, Double>> weightedSensitivities, String scenario) {
		double sum_Kb2 = 0.0;
		double sum_cross_terms = 0.0;

		List<Integer> buckets = new ArrayList<>(capitalByBucket.keySet());

		for (int i = 0; i < buckets.size(); i++) {
			int b = buckets.get(i);
			double K_b = capitalByBucket.getOrDefault(b, 0.0);
			sum_Kb2 += K_b * K_b;

	        double S_b = weightedSensitivities.values().stream()
	                .flatMap(bucketMap -> bucketMap.entrySet().stream())
	                .filter(entry -> entry.getKey().equals(b))
	                .mapToDouble(Map.Entry::getValue)
	                .sum();
	        
			if (S_b < 0) {
				S_b = Math.max(Math.min(S_b, K_b), -K_b);
			}

			for (int j = i + 1; j < buckets.size(); j++) {
				int c = buckets.get(j);
				double K_c = capitalByBucket.getOrDefault(c, 0.0);

	            double S_c = weightedSensitivities.values().stream()
	                    .flatMap(bucketMap -> bucketMap.entrySet().stream())
	                    .filter(entry -> entry.getKey().equals(c))
	                    .mapToDouble(Map.Entry::getValue)
	                    .sum();
	            
				if (S_c < 0) {
					S_c = Math.max(Math.min(S_c, K_c), -K_c);
				}

				double gamma_bc = getInterBucketCorrelation(b, c, scenario);
				sum_cross_terms += gamma_bc * S_b * S_c;
			}
		}

		return Math.sqrt(Math.max(0, sum_Kb2 + sum_cross_terms));
	}

	//Intra-bucket delta
	public static double aggregateInterBucketDelta(
	        Map<Integer, Double> capitalByBucket,
	        Map<String, Map<Integer, Double>> weightedSensitivitiesDelta,
	        String scenario) {

	    return calculateInterBucketAggregation(capitalByBucket, weightedSensitivitiesDelta, scenario);
	}

	//Intra-bucket vega
	public static double aggregateInterBucketVega(
	        Map<Integer, Double> capitalByBucket,
	        Map<String, Map<Integer, Double>> weightedSensitivitiesVega,
	        String scenario) {

	    return calculateInterBucketAggregation(capitalByBucket, weightedSensitivitiesVega, scenario);
	}

	
    //Intra-bucket curvature risk correlations
    private static double getAdjustedCorrelationIntra(int bucket, String scenario) {
        double baseCorrelation = getIntraBucketCorrelation(bucket, "medium");
        double squaredCorrelation = Math.pow(baseCorrelation, 2);

        switch (scenario) {
            case "high":
                return Math.min(squaredCorrelation * 1.25, 1.0);
            case "low":
                return Math.max(2 * squaredCorrelation - 1.0, 0.75 * squaredCorrelation);
            default:
                return squaredCorrelation;
        }
    }

    //Intra-bucket aggregation curvature
    public static Map<Integer, Double> aggregateCurvatureRiskIntraBucket(
            Map<String, Map<Integer, Double[]>> curvatureRisk, 
            Map<Integer, String> selectedScenarioByBucket, 
            String scenario) {

        Map<Integer, Double> capitalByBucket = new HashMap<>();
        Map<Integer, List<Double[]>> curvatureByBucket = new HashMap<>();

        curvatureRisk.forEach((riskFactor, bucketMap) ->
            bucketMap.forEach((bucket, cvrValues) ->
                curvatureByBucket.computeIfAbsent(bucket, k -> new ArrayList<>()).add(cvrValues)
            )
        );

        for (Map.Entry<Integer, List<Double[]>> entry : curvatureByBucket.entrySet()) {
            int bucket = entry.getKey();
            List<Double[]> cvrList = entry.getValue();

            double sum_CVR_plus_squared = 0.0;
            double sum_CVR_minus_squared = 0.0;
            double sum_CVR_plus_corr = 0.0;
            double sum_CVR_minus_corr = 0.0;

            for (int i = 0; i < cvrList.size(); i++) {
                double CVR_k_plus = cvrList.get(i)[0];
                double CVR_k_minus = cvrList.get(i)[1];
                sum_CVR_plus_squared += Math.pow(Math.max(CVR_k_plus, 0), 2);
                sum_CVR_minus_squared += Math.pow(Math.max(CVR_k_minus, 0), 2);

                for (int j = i + 1; j < cvrList.size(); j++) {
                    double CVR_l_plus = cvrList.get(j)[0];
                    double CVR_l_minus = cvrList.get(j)[1];
                    double correlation = getAdjustedCorrelationIntra(bucket, scenario);

                    double psi_plus = (CVR_k_plus <= 0 && CVR_l_plus <= 0) ? 0 : 1;
                    double psi_minus = (CVR_k_minus <= 0 && CVR_l_minus <= 0) ? 0 : 1;

                    sum_CVR_plus_corr += correlation * CVR_k_plus * CVR_l_plus * psi_plus;
                    sum_CVR_minus_corr += correlation * CVR_k_minus * CVR_l_minus * psi_minus;
                }
            }

            double K_b_plus = Math.sqrt(Math.max(0, sum_CVR_plus_squared + sum_CVR_plus_corr));
            double K_b_minus = Math.sqrt(Math.max(0, sum_CVR_minus_squared + sum_CVR_minus_corr));
            double K_b = Math.max(K_b_plus, K_b_minus);

            String selectedScenario;
            if (K_b_plus == K_b_minus) {
                double sum_CVR_plus = cvrList.stream().mapToDouble(cvr -> Math.max(cvr[0], 0)).sum();
                double sum_CVR_minus = cvrList.stream().mapToDouble(cvr -> Math.max(cvr[1], 0)).sum();
                selectedScenario = sum_CVR_plus > sum_CVR_minus ? "plus" : "minus";
            } else {
                selectedScenario = (K_b == K_b_plus) ? "plus" : "minus";
            }

            capitalByBucket.put(bucket, K_b);
            selectedScenarioByBucket.put(bucket, selectedScenario);
        }

        return capitalByBucket;
    }

    
    //Inter-bucket aggregation curvature correlations
    private static double getAdjustedCorrelationInter(int bucketA, int bucketB, String scenario) {
        double baseCorrelation = getInterBucketCorrelation(bucketA, bucketB, "medium");
        double squaredCorrelation = Math.pow(baseCorrelation, 2);

        switch (scenario) {
            case "high":
                return Math.min(squaredCorrelation * 1.25, 1.0);
            case "low":
                return Math.max(2 * squaredCorrelation - 1.0, 0.75 * squaredCorrelation);
            default:
                return squaredCorrelation;
        }
    }

    //Inter-bucket aggregation curvature
    public static double aggregateCurvatureRiskInterBucket(
            Map<Integer, Double> capitalByBucket,
            Map<Integer, String> selectedScenarioByBucket,
            Map<String, Map<Integer, Double[]>> curvatureRisk, String scenario) {

        double sum_Kb2 = capitalByBucket.values().stream()
                .mapToDouble(K_b -> K_b * K_b)
                .sum();

        Map<Integer, Double> S_b_map = new HashMap<>();
        for (Map.Entry<String, Map<Integer, Double[]>> entry : curvatureRisk.entrySet()) {
            for (Map.Entry<Integer, Double[]> bucketEntry : entry.getValue().entrySet()) {
                int bucket = bucketEntry.getKey();
                double sum_CVR_plus = bucketEntry.getValue()[0];
                double sum_CVR_minus = bucketEntry.getValue()[1];

                String selectedScenario = selectedScenarioByBucket.getOrDefault(bucket, "plus");
                double S_b = selectedScenario.equals("plus") ? sum_CVR_plus : sum_CVR_minus;

                S_b_map.merge(bucket, S_b, Double::sum);
            }
        }

        double sum_cross_terms = 0.0;
        List<Integer> bucketList = new ArrayList<>(S_b_map.keySet());

        for (int i = 0; i < bucketList.size(); i++) {
            int b = bucketList.get(i);
            double S_b = S_b_map.getOrDefault(b, 0.0);

            for (int j = i + 1; j < bucketList.size(); j++) {
                int c = bucketList.get(j);
                double S_c = S_b_map.getOrDefault(c, 0.0);

                double psi_bc = (S_b < 0 && S_c < 0) ? 0 : 1;
                double gamma_bc = getAdjustedCorrelationInter(b, c, scenario);

                sum_cross_terms += gamma_bc * S_b * S_c * psi_bc;
            }
        }

        double result = Math.sqrt(Math.max(0, sum_Kb2 + sum_cross_terms));

        return result;
    }

}
