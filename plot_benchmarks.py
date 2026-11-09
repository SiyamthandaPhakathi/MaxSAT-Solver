"""""
Plot MaxSAT benchmark results.
 
Usage:
    python plot_benchmarks.py benchmarks.csv [-o plots] [--show]
 
Requires: pandas, matplotlib, numpy
"""
import argparse
import os
import sys
 
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
 
LONG_MAX = 2**63 - 1
 
 
# --------------------------------------------------------------------------
# Loading
# --------------------------------------------------------------------------
def load(path: str) -> pd.DataFrame:
    """Read the CSV, repairing rows where flips_per_ms used a decimal comma
    (e.g. '1,09' -> 1.09), which produces one extra field per row."""
    with open(path, encoding="utf-8") as f:
        lines = [ln.strip() for ln in f if ln.strip()]
    header = lines[0].split(",")
    n = len(header)
    idx = header.index("flips_per_ms")
 
    rows, bad = [], 0
    for ln in lines[1:]:
        parts = ln.split(",")
        if len(parts) == n + 1:  # decimal comma in flips_per_ms
            parts = parts[:idx] + [parts[idx] + "." + parts[idx + 1]] + parts[idx + 2:]
        if len(parts) != n:
            bad += 1
            continue
        rows.append(parts)
    if bad:
        print(f"warning: skipped {bad} malformed row(s)", file=sys.stderr)
 
    df = pd.DataFrame(rows, columns=header)
    for c in header:
        if c not in ("instance", "algo"):
            df[c] = pd.to_numeric(df[c], errors="coerce")
 
    df["short"] = (
        df["instance"]
        .str.replace(r"^converted_", "", regex=True)
        .str.replace(r"\.wcnf$", "", regex=True)
    )
    df["total_ms"] = df["parse_ms"] + df["prep_ms"] + df["search_ms"]
    df["feasible"] = df["hard_unsat"] == 0
    df["soft_cost_valid"] = df["soft_cost"].where(
        df["feasible"] & (df["soft_cost"] < LONG_MAX)
    )
    return df
 
 
# --------------------------------------------------------------------------
# Helpers
# --------------------------------------------------------------------------
def grouped_barh(df, value, title, xlabel, out, log=False, dropna=True):
    """Horizontal grouped bars: one group per instance, one bar per algo,
    mean over seeds with min-max error bars."""
    g = df.groupby(["short", "algo"])[value]
    mean = g.mean().unstack("algo")
    lo = mean - g.min().unstack("algo")
    hi = g.max().unstack("algo") - mean
    if dropna:
        mean = mean.dropna(how="all")
        lo, hi = lo.loc[mean.index], hi.loc[mean.index]
    if mean.empty:
        print(f"skip {out}: no data")
        return
 
    algos = list(mean.columns)
    n_inst, n_alg = len(mean), len(algos)
    h = 0.8 / n_alg
    fig, ax = plt.subplots(figsize=(10, max(3.5, 0.45 * n_inst * n_alg + 1.5)))
    y = np.arange(n_inst)
    for i, a in enumerate(algos):
        ax.barh(
            y + i * h - 0.4 + h / 2, mean[a].fillna(0), h, label=a,
            xerr=[lo[a].fillna(0), hi[a].fillna(0)], capsize=2,
        )
    ax.set_yticks(y)
    ax.set_yticklabels(mean.index, fontsize=8)
    ax.invert_yaxis()
    if log:
        ax.set_xscale("log")
    ax.set_xlabel(xlabel)
    ax.set_title(title)
    ax.grid(axis="x", alpha=0.3)
    if n_alg > 1:
        ax.legend()
    save(fig, out)
 
 
def save(fig, path):
    fig.tight_layout()
    fig.savefig(path, dpi=150)
    print("wrote", path)
 
 
# --------------------------------------------------------------------------
# Plots
# --------------------------------------------------------------------------
def plot_time_breakdown(df, outdir):
    """Stacked horizontal bars: parse / prep / search (mean over seeds)."""
    m = df.groupby(["short", "algo"])[["parse_ms", "prep_ms", "search_ms"]].mean()
    labels = [f"{s} [{a}]" if df["algo"].nunique() > 1 else s for s, a in m.index]
    fig, ax = plt.subplots(figsize=(10, max(3.5, 0.4 * len(m) + 1.5)))
    left = np.zeros(len(m))
    for col, name in [("parse_ms", "parse"), ("prep_ms", "prep"), ("search_ms", "search")]:
        ax.barh(labels, m[col].values / 1000, left=left, label=name)
        left += m[col].values / 1000
    ax.invert_yaxis()
    ax.set_xlabel("seconds")
    ax.set_title("Time breakdown (mean over seeds)")
    ax.legend()
    ax.tick_params(axis="y", labelsize=8)
    ax.grid(axis="x", alpha=0.3)
    save(fig, os.path.join(outdir, "01_time_breakdown.png"))
 
 
def plot_feasibility(df, outdir):
    """Hard clauses left unsatisfied (0 = feasible), log scale, per seed."""
    d = df.copy()
    fig, ax = plt.subplots(figsize=(10, max(3.5, 0.45 * d["short"].nunique() + 1.5)))
    insts = list(dict.fromkeys(d["short"]))
    ypos = {s: i for i, s in enumerate(insts)}
    for (algo, seed), sub in d.groupby(["algo", "seed"]):
        x = sub["hard_unsat"].clip(lower=0.5)  # 0 shown at 0.5 on log axis
        ax.scatter(x, sub["short"].map(ypos), label=f"{algo} seed={int(seed)}",
                   s=45, alpha=0.8)
    ax.axvline(0.5, color="green", ls="--", alpha=0.5)
    ax.text(0.55, -0.6, "0 = feasible", color="green", fontsize=8)
    ax.set_xscale("log")
    ax.set_yticks(range(len(insts)))
    ax.set_yticklabels(insts, fontsize=8)
    ax.invert_yaxis()
    ax.set_xlabel("unsatisfied hard clauses (log)")
    ax.set_title("Hard-clause violations at end of search")
    ax.grid(axis="x", alpha=0.3)
    ax.legend(fontsize=8)
    save(fig, os.path.join(outdir, "02_hard_unsat.png"))
 
 
def plot_scaling(df, outdir):
    """Instance size vs. runtime (log-log)."""
    fig, axes = plt.subplots(1, 2, figsize=(12, 4.5))
    for algo, sub in df.groupby("algo"):
        axes[0].scatter(sub["num_clauses"], sub["total_ms"] / 1000, label=algo, alpha=0.8)
        axes[1].scatter(sub["num_clauses"], sub["search_ms"] / 1000, label=algo, alpha=0.8)
    for ax, t in zip(axes, ["Total time", "Search time"]):
        ax.set_xscale("log")
        ax.set_yscale("log")
        ax.set_xlabel("clauses")
        ax.set_ylabel("seconds")
        ax.set_title(f"{t} vs. instance size")
        ax.grid(alpha=0.3, which="both")
        ax.legend()
    save(fig, os.path.join(outdir, "06_scaling.png"))
 
 
def plot_time_to_best(df, outdir):
    """Time (or step) at which the best solution was found vs. search time."""
    fig, ax = plt.subplots(figsize=(6.5, 5))
    for algo, sub in df.groupby("algo"):
        ax.scatter(sub["search_ms"], sub["best_cost_step"], label=algo, alpha=0.8)
    lim = [1, max(df["search_ms"].max(), df["best_cost_step"].max()) * 1.5]
    ax.plot(lim, lim, "k--", alpha=0.3, label="y = x")
    ax.set_xscale("log")
    ax.set_yscale("symlog", linthresh=1)
    ax.set_xlabel("search_ms")
    ax.set_ylabel("best_cost_step")
    ax.set_title("When was the best solution found?")
    ax.grid(alpha=0.3, which="both")
    ax.legend()
    save(fig, os.path.join(outdir, "08_time_to_best.png"))
 
 
def plot_seed_variance(df, outdir):
    """Compare seeds directly (only meaningful with >= 2 seeds)."""
    if df["seed"].nunique() < 2:
        return
    piv = df.pivot_table(index="short", columns="seed", values="search_ms", aggfunc="mean")
    fig, ax = plt.subplots(figsize=(10, max(3.5, 0.4 * len(piv) + 1.5)))
    piv.plot.barh(ax=ax)
    ax.invert_yaxis()
    ax.set_xlabel("search_ms")
    ax.set_title("Search time per seed")
    ax.tick_params(axis="y", labelsize=8)
    ax.grid(axis="x", alpha=0.3)
    save(fig, os.path.join(outdir, "07_seed_variance.png"))
 
 
# --------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv")
    ap.add_argument("-o", "--outdir", default="plots")
    ap.add_argument("--show", action="store_true", help="also open windows")
    args = ap.parse_args()
 
    os.makedirs(args.outdir, exist_ok=True)
    df = load(args.csv)
    print(f"{len(df)} runs, {df['short'].nunique()} instances, "
          f"algos={sorted(df['algo'].unique())}, seeds={sorted(df['seed'].dropna().astype(int).unique())}")
 
    plot_time_breakdown(df, args.outdir)
    plot_feasibility(df, args.outdir)
    grouped_barh(df[df["feasible"]], "soft_cost_valid",
                 "Soft cost (feasible runs only; lower is better)", "soft cost",
                 os.path.join(args.outdir, "03_soft_cost.png"), log=True)
    grouped_barh(df, "flips_per_ms", "Search throughput", "flips / ms (log)",
                 os.path.join(args.outdir, "04_flips_per_ms.png"), log=True)
    grouped_barh(df, "peak_jvm_mb", "Peak JVM memory", "MB",
                 os.path.join(args.outdir, "05_peak_memory.png"))
    plot_scaling(df, args.outdir)
    plot_seed_variance(df, args.outdir)
    plot_time_to_best(df, args.outdir)
 
    if args.show:
        plt.show()
 
 
if __name__ == "__main__":
    main()
 