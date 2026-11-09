package org.solver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PreprocessorTest {

    @Test
    void emptyFormulaReturnsEmptyResult() {
        int[] formula = {};
        Preprocessor.Result result = Preprocessor.Result.run(formula, 0);

        assertFalse(result.unsat);
        assertEquals(0, result.numClauses);
        assertArrayEquals(new int[]{}, result.simplifiedFormula);
    }

    @Test
    void noUnitClausesLeavesFormulaUnchanged() {
        int[] formula = {1, 2, 0, -1, 3, 0};
        int numVariables = 3;

        Preprocessor.Result result = Preprocessor.Result.run(formula, numVariables);

        assertFalse(result.unsat);
        assertEquals(2, result.numClauses);
        assertArrayEquals(formula, result.simplifiedFormula);
        
        // No variables should be forced
        assertFalse(result.forced[1]);
        assertFalse(result.forced[2]);
        assertFalse(result.forced[3]);
    }

    @Test
    void unitClausePropagatesAndSatisfiesClauses() {
        // Variable 1 is forced TRUE. 
        // Second clause (1 v 2) is satisfied and should be removed entirely.
        int[] formula = {1, 0, 1, 2, 0};
        int numVariables = 2;

        Preprocessor.Result result = Preprocessor.Result.run(formula, numVariables);

        assertFalse(result.unsat);
        assertEquals(0, result.numClauses);
        assertArrayEquals(new int[]{}, result.simplifiedFormula);

        assertTrue(result.forced[1]);
        assertTrue(result.forcedValue[1]);
        assertFalse(result.forced[2]); // Variable 2 is not forced
    }

    @Test
    void unitClausePropagatesAndShrinksClauses() {
        // Variable 1 is forced TRUE.
        // Second clause (-1 v 2) has -1 which becomes FALSE, so it shrinks to (2).
        // Since it's now a unit clause, the preprocessor eagerly forces 2=TRUE and satisfies it.
        int[] formula = {1, 0, -1, 2, 0};
        int numVariables = 2;

        Preprocessor.Result result = Preprocessor.Result.run(formula, numVariables);

        assertFalse(result.unsat);
        assertEquals(0, result.numClauses); // Clause was fully satisfied by eager propagation
        assertArrayEquals(new int[]{}, result.simplifiedFormula);

        assertTrue(result.forced[1]);
        assertTrue(result.forcedValue[1]);
        assertTrue(result.forced[2]); // 2 is now forced!
        assertTrue(result.forcedValue[2]);
    }

    @Test
    void negativeUnitClausePropagatesCorrectly() {
        // Variable 1 is forced FALSE.
        // Second clause (1 v -2) shrinks to (-2).
        // Eager propagation forces 2=FALSE and satisfies the clause.
        int[] formula = {-1, 0, 1, -2, 0};
        int numVariables = 2;

        Preprocessor.Result result = Preprocessor.Result.run(formula, numVariables);

        assertFalse(result.unsat);
        assertEquals(0, result.numClauses);
        assertArrayEquals(new int[]{}, result.simplifiedFormula);

        assertTrue(result.forced[1]);
        assertFalse(result.forcedValue[1]); // Value should be FALSE
        
        assertTrue(result.forced[2]);
        assertFalse(result.forcedValue[2]); // -2 is forced TRUE -> var 2 is FALSE
    }

    @Test
    void cascadingUnitClausesAreResolved() {
        // 1=T -> forces 2=T (from -1 v 2) -> forces 3=T (from -2 v 3)
        int[] formula = {1, 0, -1, 2, 0, -2, 3, 0};
        int numVariables = 3;

        Preprocessor.Result result = Preprocessor.Result.run(formula, numVariables);

        assertFalse(result.unsat);
        assertEquals(0, result.numClauses); // All clauses are satisfied
        assertArrayEquals(new int[]{}, result.simplifiedFormula);

        assertTrue(result.forced[1]);
        assertTrue(result.forcedValue[1]);
        assertTrue(result.forced[2]);
        assertTrue(result.forcedValue[2]);
        assertTrue(result.forced[3]);
        assertTrue(result.forcedValue[3]);
    }

    @Test
    void conflictingUnitClausesResultInUnsat() {
        // 1=T and 1=F are both unit clauses.
        int[] formula = {1, 0, -1, 0};
        int numVariables = 1;

        Preprocessor.Result result = Preprocessor.Result.run(formula, numVariables);

        assertTrue(result.unsat);
    }

    @Test
    void emptyClauseResultsInUnsat() {
        // The second clause is empty (0 immediately follows 0).
        int[] formula = {1, 2, 0, 0, 3, 0};
        int numVariables = 3;

        Preprocessor.Result result = Preprocessor.Result.run(formula, numVariables);

        assertTrue(result.unsat);
    }

    @Test
    void propagationLeadingToEmptyClauseResultsInUnsat() {
        // 1=T. 
        // Second clause (-1 v 2) shrinks to (2) -> 2=T.
        // Third clause (-1 v -2) shrinks to (-2) -> 2=F.
        // Conflict!
        int[] formula = {1, 0, -1, 2, 0, -1, -2, 0};
        int numVariables = 2;

        Preprocessor.Result result = Preprocessor.Result.run(formula, numVariables);

        assertTrue(result.unsat);
    }
    
    @Test
    void complexSimplificationRetainsUnresolvedClauses() {
        // 1 is forced TRUE. 
        // 2 is forced FALSE.
        // Clause 3: (1 v 3) -> satisfied (removed).
        // Clause 4: (2 v 4 v 5) -> 2 is FALSE, shrinks to (4 v 5).
        // Clause 5: (-4 v 6) -> unchanged.
        int[] formula = {1, 0, -2, 0, 1, 3, 0, 2, 4, 5, 0, -4, 6, 0};
        int numVariables = 6;
        
        Preprocessor.Result result = Preprocessor.Result.run(formula, numVariables);
        
        assertFalse(result.unsat);
        assertEquals(2, result.numClauses);
        assertArrayEquals(new int[]{4, 5, 0, -4, 6, 0}, result.simplifiedFormula);
        
        assertTrue(result.forced[1]);
        assertTrue(result.forcedValue[1]);
        assertTrue(result.forced[2]);
        assertFalse(result.forcedValue[2]);
        
        // 3, 4, 5, 6 remain unforced
        assertFalse(result.forced[3]);
        assertFalse(result.forced[4]);
        assertFalse(result.forced[5]);
        assertFalse(result.forced[6]);
    }
}