import os
import subprocess
import time

# ==========================================
# CONFIGURATION
# ==========================================
TEST_DIR = "test_instances"
TIMEOUT_MS = 600000  # Time given to the solver per run
SEEDS = [1337, 2024, 9999]  # 5 seeds for statistical significance

PROBSAT_PARAMS = {
    "cb": 1.8370534425692047,
    "eps": 0.1285930330663328,
    "weight_mode": "poly",
    "luby_unit": 8127
}

PROTOTYPE_PARAMS = {
    "cb": 1.8370534425692047,
    "eps": 0.1285930330663328,
    "weight_mode": "poly",
    "luby_unit": 8127,
    "tabu_tenure": 26
}

def run_solver(instance_path, algo, seed, params):
    cmd = [
        "java", "-cp", "target/classes", "org.solver.Main", instance_path,
        "--algo", algo,
        "--timeout", str(TIMEOUT_MS),
        "--seed", str(seed),
        "--cb", str(params["cb"]),
        "--eps", str(params["eps"]),
        "--weight-mode", params["weight_mode"],
        "--luby-unit", str(params["luby_unit"])
    ]
    
    if algo == "prototype":
        cmd.extend(["--tabu-tenure", str(params["tabu_tenure"])])

    # OS-level timeout buffer (Java handles its own internal timer)
    os_timeout = (TIMEOUT_MS / 1000.0) + 3.0

    try:
        subprocess.run(cmd, capture_output=True, text=True, timeout=os_timeout)
    except subprocess.TimeoutExpired:
        print(f"  [!] OS Timeout triggered for {algo} on seed {seed}. Java failed to halt.")
    except Exception as e:
        print(f"  [!] Execution error: {e}")

if __name__ == "__main__":
    if not os.path.exists(TEST_DIR):
        print(f"Error: Directory '{TEST_DIR}' not found.")
        exit(1)

    instances = [f for f in os.listdir(TEST_DIR) if f.endswith(".wcnf")]
    if not instances:
        print(f"No .wcnf files found in {TEST_DIR}.")
        exit(1)

    total_runs = len(instances) * len(SEEDS) * 2
    current_run = 0

    print(f"Starting Phase 3 Benchmarking... Total runs scheduled: {total_runs}\n")
    start_time = time.time()

    for instance in instances:
        instance_path = os.path.join(TEST_DIR, instance)
        print(f"Evaluating: {instance}")
        
        for seed in SEEDS:
            # Run baseline ProbSAT
            current_run += 1
            print(f"  [{current_run}/{total_runs}] ProbSAT | Seed: {seed}", end="\r")
            run_solver(instance_path, "probsat", seed, PROBSAT_PARAMS)

            # Run Experimental Prototype
            current_run += 1
            print(f"  [{current_run}/{total_runs}] Prototype | Seed: {seed}", end="\r")
            run_solver(instance_path, "prototype", seed, PROTOTYPE_PARAMS)
            
        print(f"  -> Finished {instance}                            ")

    elapsed = time.time() - start_time
    print(f"\nBenchmarking complete in {elapsed:.2f} seconds.")
    print("Results have been appended to 'raw_benchmark_runs.csv'.")