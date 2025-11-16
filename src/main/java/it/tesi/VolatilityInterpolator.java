package it.tesi;

import java.util.Locale;

import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

public class VolatilityInterpolator {

    private static final double[] MATURITIES = {0.5, 1.0, 3.0, 5.0, 10.0};
    private static final TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(MATURITIES);

    
    //Find the nearest tenor for a given data maturity
    public static double getNearestRegulatoryTenor(double maturity) {
        int indexGreaterOrEqual = timeDiscretization.getTimeIndexNearestGreaterOrEqual(maturity);
        int indexLessOrEqual = timeDiscretization.getTimeIndexNearestLessOrEqual(maturity);

        if (indexGreaterOrEqual < 0) {
            return MATURITIES[0];
        } else if (indexGreaterOrEqual >= MATURITIES.length) {
            return MATURITIES[MATURITIES.length - 1];
        }

        if (indexLessOrEqual >= 0) {
            double lower = MATURITIES[indexLessOrEqual];
            double upper = MATURITIES[indexGreaterOrEqual];

            return (Math.abs(maturity - lower) < Math.abs(maturity - upper)) ? lower : upper;
        }

        return MATURITIES[indexGreaterOrEqual];
    }

    
    //Produce the formatted riskFactorVega, while including underlying and tenor
    public static String getFormattedRiskFactorVega(String underlying, double maturity) {
        double nearestTenor = getNearestRegulatoryTenor(maturity);
        return String.format(Locale.US, "ImpliedVol-%s-%.1fY", underlying, nearestTenor);
    }
}
