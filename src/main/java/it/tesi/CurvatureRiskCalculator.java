package it.tesi;

import net.finmath.exception.CalculationException;
import net.finmath.montecarlo.*;
import net.finmath.montecarlo.assetderivativevaluation.*;
import net.finmath.montecarlo.assetderivativevaluation.models.BlackScholesModel;
import net.finmath.montecarlo.assetderivativevaluation.products.BermudanOption;
import net.finmath.montecarlo.assetderivativevaluation.products.EuropeanOption;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

import java.util.*;
import java.util.stream.Collectors;

public class CurvatureRiskCalculator {

    private final int numberOfPaths;
    private final int numberOfTimeSteps;
    private final double deltaT;
    private final int seed;

    
    public CurvatureRiskCalculator(int numberOfPaths, int numberOfTimeSteps, double deltaT, int seed) {
        this.numberOfPaths = numberOfPaths;
        this.numberOfTimeSteps = numberOfTimeSteps;
        this.deltaT = deltaT;
        this.seed = seed;
    }

    
    public Map<String, Map<Integer, Double[]>> calculateCurvatureRisk(List<Trade> trades) throws CalculationException {
        Map<String, Map<Integer, Double[]>> curvatureRisk = new HashMap<>();

        Map<String, List<Trade>> tradesByRiskFactor  = trades.stream()
                .filter(trade -> trade.getAssetType().equalsIgnoreCase("Option"))
                .collect(Collectors.groupingBy(Trade::getRiskFactorDelta));

        for (Map.Entry<String, List<Trade>> entry : tradesByRiskFactor .entrySet()) {
            String riskFactorDelta = entry.getKey();
            List<Trade> riskFactorDeltaTrades = entry.getValue();
            int bucket = riskFactorDeltaTrades.get(0).getBucket();
            double riskWeight = getCurvatureRiskWeight(bucket);

            double CVR_plus = 0.0;
            double CVR_minus = 0.0;

            for (Trade trade : riskFactorDeltaTrades) {
                double delta = trade.getDelta();
                double shockedUnderlyingPriceUp = trade.getUnderlyingPrice() * (1 + riskWeight);
                double shockedUnderlyingPriceDown = trade.getUnderlyingPrice() * (1 - riskWeight);

                double valueBase = priceOption(trade, trade.getUnderlyingPrice());
                double valueUp = priceOption(trade, shockedUnderlyingPriceUp);
                double valueDown = priceOption(trade, shockedUnderlyingPriceDown);

                CVR_plus += -(valueUp - valueBase - riskWeight * delta);
                CVR_minus += -(valueDown - valueBase + riskWeight * delta);
                
                trade.setCurvatureRisk(CVR_plus, CVR_minus);
            }

            curvatureRisk.computeIfAbsent(riskFactorDelta, k -> new HashMap<>())
                         .put(bucket, new Double[]{CVR_plus, CVR_minus});
        }

        return curvatureRisk;
    }

    //pricing
    private double priceOption(Trade trade, double shockedUnderlyingPrice) throws CalculationException {
        RandomVariable initialValue = new RandomVariableFromDoubleArray(shockedUnderlyingPrice);
        RandomVariable riskFreeRate = new RandomVariableFromDoubleArray(trade.getRiskFreeRate());
        RandomVariable volatility = new RandomVariableFromDoubleArray(trade.getVolatility());

        BlackScholesModel model = new BlackScholesModel(initialValue, riskFreeRate, volatility, new RandomVariableFromArrayFactory());
        TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0, numberOfTimeSteps, deltaT);
        BrownianMotion brownianMotion = new BrownianMotionFromMersenneRandomNumbers(timeDiscretization, 1, numberOfPaths, seed);
        MonteCarloAssetModel monteCarloModel = new MonteCarloAssetModel(new EulerSchemeFromProcessModel(model, brownianMotion));

        RandomVariable value;
        if (trade.getOptionStyle().equalsIgnoreCase("European")) {
            EuropeanOption europeanOption = new EuropeanOption(trade.getUnderlying(), trade.getMaturity(), trade.getStrikes()[0], trade.getOptionType());
            value = europeanOption.getValue(0.0, monteCarloModel);
        } else {
            double[] exerciseDates = trade.getExerciseDates();
            double[] notionals = new double[exerciseDates.length];
            double[] strikes = trade.getStrikes();
            Arrays.fill(notionals, 1.0);

            BermudanOption bermudanOption = new BermudanOption(exerciseDates, notionals, strikes, BermudanOption.ExerciseMethod.ESTIMATE_COND_EXPECTATION);
            value = bermudanOption.getValue(0.0, monteCarloModel);
        }

        return value.getAverage();
    }

    //Find the risk weights Delta to be used as shocks
    public static double getCurvatureRiskWeight(int bucket) {
        Double[] weights = SensitivityAggregator.getRiskWeights().getOrDefault(bucket, new Double[]{1.0, 1.0});
        
        return weights[0];
    }


}
