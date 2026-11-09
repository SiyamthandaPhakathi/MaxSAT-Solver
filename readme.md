# MaxSAT Stochastic Local Search Solver

This repository contains a high-performance, two-phase Stochastic Local Search (SLS) solver for unweighted and weighted Partial MaxSAT (WCNF) problems. It features a highly optimized bit-packed data representation, a fast unit-propagation preprocessor, and two algorithm variants: a baseline **ProbSAT** implementation and an experimental **Tabu-enhanced Prototype**.

## How the Program Works

The solver is designed for speed and memory locality, processing MaxSAT instances through a heavily optimized pipeline:

### 1. Parsing & Soft Clause Relaxation (`Parser.java`, `SoftClauseRelaxer.java`)
The DIMACS `cnf` or `wcnf` files are ingested and converted into an initial flat array format. Because handling arbitrary-length soft clauses dynamically during search is computationally expensive, the solver applies **Soft Clause Relaxation**. 
For any soft clause with length $k \ge 2$, such as $(l_1 \lor l_2 \lor \dots \lor l_k)$ with weight $w$:
* It creates a new blocking variable $y$.
* It creates a new **hard clause**: $(l_1 \lor l_2 \lor \dots \lor l_k \lor y)$.
* It creates a new **soft unit clause**: $(\neg y)$ with weight $w$.

This guarantees all soft clauses during search are unit clauses, reducing break-cost calculations to $O(1)$.

### 2. Preprocessing (`Preprocessor.java`)
A fast unit-propagation algorithm sweeps the hard clauses to resolve forced variables eagerly. If the hard formula is proven unsatisfiable at this stage, the solver halts early. It outputs a simplified, dense formula to the next stage.

### 3. Bit-Packed Data Representation (`BitPacking.java`)
To avoid cache misses from scattered object references, the solver heavily packs data into primitive arrays:
* **Literal Packing**: Literals are stored as 32-bit integers. Bits 1-31 hold the `varID`, and bit 0 holds the sign.
* **Dense Variable IDs**: Sparse variable IDs (caused by preprocessing) are remapped to a continuous, dense range.
* **Variable State Data**: A single integer array (`varData`) tracks per-variable properties. Bit 0 tracks the boolean assignment state, and bit 1 tracks if the variable is permanently locked (forced by preprocessing).
* **Flat Occurrence Lists**: Clause offsets are implemented instead of zero-delimiters to avoid branching during search loops.

### 4. Two-Phase Search
The core SLS search operates in two sequential phases:
1. **Hard Clause Satisfaction**: The search ignores soft costs entirely. It utilizes a Luby restart sequence (`Luby.java`) and a Roulette Wheel selection mechanism weighted by hard break costs (polynomial or exponential, computed in `ProbSATWeights.java`) to find a hard-feasible assignment.
2. **Soft Clause Optimization**: Once the hard clauses are satisfied, the solver exclusively flips variables that **do not break any hard clauses** (safe flips). It optimizes the soft cost by breaking/making soft unit clauses.

### 5. Algorithm Variants
* **ProbSAT (`ProbSAT.java`)**: The baseline SLS solver. Selects variables strictly based on the ProbSAT weighted probabilities.
* **Prototype (`Prototype.java`)**: The experimental solver. It operates identically to ProbSAT during the hard phase but introduces a **Tabu Search mechanism** during the soft optimization phase. Variables have a `softTabuTenure` (number of flips they are forbidden from being flipped again), which helps escape local optima. It also features an **Aspiration Criterion**: a tabu move is allowed if it leads to a new global best soft cost.

---

## How to Run the Solver

Ensure you have Java compiled. The main entry point is `org.solver.Main`.

### Compilation
```bash
javac -d target/classes src/org/solver/*.java
```

### Execution
Run the solver on a `.cnf` or `.wcnf` file using the following command structure:
```bash
java -cp target/classes org.solver.Main <path-to-file> [options]
```

**Options:**
* `--algo <probsat|prototype>`: Choose the solver variant (default: `probsat`).
* `--timeout <ms>`: Set the timeout limit in milliseconds (default: `5000`).
* `--seed <long>`: Set the random seed for the XOR-shift PRNG (default: `System.nanoTime()`).
* `--cb <double>`: Set the break-cost constant `cb` (default: `2.85`).
* `--eps <double>`: Set the epsilon value for polynomial weight calculation (default: `1.0`).
* `--weight-mode <poly|exp>`: Choose the probability distribution function (default: `poly`).
* `--luby-unit <long>`: Set the multiplier for the Luby restart sequence budget (default: `1000`).
* `--tabu-tenure <int>`: Set the Tabu tenure length (only applies to `--algo prototype`) (default: `10`).

**Example:**
```bash
java -cp target/classes org.solver.Main instance.wcnf --algo prototype --timeout 10000 --cb 1.83 --eps 0.12 --tabu-tenure 26
```

---

## Hyperparameter Tuning (`tune.py`)

The project includes an Optuna-based Python script to automate the discovery of optimal hyperparameters.

1. **Setup**: Install required packages: `pip install optuna pandas`.
2. **Configuration**: Place your training instances in a directory named `train_instances/`.
3. **Run**: Execute `python tune.py`.

**How it works:**
The script searches the hyperparameter space (e.g., `cb` $\in [1.5, 4.5]$, `tabu_tenure` $\in [2, 50]$) over a multi-seed evaluation. If a configuration fails to find a hard-feasible solution within the timeout, it receives a massive penalty cost ($1,000,000,000$). Otherwise, it attempts to minimize the average soft cost. The full history of the Optuna trials is exported to `tuning_trials.csv` for analysis.

---

## Benchmarking (`benchmark.py`)

The benchmark script evaluates the tuned algorithms against a holdout test set to ensure statistical validity.

1. **Configuration**: Place your testing instances in a directory named `test_instances/`. Ensure the tuned hyperparameters for both `probsat` and `prototype` are updated in the script variables `PROBSAT_PARAMS` and `PROTOTYPE_PARAMS`.
2. **Run**: Execute `python benchmark.py`.

**How it works:**
The script runs both algorithms against every instance in the `test_instances/` directory using multiple seeds (e.g., 1337, 2024, 9999). 

### Data Output
Every execution of the Java program appends its results to a file named `raw_benchmark_runs.csv`. This file acts as a centralized database for your experiments. It logs:
* Instance name, algorithm, seed.
* Formula characteristics (num variables, num clauses).
* Exact search and preprocessing times (ms).
* Total flips and flips per millisecond.
* Total unsatisfied hard clauses and final best soft cost.
* Memory usage (Peak JVM MB).