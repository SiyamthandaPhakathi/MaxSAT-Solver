package org.solver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <cnf-file> [options]");
            System.err.println("Options: --timeout <ms> --seed <long> --cb <double> --eps <double> --weight-mode <poly|exp> --luby-unit <long> --tabu-tenure <int> --algo <probsat|prototype>");
            return;
        }

        // Default Hyperparameters
        long timeoutMillis = 5000L;
        String filename = null;
        String algo = "probsat";
        long seed = System.nanoTime();
        double cb = 2.85;
        double eps = 1.0;
        String weightMode = "poly";
        long lubyUnit = 1000;
        int tabuTenure = 10;

        // CLI Parser
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--timeout": timeoutMillis = Long.parseLong(args[++i]); break;
                case "--seed": seed = Long.parseLong(args[++i]); break;
                case "--cb": cb = Double.parseDouble(args[++i]); break;
                case "--eps": eps = Double.parseDouble(args[++i]); break;
                case "--weight-mode": weightMode = args[++i].toLowerCase(); break;
                case "--luby-unit": lubyUnit = Long.parseLong(args[++i]); break;
                case "--tabu-tenure": tabuTenure = Integer.parseInt(args[++i]); break;
                case "--algo": algo = args[++i].toLowerCase(); break;
                default: 
                    if (!args[i].startsWith("--")) filename = args[i];
                    break;
            }
        }

        if (filename == null) {
            System.err.println("Error: No CNF/WCNF file provided.");
            return;
        }

        // Apply Hyperparameters
        ProbSATWeights.CB = cb;
        ProbSATWeights.EPS = eps;
        ProbSATWeights.usePolyWeight = weightMode.equals("poly");
        ProbSAT.FLIP_BUDGET_UNIT = lubyUnit;
        Prototype.FLIP_BUDGET_UNIT = lubyUnit;
        Prototype.softTabuTenure = tabuTenure;
        ProbSAT.setSeed(seed);
        Prototype.setSeed(seed);

        try {
            // Parsing
            long parseStart = System.currentTimeMillis();
            int[] flatFormula = Parser.parse(filename);
            BitPacking.split(flatFormula);
            long parseTimeMs = System.currentTimeMillis() - parseStart;

            //Preprocessing & Data Loading
            long prepStart = System.currentTimeMillis();
            BitPacking.SplitView view = BitPacking.getSplitView();
            SoftClauseRelaxer.relax(view.hardFormula, view.softFormula, view.softWeights, Parser.numVariables);
            Preprocessor.Result pre = Preprocessor.Result.run(
                SoftClauseRelaxer.relaxedHardFormula, SoftClauseRelaxer.relaxedNumVariables);
            
            if (pre.unsat) {
                System.out.println("Formula is UNSAT after preprocessing.");
                return;
            }
            
            BitPacking.loadFromPreprocessed(
                SoftClauseRelaxer.relaxedNumVariables, pre,
                SoftClauseRelaxer.relaxedSoftFormula, SoftClauseRelaxer.relaxedSoftWeights);
            long prepTimeMs = System.currentTimeMillis() - prepStart;

            // Search
            System.out.println("Running " + algo + " with seed " + seed);
            long searchStart = System.currentTimeMillis();
            
            long bestSoftCost = -1;
            long hardUnsatCount = -1;
            long totalFlips = 0;
            long bestCostStep = 0;
            
            if (algo.equals("prototype")) {
                Prototype.solve(timeoutMillis);
                bestSoftCost = Prototype.bestSoftCost;
                hardUnsatCount = Prototype.bestUnsatHardCount;
                totalFlips = Prototype.hardFlips + Prototype.softFlips;
                bestCostStep = Prototype.bestCostStep;
            } else {
                ProbSAT.solve(timeoutMillis);
                bestSoftCost = ProbSAT.bestSoftCost;
                hardUnsatCount = ProbSAT.bestUnsatHardCount;
                totalFlips = ProbSAT.hardFlips + ProbSAT.softFlips;
                bestCostStep = ProbSAT.bestCostStep;
            }
            
            long searchDurationMs = System.currentTimeMillis() - searchStart;
            double flipsPerMs = searchDurationMs > 0 ? (double) totalFlips / searchDurationMs : totalFlips;
            
            long peakJvmMemMb = 0;
            for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
                peakJvmMemMb += pool.getPeakUsage().getUsed();
            }
            peakJvmMemMb /= (1024 * 1024);

            // Export to CSV
            File csv = new File("raw_benchmark_runs.csv");
            boolean isNew = !csv.exists() || csv.length() == 0;
            try (PrintWriter writer = new PrintWriter(new FileWriter(csv, true))) {
                if (isNew) {
                    writer.println("instance,algo,seed,num_vars,num_clauses,hard_unsat,soft_cost,parse_ms,prep_ms,search_ms,total_flips,flips_per_ms,best_cost_step,peak_jvm_mb");
                }
                writer.println(String.format("%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.2f,%d,%d",
                    new File(filename).getName(), algo, seed, Parser.numVariables, Parser.numClauses,
                    hardUnsatCount, bestSoftCost, parseTimeMs, prepTimeMs, searchDurationMs,
                    totalFlips, flipsPerMs, bestCostStep, peakJvmMemMb
                ));
            }

            System.out.println("Unsat hard clauses: " + hardUnsatCount);
            System.out.println("Soft cost: " + bestSoftCost);
            System.out.println("Total flips: " + totalFlips);
            System.out.printf("Flips/ms: %.2f \n",flipsPerMs );
            
            boolean[] best = algo.equals("prototype") ? Prototype.bestAssignment : ProbSAT.bestAssignment;
            if (best != null) {
                boolean ok = Verifier.verifyWCNF(best, view.hardFormula, view.softFormula, view.softWeights);
                System.out.println("Verified: " + ok);
            } else {
                System.out.println("No hard-feasible assignment found within timeout.");
            }
            

        } catch (Exception e) {
            System.err.println("Execution failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}