package org.solver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProbSATWeightsTest {

    @BeforeEach
    void setUp() {
        // Initialize BitPacking state needed for trueLiteralBelongsTo
        // Assumes BitPacking.varData is accessible or we can initialize it
        BitPacking.varData = new int[10];
        
        // Setup initial variable states for tests
        // Var 1 = TRUE
        // Var 2 = FALSE
        // Var 3 = TRUE
        BitPacking.setState(1, true);
        BitPacking.setState(2, false);
        BitPacking.setState(3, true);
    }

    // =====================================================================
    // Mathematical Scoring Function Tests
    // =====================================================================

    @Test
    void testWeightPoly() {
        double cb = 2.85;
        double eps = 1.0;

        // breakCost = 0
        assertEquals(Math.pow(1.0 / (eps + 0.0), cb), ProbSATWeights.weightPoly(0.0), 1e-9);

        // breakCost = 1
        assertEquals(Math.pow(1.0 / (eps + 1.0), cb), ProbSATWeights.weightPoly(1.0), 1e-9);

        // breakCost = 2.5
        assertEquals(Math.pow(1.0 / (eps + 2.5), cb), ProbSATWeights.weightPoly(2.5), 1e-9);
    }

    @Test
    void testWeightExp() {
        double cb = 2.85;

        // breakCost = 0
        assertEquals(Math.pow(1.0 / cb, 0.0), ProbSATWeights.weightExp(0.0), 1e-9);

        // breakCost = 1
        assertEquals(Math.pow(1.0 / cb, 1.0), ProbSATWeights.weightExp(1.0), 1e-9);

        // breakCost = 2.5
        assertEquals(Math.pow(1.0 / cb, 2.5), ProbSATWeights.weightExp(2.5), 1e-9);
    }

    // =====================================================================
    // computeBreak Tests
    // =====================================================================

    @Test
    void testComputeBreakNoWeights() {
        // clause 0: (1 v 2) -> starts at 0
        // clause 1: (1 v -3) -> starts at 3
        int lit1Pos = (1 << 1);      // Var 1, positive (SAT)
        int lit2Pos = (2 << 1);      // Var 2, positive (UNSAT)
        int lit3Neg = (3 << 1) | 1;  // Var 3, negative (UNSAT)

        int[] formula = {lit1Pos, lit2Pos, 0, lit1Pos, lit3Neg, 0};
        
        int[][] occurrences = new int[5][];
        occurrences[1] = new int[]{0, 3}; // Var 1 occurs at clause start 0 and 3
        
        int[] trueLitCount = new int[6];
        trueLitCount[0] = 1; // Clause 0 has 1 true literal (lit1Pos)
        trueLitCount[3] = 1; // Clause 1 has 1 true literal (lit1Pos)

        // Since weightByClauseStart is null, each break should add 1.0
        double breakCost = ProbSATWeights.computeBreak(formula, 1, occurrences, trueLitCount, null);
        
        assertEquals(2.0, breakCost, 1e-9, "Break cost should be 2.0 (1.0 for each clause where it is the sole true literal)");
    }

    @Test
    void testComputeBreakWithWeights() {
        int lit1Pos = (1 << 1);
        int lit2Pos = (2 << 1);
        int lit3Neg = (3 << 1) | 1;

        int[] formula = {lit1Pos, lit2Pos, 0, lit1Pos, lit3Neg, 0};
        
        int[][] occurrences = new int[5][];
        occurrences[1] = new int[]{0, 3};
        
        int[] trueLitCount = new int[6];
        trueLitCount[0] = 1;
        trueLitCount[3] = 1;

        long[] weightByClauseStart = new long[6];
        weightByClauseStart[0] = 5L;
        weightByClauseStart[3] = 10L;

        double breakCost = ProbSATWeights.computeBreak(formula, 1, occurrences, trueLitCount, weightByClauseStart);
        
        assertEquals(15.0, breakCost, 1e-9, "Break cost should sum the specific clause weights (5 + 10)");
    }

    @Test
    void testComputeBreakIgnoresClausesWithMultipleTrueLiterals() {
        int lit1Pos = (1 << 1); // SAT
        int lit3Pos = (3 << 1); // SAT

        int[] formula = {lit1Pos, lit3Pos, 0};
        
        int[][] occurrences = new int[5][];
        occurrences[1] = new int[]{0};
        
        int[] trueLitCount = new int[6];
        trueLitCount[0] = 2; // Multiple true literals!

        double breakCost = ProbSATWeights.computeBreak(formula, 1, occurrences, trueLitCount, null);
        
        assertEquals(0.0, breakCost, 1e-9, "Break cost should be 0 because the clause has more than 1 true literal");
    }

    @Test
    void testComputeBreakIgnoresWhenTrueLiteralBelongsToOtherVariable() {
        int lit1Pos = (1 << 1); // SAT
        int lit2Pos = (2 << 1); // UNSAT

        int[] formula = {lit1Pos, lit2Pos, 0};
        
        int[][] occurrences = new int[5][];
        occurrences[2] = new int[]{0}; // We are calculating breaks for Var 2
        
        int[] trueLitCount = new int[6];
        trueLitCount[0] = 1; // Only Var 1 is true

        double breakCost = ProbSATWeights.computeBreak(formula, 2, occurrences, trueLitCount, null);
        
        assertEquals(0.0, breakCost, 1e-9, "Break cost should be 0 because the sole true literal belongs to Var 1, not Var 2");
    }

    // =====================================================================
    // weightsForClause Tests
    // =====================================================================

    @Test
    void testWeightsForClause() {
        // Clause: (1 v 2)
        int lit1Pos = (1 << 1); // SAT
        int lit2Pos = (2 << 1); // UNSAT
        int[] formula = {lit1Pos, lit2Pos, 0};

        // We need to set up occurrences to simulate:
        // Var 1 will have a break cost of 1.0 (it breaks this clause)
        // Var 2 will have a break cost of 0.0 (it doesn't break this clause)
        int[][] occurrences = new int[5][];
        occurrences[1] = new int[]{0}; 
        occurrences[2] = new int[]{0};
        
        int[] trueLitCount = new int[6];
        trueLitCount[0] = 1; // lit1Pos is the only true literal

        double[] weights = ProbSATWeights.weightsForClause(formula, 0, occurrences, trueLitCount, null);

        assertNotNull(weights);
        assertEquals(2, weights.length, "Weights array should match the number of literals in the clause");

        // Expected break costs:
        // Var 1 -> breaks 1 clause -> breakCost = 1.0 -> weightPoly(1.0)
        // Var 2 -> breaks 0 clauses -> breakCost = 0.0 -> weightPoly(0.0)
        double expectedWeightVar1 = ProbSATWeights.weightPoly(1.0);
        double expectedWeightVar2 = ProbSATWeights.weightPoly(0.0);

        assertEquals(expectedWeightVar1, weights[0], 1e-9, "Weight for literal 1 is incorrect");
        assertEquals(expectedWeightVar2, weights[1], 1e-9, "Weight for literal 2 is incorrect");
    }
}