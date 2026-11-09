import optuna
import subprocess
import os
import re
import pandas as pd

# ==========================================
# CONFIGURATION
# ==========================================
TRAIN_DIR = "train_instances"
ALGO = "prototype" # Change to "probsat" to tune the baseline
TIMEOUT_MS = 5000
SEEDS = [42, 1337] # Multi-seed evaluation for stochastic stability
PENALTY_COST = 1_000_000_000 # Cost assigned if no hard-feasible solution is found

def evaluate_instance(instance_path, cb, eps, weight_mode, luby_unit, tabu_tenure, seed):
    cmd = [
        "java", "-cp", "target/classes", "org.solver.Main", instance_path,
        "--algo", ALGO,
        "--timeout", str(TIMEOUT_MS),
        "--seed", str(seed),
        "--cb", str(cb),
        "--eps", str(eps),
        "--weight-mode", weight_mode,
        "--luby-unit", str(luby_unit),
        "--tabu-tenure", str(tabu_tenure)
    ]
    
    try:
        # Add a 2-second buffer to the OS-level timeout to allow Java to gracefully exit
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=(TIMEOUT_MS / 1000.0) + 2.0)
        output = result.stdout
        
        # Parse the output injected in Phase 1
        if "No hard-feasible assignment found" in output:
            return PENALTY_COST
            
        match = re.search(r"Hard Unsat:\s*(\d+)\s*\|\s*Soft Cost:\s*(\d+)", output)
        if match:
            hard_unsat = int(match.group(1))
            soft_cost = int(match.group(2))
            
            # Reject configurations that violate hard constraints
            if hard_unsat > 0:
                return PENALTY_COST
                
            return soft_cost
            
    except subprocess.TimeoutExpired:
        return PENALTY_COST
    except Exception as e:
        print(f"Subprocess error on {instance_path}: {e}")
        return PENALTY_COST
        
    return PENALTY_COST

def objective(trial):
    # 1. Define the Hyperparameter Search Space
    cb = trial.suggest_float("cb", 1.5, 4.5)
    eps = trial.suggest_float("eps", 0.1, 2.0)
    weight_mode = trial.suggest_categorical("weight_mode", ["poly", "exp"])
    luby_unit = trial.suggest_int("luby_unit", 100, 10000, log=True)
    tabu_tenure = trial.suggest_int("tabu_tenure", 2, 50)

    total_cost = 0
    instances = [f for f in os.listdir(TRAIN_DIR) if f.endswith(".cnf") or f.endswith(".wcnf")]
    
    if not instances:
        raise ValueError(f"No instances found in {TRAIN_DIR}/")

    # 2. Evaluate the configuration across all training instances and seeds
    for instance in instances:
        instance_path = os.path.join(TRAIN_DIR, instance)
        instance_cost = 0
        
        for seed in SEEDS:
            cost = evaluate_instance(instance_path, cb, eps, weight_mode, luby_unit, tabu_tenure, seed)
            instance_cost += cost
            
        # Average across seeds for this instance
        total_cost += (instance_cost / len(SEEDS))

    # Optuna minimizes the returned value
    return total_cost

if __name__ == "__main__":
    print(f"Starting Hyperparameter Tuning for {ALGO}...")
    
    # Create an Optuna study
    study = optuna.create_study(direction="minimize", study_name="maxsat_tuning")
    
    # Run 100 trials (adjust based on compute budget)
    study.optimize(objective, n_trials=100)

    print("\n==========================================")
    print("Best Configuration Found:")
    for key, value in study.best_trial.params.items():
        print(f"  --{key.replace('_', '-')}: {value}")
    print(f"Best Objective Value: {study.best_value}")
    print("==========================================")

    # Export trial history to CSV for Phase 5 visualizations
    df = study.trials_dataframe()
    df.to_csv("tuning_trials.csv", index=False)
    print("Saved full trial history to tuning_trials.csv")