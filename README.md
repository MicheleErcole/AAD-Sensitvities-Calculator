# **AAD Sensitivities Calculator**

This project provides a Java implementation of the **Sensitivities-Based Method (SBM)** of the **Fundamental Review of the Trading Book (FRTB)** framework, integrating **Automatic Adjoint Differentiation (AAD)** to compute exact derivatives for delta, vega, and curvature risk measures.
The implementation is applied to an equity portfolio containing cash equities, European options, and Bermudan options.

---

## 📁 **Features**

### ✔️ Portfolio Processing

* Reads trade data from CSV files
* Assigns each instrument to its regulatory bucket
* Identifies risk factors:

  * Spot price (for delta and curvature)
  * Implied volatility mapped to regulatory tenors (for vega)

### ✔️ Sensitivity Computation

Implemented using both:

* **Finite Difference (FD)**
* **Automatic Adjoint Differentiation (AAD)**

Computed measures include:

* Delta
* Vega
* Curvature (per FRTB formula)

### ✔️ Aggregation Framework

Fully aligned with FRTB standards:

* Net sensitivities
* Weighted sensitivities
* Intra-bucket aggregation
* Inter-bucket aggregation
* Capital requirement under **low / medium / high** correlation
* Final regulatory charge selection

### ✔️ Sample Portfolio Included

* 2 equities
* 2 European options
* 2 Bermudan options
* Complete CSV input files for trades and bucket mapping

---

## 🧱 **Project Architecture**

| Component                   | Purpose                                                |
| --------------------------- | ------------------------------------------------------ |
| **CSVParser**               | Reads input datasets                                   |
| **Trade**                   | Represents trades with bucket and risk factor metadata |
| **VolatilityInterpolator**  | Maps implied volatilities to regulatory tenors         |
| **AADPricer**               | Computes instrument prices and AAD sensitivities       |
| **CurvatureRiskCalculator** | Implements curvature formulas                          |
| **SensitivityAggregator**   | Aggregates risk across factors and buckets             |
| **Main**                    | Orchestrates the full FRTB-SBM workflow                |

---
![UML Diagram](UML%20Diagram.png)

