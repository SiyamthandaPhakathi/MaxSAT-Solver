package org.solver;

import java.util.Arrays;

/**
 * Relax soft clauses with size 2 or greater into a hard clause and a soft unary clause. Therefore every soft clause is treated as a unit clause with 
 * O(1) break cost lookup
 * 
 * For a soft clause (l1 v l2 v ... v lk) with weight w and k >= 2:
 *   hard clause:      (l1 v l2 v ... v lk v y)   
 *   soft unit clause: (-y)                       
 *
 * If the original literals can satisfy the clause, y can be false and nothing is
 * paid. If they can't, y must be true to keep the hard clause satisfied, which
 * violates (-y) and costs w 
 *
 * Soft unit clauses and empty soft clauses are passed through
 * unchanged
 */

public final class SoftClauseRelaxer {
    private SoftClauseRelaxer() {}

    //OOriginal hard clauses plus the new relaxed clause seperated by 0
    public static int[] relaxedHardFormula;

    // Unary soft clauses
    public static int[] relaxedSoftFormula;

    // Original order
    public static long[] relaxedSoftWeights;

    //Orginal number of variables plus new variables
    public static int relaxedNumVariables;

    public static void relax(int[] hardFormula, int[] softFormula, long[] softWeights, int numVariables) {
        if (softFormula == null || softFormula.length == 0) {
            relaxedHardFormula = hardFormula != null ? hardFormula : new int[0];
            relaxedSoftFormula = new int[0];
            relaxedSoftWeights = softWeights != null ? softWeights : new long[0];
            relaxedNumVariables = numVariables;
            return;
        }
        if (softWeights == null) {
            throw new IllegalArgumentException("Soft weights cannot be null when a soft formula exists.");
        }

        int numSoftClauses = countClauses(softFormula);
        if (numSoftClauses != softWeights.length) {
            throw new IllegalArgumentException(
                "Soft clause count (" + numSoftClauses + ") does not match soft weight count (" + softWeights.length + ").");
        }

        int hardLen = hardFormula != null ? hardFormula.length : 0;

        // Worst case is that every soft clause gets relaxed, adding blocking literal + 0 per clause
        // on top of copying its original literals across into the hard side.
        int[] newHardBuffer = new int[hardLen + softFormula.length + numSoftClauses];
        int hardSize = 0;
        if (hardFormula != null) {
            System.arraycopy(hardFormula, 0, newHardBuffer, 0, hardLen);
            hardSize = hardLen;
        }

        // Worst case every soft clause becomes a unit clause
        int[] newSoftBuffer = new int[softFormula.length + numSoftClauses];
        int softSize = 0;
        long[] newWeights = new long[numSoftClauses];

        int nextVarID = numVariables;
        int clauseStart = 0;
        int clauseIndex = 0;

        for (int i = 0; i < softFormula.length; i++) {
            if (softFormula[i] == 0) {
                int clauseLen = i - clauseStart;

                if (clauseLen <= 1) {
                    //Empty or unary soft clause are passed through as we can't relax them
                    for (int j = clauseStart; j < i; j++) {
                        newSoftBuffer[softSize++] = softFormula[j];
                    }
                    newSoftBuffer[softSize++] = 0;
                } else {
                    int y = ++nextVarID;

                    //Hard clause original literals plus the blocking literal y
                    System.arraycopy(softFormula, clauseStart, newHardBuffer, hardSize, clauseLen);
                    hardSize += clauseLen;
                    newHardBuffer[hardSize++] = y;
                    newHardBuffer[hardSize++] = 0;

                    //Soft unary clause (-y), same weight as the original clause.
                    newSoftBuffer[softSize++] = -y;
                    newSoftBuffer[softSize++] = 0;
                }

                newWeights[clauseIndex] = softWeights[clauseIndex];
                clauseIndex++;
                clauseStart = i + 1;
            }
        }

        relaxedHardFormula = Arrays.copyOf(newHardBuffer, hardSize);
        relaxedSoftFormula = Arrays.copyOf(newSoftBuffer, softSize);
        relaxedSoftWeights = newWeights;
        relaxedNumVariables = nextVarID;
    }

    private static int countClauses(int[] formula) {
        int count = 0;
        for (int literal : formula) {
            if (literal == 0) count++;
        }
        return count;
    }
}