# Master-s-Thesis-Project
This project provides a Java implementation of the Sensitivities-Based Method (SBM) of the Fundamental Review of the Trading Book (FRTB) framework, integrating Automatic Adjoint Differentiation (AAD) to compute exact derivatives for delta, vega, and curvature risk measures.
The implementation is applied to an equity portfolio containing cash equities, European options, and Bermudan options.

📌 Project Overview

Under the FRTB regulation, banks must compute market risk capital requirements based on sensitivities. The regulation typically relies on finite differences (FD) to approximate derivatives.
This project replaces FD with Automatic Adjoint Differentiation, achieving:

Exact derivatives (up to machine precision)

Improved computational performance, especially as portfolio size increases

Full workflow replication of the sensitivities-based method (SBM)

📁 Features
✔️ Portfolio Processing

Reads trade data from CSV files

Assigns each instrument to its regulatory bucket

Identifies risk factors:

Spot price (for delta and curvature)

Implied volatility mapped to regulatory tenors (for vega)

✔️ Sensitivity Computation

Implemented using both:

Finite Difference (FD)

Automatic Adjoint Differentiation (AAD)

Computed measures include:

Delta

Vega

Curvature (per FRTB formula)

✔️ Aggregation Framework

Conforms fully to the FRTB standard:

Net Sensitivities

Weighted Sensitivities

Intra-bucket aggregation

Inter-bucket aggregation

Capital requirement under low / medium / high correlation

Final regulatory charge:

K_final = max(K_low, K_medium, K_high)

✔️ Sample Portfolio Included

2 equities

2 European options

2 Bermudan options

Complete CSV sample files for trades and bucket mapping

🧱 Project Architecture
Main Components
Component	Purpose
CSVParser	Reads input datasets
Trade	Trade representation with bucket and risk factor metadata
VolatilityInterpolator	Maps implied volatilities to regulatory tenors
AADPricer	Computes instrument prices and AAD sensitivities
CurvatureRiskCalculator	Implements curvature formulas
SensitivityAggregator	Aggregates risk across factors and buckets
Main	Orchestrates the full FRTB-SBM workflow
🔬 Automatic Adjoint Differentiation (AAD)

AAD applies the reverse mode of Automatic Differentiation, efficiently computing all required derivatives by propagating adjoints backward through the computation graph.

Advantages over FD:

Lower numerical error

Better performance for large portfolios

Exact chain rule application
