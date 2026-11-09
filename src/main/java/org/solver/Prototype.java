package org.solver;

import java.util.Arrays;

/**
 * Occurrence lists (which clauses each variable appears in) are stored as flat
 * CSR-style arrays (hardOccFlat/hardOccStart, softOccFlat/softOccStart) rather
 * than jagged int[][] arrays, so each variable's occurrence positions are
 * contiguous in memory and require only one array dereference instead of two.
 */
public class Prototype {
    public static long bestUnsatHardCount = Long.MAX_VALUE;
    public static long bestSoftCost = Long.MAX_VALUE;
    public static boolean[] bestAssignment;

    private static long rngState = 123456789L; // Seed for xorshift PRNG (faster than java.util.Random)
    public static  long FLIP_BUDGET_UNIT = 1000; // Can tune according to numVars
    private static final int TIME_CHECK_INTERVAL = 512; // Poll the clock every N flips, not every flip

    //Hard clause bookkeeping (indexed by clause index, 0..numHardClauses-1)
    private static int[] hardOccFlat;    // flat occurrence positions, grouped contiguously per variable
    private static int[] hardOccStart;   // hardOccStart[v]..hardOccStart[v+1]-1 gives variable v's positions in hardOccFlat
    private static int[] clauseIndexOfHardLit;   // hardLiterals position -> clause index
    private static int[] trueLitCountHard;       // indexed by clause index
    private static int[] unsatHardClauses;       // stores clause indices
    private static int[] posInUnsatHard;         // indexed by clause index
    private static int unsatHardCount;
    private static int numHardClauses;

    //mMetrics for benchmarking
    public static long hardFlips = 0;
    public static long softFlips = 0;
    public static int hardRestarts = 0;
    public static long hardTimeMs = 0;
    public static long bestCostStep = 0;
    public static long initialSoftCost = -1;

    //Soft clause bookkeeping (indexed by clause index, 0..numSoftClauses-1)
    private static int[] softOccFlat;
    private static int[] softOccStart;
    private static int[] clauseIndexOfSoftLit;
    private static int[] trueLitCountSoft;
    private static int[] unsatSoftClauses;
    private static int[] posInUnsatSoft;
    private static int unsatSoftCount;
    private static int numSoftClauses;
    private static long currentSoftCost;

    // Tabu list
    private static int[] softFlipStepOf; // keeps track of last step that each variable was flipped
    private static int softFlipStep; // current soft flip counter
    public  static int softTabuTenure = 10; // Number of iterations that flip is forbidden

    public static void setSeed(long seed) {
        rngState = (seed == 0) ? 1 : seed; // xorshift requires non-zero seed
    }

    public static void solve(long timeoutMillis) {
        reset();
        int[] hardLiterals = BitPacking.hardLiterals;
        int[] hardClauseStart = BitPacking.hardClauseStart;
        if (hardLiterals == null || hardClauseStart == null) {
            throw new IllegalStateException("BitPacking has not been loaded.");
        }
        
        numHardClauses = hardClauseStart.length - 1;
        buildOccurrences(true, BitPacking.solverNumVariables, hardLiterals);
        clauseIndexOfHardLit = buildClauseIndexOf(hardLiterals.length, hardClauseStart);
        
        long deadline = System.currentTimeMillis() + timeoutMillis;
        long hardStart = System.currentTimeMillis();
        int tryIndex = 1;

        // Restart based probSAT for hard clauses
        while (System.currentTimeMillis() < deadline) {
            randomizeFreeAssignments();
            rebuildHardBookkeeping(hardLiterals, hardClauseStart);
            long flipBudget = Luby.flipBudget(tryIndex, FLIP_BUDGET_UNIT);
            for (long f = 0; f < flipBudget; f++) {
                if (f % TIME_CHECK_INTERVAL == 0 && System.currentTimeMillis() >= deadline) {
                    recordHardProgress();
                    hardTimeMs = System.currentTimeMillis() - hardStart;
                    hardRestarts = tryIndex;
                    return;
                }
                if (unsatHardCount == 0) break;
                
                hardFlips++;
                int clauseIndex = pickRandomUnsat(unsatHardClauses, unsatHardCount);
                doHardFlip(clauseIndex, hardLiterals, hardClauseStart);
                recordHardProgress();
            }
            if (unsatHardCount == 0) break;
            tryIndex++;
        }
        
        hardTimeMs = System.currentTimeMillis() - hardStart;
        hardRestarts = tryIndex;

        if (bestUnsatHardCount != 0) {
            // Could not make it hard-feasible in the provided time
            bestAssignment = null;
            return;
        }
        bestUnsatHardCount = 0;

        // Reduce total soft cost without breaking hard clauses
        int[] softLiterals = BitPacking.softLiterals;
        int[] softClauseStart = BitPacking.softClauseStart;
        if (softLiterals == null || softLiterals.length == 0) {
            bestSoftCost = 0;
            recordAssignment(0);
            return;
        }
        
        numSoftClauses = softClauseStart.length - 1;
        buildOccurrences(false, BitPacking.solverNumVariables, softLiterals);
        clauseIndexOfSoftLit = buildClauseIndexOf(softLiterals.length, softClauseStart);
        rebuildSoftBookkeeping(softLiterals, softClauseStart);
        
        initialSoftCost = currentSoftCost;
        recordAssignment(currentSoftCost); // feasible baseline, whatever its cost
        
        while (unsatSoftCount != 0) {
            if (softFlips % TIME_CHECK_INTERVAL == 0 && System.currentTimeMillis() >= deadline) {
                return;
            }
            softFlips++;
            int clauseIndex = pickRandomUnsat(unsatSoftClauses, unsatSoftCount);
            doSoftFlip(clauseIndex, softLiterals, softClauseStart);
        }
        // every soft clause satisfied
    }

    /**
     * Executes a single flip during the hard clause satisfaction phase.
     * Selects a variable from the chosen unsatisfied clause using roulette-wheel
     * selection weighted by how many other hard clauses the flip would break.
     */
    private static void doHardFlip(int clauseIndex, int[] hardLiterals, int[] hardClauseStart) {
        int start = hardClauseStart[clauseIndex];
        int end = hardClauseStart[clauseIndex + 1];
        int[] candidates = varsInRangeExcludingLocked(hardLiterals, start, end);
        if (candidates.length == 0) return; // clause is entirely locked variables; shouldn't happen if preprocessing was correct
        
        double[] weights = new double[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            weights[i] = ProbSATWeights.getWeight(computeHardBreakCost(candidates[i]));
        }
        int chosen = RouletteSelector.select(candidates, weights, nextRand());
        applyFlip(chosen);
    }

    /**
     * Executes a single flip during the soft clause optimization phase.
     * Excludes any variable that would break a hard constraint, then selects among
     * the safe variables based on the weighted cost of soft clauses they'd break.
     */
    private static void doSoftFlip(int clauseIndex, int[] softLiterals, int[] softClauseStart) {
        int start = softClauseStart[clauseIndex];
        int end = softClauseStart[clauseIndex + 1];
        int[] rawCandidates = varsInRangeExcludingLocked(softLiterals, start, end);
        
        // Keep only variables whose flip will not break a hard clause.
        int[] safe = new int[rawCandidates.length];
        int n = 0;
        for (int v : rawCandidates) {
            if (computeHardBreakCost(v) == 0) {
                safe[n++] = v;
            }
        }
        if (n == 0) {
            // Every candidate would break some hard clause; nothing safe to do here.
            return;
        }
        
        // Split the safe set, not tabu or tabu but passing aspiration
        int[] allowed = new int[n];
        long[] breakCosts = new long[n];
        int m = 0;
        int oldest = -1;
        int oldestStep = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            int v = safe[i];
            long brk = computeSoftBreakCost(v);
            boolean tabu = softFlipStep - softFlipStepOf[v] < softTabuTenure; // if variable was last flipped in less time than tenure -> TABU
            if (!tabu || currentSoftCost + brk - computeSoftMakeGain(v) < bestSoftCost) { // Variable excluded if tabu OR if flip gets better solution
                breakCosts[m] = brk;
                allowed[m++] = v;
            }
            if (softFlipStepOf[v] < oldestStep) {
                oldestStep = softFlipStepOf[v];
                oldest = v;
            }
        }
        
        int chosen;
        if (m == 0) { // If everything tabu, pick stalest
            chosen = oldest;
        } else {
            double[] weights = new double[m];
            for (int i = 0; i < m; i++) {
                weights[i] = ProbSATWeights.getWeight(breakCosts[i]);
            }
            chosen = RouletteSelector.select(Arrays.copyOf(allowed, m), weights, nextRand());
        }
        
        applyFlip(chosen);
        softFlipStepOf[chosen] = ++softFlipStep;
        
        if (currentSoftCost < bestSoftCost) {
            recordAssignment(currentSoftCost);
            bestCostStep = softFlips;
        }
    }

    /**
     * Number of hard clauses that would become unsatisfied if this variable were flipped.
     */
    private static int computeHardBreakCost(int variable) {
        int breakCost = 0;
        int[] literals = BitPacking.hardLiterals;
        int s = hardOccStart[variable];
        int e = hardOccStart[variable + 1];
        for (int idx = s; idx < e; idx++) {
            int pos = hardOccFlat[idx];
            int clause = clauseIndexOfHardLit[pos];
            if (trueLitCountHard[clause] == 1 && BitPacking.isSAT(literals[pos])) {
                breakCost++;
            }
        }
        return breakCost;
    }

    /**
     * Total weight of soft clauses that would become unsatisfied if this variable were flipped.
     */
    private static long computeSoftBreakCost(int variable) {
        long breakCost = 0;
        int[] literals = BitPacking.softLiterals;
        int s = softOccStart[variable];
        int e = softOccStart[variable + 1];
        for (int idx = s; idx < e; idx++) {
            int pos = softOccFlat[idx];
            int clause = clauseIndexOfSoftLit[pos];
            if (trueLitCountSoft[clause] == 1 && BitPacking.isSAT(literals[pos])) {
                breakCost += BitPacking.softWeights[clause];
            }
        }
        return breakCost;
    }

    /**
     * Total weight of current-unsat soft clauses that would become satisfied if this variable were flipped.
     */
    private static long computeSoftMakeGain(int variable) {
        long gain = 0;
        int[] literals = BitPacking.softLiterals;
        int s = softOccStart[variable];
        int e = softOccStart[variable + 1];
        for (int idx = s; idx < e; idx++) {
            int pos = softOccFlat[idx];
            int clause = clauseIndexOfSoftLit[pos];
            if (trueLitCountSoft[clause] == 0 && !BitPacking.isSAT(literals[pos])) {
                gain += BitPacking.softWeights[clause];
            }
        }
        return gain;
    }

    /**
     * Flips a variable's state, then updates hard/soft bookkeeping.
     */
    private static void applyFlip(int variable) {
        boolean[] hardWasTrue = captureWasTrue(hardOccFlat, hardOccStart, BitPacking.hardLiterals, variable);
        boolean[] softWasTrue = captureWasTrue(softOccFlat, softOccStart, BitPacking.softLiterals, variable);

        BitPacking.flipVar(variable);

        if (hardOccFlat != null) {
            updateHardBookkeeping(variable, hardWasTrue);
        }
        if (softOccFlat != null) {
            updateSoftBookkeeping(variable, softWasTrue);
        }
    }

    /**
     * Records whether the variable's literal is currently satisfied for each clause it
     * occurs in. Null if the occurrence structure hasn't been built (e.g. no soft clauses).
     */
    private static boolean[] captureWasTrue(int[] occFlat, int[] occStart, int[] literals, int variable) {
        if (occFlat == null) return null;
        int s = occStart[variable];
        int e = occStart[variable + 1];
        boolean[] wasTrue = new boolean[e - s];
        for (int i = s; i < e; i++) {
            wasTrue[i - s] = BitPacking.isSAT(literals[occFlat[i]]);
        }
        return wasTrue;
    }

    private static void updateHardBookkeeping(int variable, boolean[] wasTrue) {
        int[] literals = BitPacking.hardLiterals;
        int s = hardOccStart[variable];
        int e = hardOccStart[variable + 1];
        for (int i = s; i < e; i++) {
            int pos = hardOccFlat[i];
            boolean isTrueNow = BitPacking.isSAT(literals[pos]);
            int clause = clauseIndexOfHardLit[pos];
            int wtIdx = i - s;
            if (wasTrue[wtIdx] && !isTrueNow) {
                trueLitCountHard[clause]--;
                if (trueLitCountHard[clause] == 0) markUnsatHard(clause);
            } else if (!wasTrue[wtIdx] && isTrueNow) {
                if (trueLitCountHard[clause] == 0) markSatHard(clause);
                trueLitCountHard[clause]++;
            }
        }
    }

    private static void updateSoftBookkeeping(int variable, boolean[] wasTrue) {
        int[] literals = BitPacking.softLiterals;
        int s = softOccStart[variable];
        int e = softOccStart[variable + 1];
        for (int i = s; i < e; i++) {
            int pos = softOccFlat[i];
            boolean isTrueNow = BitPacking.isSAT(literals[pos]);
            int clause = clauseIndexOfSoftLit[pos];
            int wtIdx = i - s;
            if (wasTrue[wtIdx] && !isTrueNow) {
                trueLitCountSoft[clause]--;
                if (trueLitCountSoft[clause] == 0) {
                    markUnsatSoft(clause);
                    currentSoftCost += BitPacking.softWeights[clause];
                }
            } else if (!wasTrue[wtIdx] && isTrueNow) {
                if (trueLitCountSoft[clause] == 0) {
                    markSatSoft(clause);
                    currentSoftCost -= BitPacking.softWeights[clause];
                }
                trueLitCountSoft[clause]++;
            }
        }
    }

    /**
     * Builds a flat, CSR-style occurrence structure: for each variable v, its occurrence
     * positions in `literals` are stored contiguously at occFlat[occStart[v] .. occStart[v+1]-1].
     *
     * @param isHard true to populate hardOccFlat/hardOccStart, false for soft
     * @param numVars number of variables in the formula
     * @param literals the bit-packed literals to build occurrences over
     */
    private static void buildOccurrences(boolean isHard, int numVars, int[] literals) {
        int[] count = new int[numVars + 2];
        for (int lit : literals) {
            count[BitPacking.getVarId(lit)]++;
        }

        int[] start = new int[numVars + 2];
        for (int v = 1; v <= numVars; v++) {
            start[v + 1] = start[v] + count[v];
        }

        int[] cursor = Arrays.copyOf(start, start.length);
        int[] flat = new int[literals.length];
        for (int i = 0; i < literals.length; i++) {
            int v = BitPacking.getVarId(literals[i]);
            flat[cursor[v]++] = i;
        }

        if (isHard) {
            hardOccStart = start;
            hardOccFlat = flat;
        } else {
            softOccStart = start;
            softOccFlat = flat;
        }
    }

    /**
     * Maps each literal position to its owning clause index, for O(1) clause lookup
     * given a literal position.
     */
    private static int[] buildClauseIndexOf(int literalCount, int[] clauseStart) {
        int[] clauseOf = new int[literalCount];
        int numClauses = clauseStart.length - 1;
        for (int c = 0; c < numClauses; c++) {
            int start = clauseStart[c];
            int end = clauseStart[c + 1];
            for (int i = start; i < end; i++) {
                clauseOf[i] = c;
            }
        }
        return clauseOf;
    }

    private static void rebuildHardBookkeeping(int[] hardLiterals, int[] hardClauseStart) {
        trueLitCountHard = new int[numHardClauses];
        posInUnsatHard = new int[numHardClauses];
        Arrays.fill(posInUnsatHard, -1);
        unsatHardClauses = new int[numHardClauses];
        unsatHardCount = 0;

        for (int c = 0; c < numHardClauses; c++) {
            int start = hardClauseStart[c];
            int end = hardClauseStart[c + 1];
            int count = 0;
            for (int i = start; i < end; i++) {
                if (BitPacking.isSAT(hardLiterals[i])) count++;
            }
            trueLitCountHard[c] = count;
            if (count == 0) markUnsatHard(c);
        }
    }

    private static void rebuildSoftBookkeeping(int[] softLiterals, int[] softClauseStart) {
        trueLitCountSoft = new int[numSoftClauses];
        posInUnsatSoft = new int[numSoftClauses];
        Arrays.fill(posInUnsatSoft, -1);
        unsatSoftClauses = new int[numSoftClauses];
        unsatSoftCount = 0;
        currentSoftCost = 0;

        for (int c = 0; c < numSoftClauses; c++) {
            int start = softClauseStart[c];
            int end = softClauseStart[c + 1];
            int count = 0;
            for (int i = start; i < end; i++) {
                if (BitPacking.isSAT(softLiterals[i])) count++;
            }
            trueLitCountSoft[c] = count;
            if (count == 0) {
                markUnsatSoft(c);
                currentSoftCost += BitPacking.softWeights[c];
            }
        }

        softFlipStepOf = new int[BitPacking.solverNumVariables+1];
        Arrays.fill(softFlipStepOf, Integer.MIN_VALUE/2);
        softFlipStep = 0;
    }

    private static void markUnsatHard(int clauseIndex) {
        unsatHardClauses[unsatHardCount] = clauseIndex;
        posInUnsatHard[clauseIndex] = unsatHardCount;
        unsatHardCount++;
    }

    private static void markSatHard(int clauseIndex) {
        int pos = posInUnsatHard[clauseIndex];
        int last = unsatHardClauses[unsatHardCount - 1];
        unsatHardClauses[pos] = last;
        posInUnsatHard[last] = pos;
        unsatHardCount--;
        posInUnsatHard[clauseIndex] = -1;
    }

    private static void markUnsatSoft(int clauseIndex) {
        unsatSoftClauses[unsatSoftCount] = clauseIndex;
        posInUnsatSoft[clauseIndex] = unsatSoftCount;
        unsatSoftCount++;
    }

    private static void markSatSoft(int clauseIndex) {
        int pos = posInUnsatSoft[clauseIndex];
        int last = unsatSoftClauses[unsatSoftCount - 1];
        unsatSoftClauses[pos] = last;
        posInUnsatSoft[last] = pos;
        unsatSoftCount--;
        posInUnsatSoft[clauseIndex] = -1;
    }

    private static int pickRandomUnsat(int[] unsatArr, int count) {
        return unsatArr[nextInt(count)];
    }

    /**
     * Extracts all variable IDs from literals[start, end), excluding any locked variables.
     */
    private static int[] varsInRangeExcludingLocked(int[] literals, int start, int end) {
        int len = end - start;
        int[] buf = new int[len];
        int n = 0;
        for (int i = start; i < end; i++) {
            int var = BitPacking.getVarId(literals[i]);
            if (!BitPacking.isLocked(var)) buf[n++] = var;
        }
        return Arrays.copyOf(buf, n);
    }

    /**
     * Randomises every free (unlocked) variable's assignment ahead of a restart.
     */
    private static void randomizeFreeAssignments() {
        for (int v = 1; v <= BitPacking.solverNumVariables; v++) {
            if (BitPacking.isLocked(v)) continue;
            boolean targetState;
            if (BitPacking.isBlockingVariable(v)) {
                //Relaced y is always false
                targetState = false;
            } else {
                //Regular variables are randomised
                targetState = (nextRand() & 1) == 1;
            }
            if (BitPacking.getState(v) != targetState) {
                BitPacking.flipVar(v);
            }
        }
    }

    private static void recordHardProgress() {
        if (unsatHardCount < bestUnsatHardCount) {
            bestUnsatHardCount = unsatHardCount;
            if (unsatHardCount == 0) {
                bestAssignment = cloneAssignment();
            }
        }
    }

    private static void recordAssignment(long softCost) {
        bestSoftCost = softCost;
        bestAssignment = cloneAssignment();
    }

    /**
     * Deep-copies the current assignment, translated from dense solver IDs back to
     * original DIMACS variable IDs.
     */
    private static boolean[] cloneAssignment() {
        boolean[] clone = new boolean[BitPacking.totalOriginalVariables + 1];
        for (int denseID = 1; denseID <= BitPacking.solverNumVariables; denseID++) {
            int originalID = BitPacking.getOriginalVariableID(denseID);
            clone[originalID] = BitPacking.getState(denseID);
        }
        return clone;
    }

    private static void reset() {
        bestUnsatHardCount = Long.MAX_VALUE;
        bestSoftCost = Long.MAX_VALUE;
        bestAssignment = null;

        hardOccFlat = null;
        hardOccStart = null;
        clauseIndexOfHardLit = null;
        trueLitCountHard = null;
        unsatHardClauses = null;
        posInUnsatHard = null;
        unsatHardCount = 0;
        numHardClauses = 0;

        softOccFlat = null;
        softOccStart = null;
        clauseIndexOfSoftLit = null;
        trueLitCountSoft = null;
        unsatSoftClauses = null;
        posInUnsatSoft = null;
        unsatSoftCount = 0;
        numSoftClauses = 0;
        currentSoftCost = 0;

        softFlipStepOf = null;
        softFlipStep= 0;
    }

    // XOR-shift for randomisation (cheaper alternative to java.util.Random)
    private static long nextRand() {
        rngState ^= (rngState << 13);
        rngState ^= (rngState >>> 7);
        rngState ^= (rngState << 17);
        return rngState;
    }

    // Returns a random integer in the range [0, bound)
    private static int nextInt(int bound) {
        return (int) ((nextRand() >>> 1) % bound);
    }

}