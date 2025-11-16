package it.tesi;

import net.finmath.exception.CalculationException;
import net.finmath.functions.AnalyticFormulas;
import net.finmath.montecarlo.*;
import net.finmath.montecarlo.assetderivativevaluation.*;
import net.finmath.montecarlo.assetderivativevaluation.models.BlackScholesModel;
import net.finmath.montecarlo.assetderivativevaluation.products.BermudanOption;
import net.finmath.montecarlo.assetderivativevaluation.products.EuropeanOption;
import net.finmath.montecarlo.automaticdifferentiation.RandomVariableDifferentiable;
import net.finmath.montecarlo.automaticdifferentiation.backward.RandomVariableDifferentiableAAD;
import net.finmath.montecarlo.automaticdifferentiation.backward.RandomVariableDifferentiableAADFactory;
import net.finmath.montecarlo.process.*;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.*;

import java.util.*;

public class AADPricer {

    private final int numberOfPaths;
    private final int numberOfTimeSteps;
    private final double deltaT;
    private final int seed;


    public AADPricer(int numberOfPaths, int numberOfTimeSteps, double deltaT, int seed) {
        this.numberOfPaths = numberOfPaths;
        this.numberOfTimeSteps = numberOfTimeSteps;
        this.deltaT = deltaT;
        this.seed = seed;
    }

    public List<Trade> priceAndCalculateGreeks(List<Trade> trades) throws CalculationException {
        List<Trade> updatedTrades = new ArrayList<>();
        RandomVariableDifferentiableAADFactory randomVariableFactory = new RandomVariableDifferentiableAADFactory();

        for (Trade trade : trades) {
            System.out.println("Calcolando trade: " + trade.getDealNumber());

            double deltaAAD = 0.0, deltaFD = 0.0, vegaAAD = 0.0, vegaFD = 0.0;
            double analyticValue = 0.0, analyticDelta = 0.0, analyticVega = 0.0;
            double timeAAD = 0.0, timeFD = 0.0, timeAnalytic = 0.0;
            
            RandomVariable value = null;
            RandomVariable valueOriginal = null;
            RandomVariable valueUp = null;
            RandomVariable valueVolUp = null;
            
            if (trade.getAssetType().equalsIgnoreCase("Stock")) {
            	value = new RandomVariableFromDoubleArray(trade.getUnderlyingPrice());
                deltaAAD = trade.getUnderlyingPrice();
                vegaAAD = 0.0;
                
            } 
            else {
                
                //Definition of Random Differentiable Variable 
                RandomVariableDifferentiableAAD initialValue = (RandomVariableDifferentiableAAD) randomVariableFactory.createRandomVariable(trade.getUnderlyingPrice());
                RandomVariableDifferentiable riskFreeRate = randomVariableFactory.createRandomVariable(trade.getRiskFreeRate());
                RandomVariableDifferentiableAAD volatility = (RandomVariableDifferentiableAAD) randomVariableFactory.createRandomVariable(trade.getVolatility());

                //Black-Scholes model creation
                BlackScholesModel model = new BlackScholesModel(initialValue, riskFreeRate, volatility, randomVariableFactory);

                //Time discretization and MC simulation
                TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0, numberOfTimeSteps, deltaT);
                BrownianMotion brownianMotion = new BrownianMotionFromMersenneRandomNumbers(timeDiscretization, 1, numberOfPaths, seed);
                EulerSchemeFromProcessModel process = new EulerSchemeFromProcessModel(model, brownianMotion);
                MonteCarloAssetModel monteCarloModel = new MonteCarloAssetModel(process);

                //Calculation with AAD
                long startAAD = System.nanoTime();
                if (trade.getOptionStyle().equalsIgnoreCase("European")) {
                    EuropeanOption europeanOption = new EuropeanOption(trade.getUnderlying(), trade.getMaturity(), trade.getStrikes()[0], trade.getOptionType());
                    value = (RandomVariableDifferentiable) europeanOption.getValue(0.0, monteCarloModel);
                } else {
                    double[] exerciseDates = trade.getExerciseDates();
                    double[] notionals = new double[exerciseDates.length];
                    double[] strikes = trade.getStrikes();

                    Arrays.fill(notionals, 1.0);
                    

                    BermudanOption bermudanOption = new BermudanOption(exerciseDates, notionals, strikes, BermudanOption.ExerciseMethod.ESTIMATE_COND_EXPECTATION);
                    value = bermudanOption.getValue(0.0, monteCarloModel);

                    printGradientInfo((RandomVariableDifferentiableAAD) value);

                }

                Map<Long, RandomVariable> derivative = ((RandomVariableDifferentiableAAD) value).getGradient();
                deltaAAD = (derivative.get(initialValue.getID()).getAverage()) * trade.getUnderlyingPrice();
                vegaAAD = (derivative.get(volatility.getID()).getAverage()) * trade.getVolatility();
                long endAAD = System.nanoTime();
                timeAAD = (endAAD - startAAD) / 1e6;

                
                // Calculation with FD
                long startFD = System.nanoTime();

                BlackScholesModel modelOriginal = new BlackScholesModel(initialValue, riskFreeRate, volatility, randomVariableFactory);
                MonteCarloAssetModel monteCarloOriginal = new MonteCarloAssetModel(new EulerSchemeFromProcessModel(modelOriginal, brownianMotion));

                if (trade.getOptionStyle().equalsIgnoreCase("European")) {
                    EuropeanOption europeanOption = new EuropeanOption(trade.getUnderlying(), trade.getMaturity(), trade.getStrikes()[0], trade.getOptionType());
                    valueOriginal = (RandomVariableDifferentiable) europeanOption.getValue(0.0, monteCarloOriginal);

                    //Delta
                    BlackScholesModel modelUp = new BlackScholesModel(initialValue.mult(1.01), riskFreeRate, volatility, randomVariableFactory);
                    MonteCarloAssetModel monteCarloUp = new MonteCarloAssetModel(new EulerSchemeFromProcessModel(modelUp, brownianMotion));
                    valueUp = (RandomVariableDifferentiable) europeanOption.getValue(0.0, monteCarloUp);

                    //Vega
                    BlackScholesModel modelVolUp = new BlackScholesModel(initialValue, riskFreeRate, volatility.mult(1.01), randomVariableFactory);
                    MonteCarloAssetModel monteCarloVolUp = new MonteCarloAssetModel(new EulerSchemeFromProcessModel(modelVolUp, brownianMotion));
                    valueVolUp = (RandomVariableDifferentiable) europeanOption.getValue(0.0, monteCarloVolUp);
                } 
                else { //Bermudan Option
                    double[] exerciseDates = trade.getExerciseDates();
                    double[] notionals = new double[exerciseDates.length];
                    double[] strikes = trade.getStrikes();

                    Arrays.fill(notionals, 1.0);

                    BermudanOption bermudanOption = new BermudanOption(exerciseDates, notionals, strikes, BermudanOption.ExerciseMethod.ESTIMATE_COND_EXPECTATION);

                    valueOriginal = bermudanOption.getValue(0.0, monteCarloOriginal);

                    //Delta FD
                    BlackScholesModel modelUp = new BlackScholesModel(initialValue.mult(1.01), riskFreeRate, volatility, randomVariableFactory);
                    MonteCarloAssetModel monteCarloUp = new MonteCarloAssetModel(new EulerSchemeFromProcessModel(modelUp, brownianMotion));
                    valueUp = bermudanOption.getValue(0.0, monteCarloUp);

                    //Vega FD
                    BlackScholesModel modelVolUp = new BlackScholesModel(initialValue, riskFreeRate, volatility.mult(1.01), randomVariableFactory);
                    MonteCarloAssetModel monteCarloVolUp = new MonteCarloAssetModel(new EulerSchemeFromProcessModel(modelVolUp, brownianMotion));
                    valueVolUp = bermudanOption.getValue(0.0, monteCarloVolUp);
                }

                //Delta and Vega FD
                deltaFD = ((valueUp.getAverage() - valueOriginal.getAverage()) / 0.01);
                vegaFD = ((valueVolUp.getAverage() - valueOriginal.getAverage()) / 0.01); 

                long endFD = System.nanoTime();
                timeFD = (endFD - startFD) / 1e6;

              
                //Check with Analytic Formulas
                if (trade.getOptionStyle().equalsIgnoreCase("European")) {    
                boolean isCall = trade.getOptionType() == 1.0;
                analyticValue = AnalyticFormulas.blackScholesOptionValue(trade.getUnderlyingPrice(), trade.getRiskFreeRate(), trade.getVolatility(), trade.getMaturity(), trade.getStrikes()[0], isCall);
                double deltaCall = AnalyticFormulas.blackScholesOptionDelta(trade.getUnderlyingPrice(), trade.getRiskFreeRate(), trade.getVolatility(), trade.getMaturity(), trade.getStrikes()[0]);
                analyticDelta = ((trade.getOptionType() == 1.0) ? deltaCall : (deltaCall - 1)) * trade.getUnderlyingPrice();
                analyticVega = AnalyticFormulas.blackScholesOptionVega(trade.getUnderlyingPrice(), trade.getRiskFreeRate(), trade.getVolatility(), trade.getMaturity(), trade.getStrikes()[0]) * trade.getVolatility();
               
                }
            }


            System.out.printf("Underlying %s | Bucket: %s | AssetType: %s | OptionStyle: %s\n", trade.getUnderlying(), trade.getBucket(), trade.getAssetType(), trade.getOptionStyle());
            System.out.printf("Value AAD: %.6f | Value FD: %.6f | Value Analytic: %.6f\n", value.getAverage(), (valueOriginal != null ? valueOriginal.getAverage() : 0.0), analyticValue);
            System.out.printf("Delta AAD: %.6f | Delta FD: %.6f | Analytic Delta: %.6f\n", deltaAAD, deltaFD, analyticDelta);
            System.out.printf("Vega AAD: %.6f | Vega FD: %.6f | Analytic Vega: %.6f\n", vegaAAD, vegaFD, analyticVega);
            System.out.printf("Time AAD: %.3f ms | Time FD: %.3f ms | Time Analytic: %.3f ms\n\n", timeAAD, timeFD, timeAnalytic);
            
            //Aggiorno il Trade con i valori di valueAAD, deltaAAD e vegaAAD
            Trade updatedTrade = new Trade(
                    trade.getPortfolio(), trade.getDealNumber(), trade.getAssetType(), trade.getOptionStyle(),
                    trade.getRiskFactorDelta(), trade.getRiskFactorVega(), trade.getUnderlying(), trade.getBucket(), trade.getOptionType(),
                    trade.getCurrency(), trade.getAmount(), trade.getVolatility(),
                    trade.getStrikes(), trade.getUnderlyingPrice(), trade.getMaturity(), trade.getExerciseDates(),
                    trade.getRiskFreeRate(), value.getAverage(), deltaAAD, vegaAAD, trade.getCurvatureRiskPlus(), trade.getCurvatureRiskMinus()
            );

            updatedTrades.add(updatedTrade);
        }

        return updatedTrades;
    }
    
    private static void printGradientInfo(RandomVariableDifferentiable value) {
        System.out.println("== GRADIENT INFO ==");

        Map<Long, RandomVariable> gradient = value.getGradient();

        for (Map.Entry<Long, RandomVariable> entry : gradient.entrySet()) {
            Long id = entry.getKey();
            RandomVariable derivative = entry.getValue();

            System.out.printf("Input ID: %d | Derivative avg: %.6f%n", id, derivative.getAverage());
        }
    }

}
