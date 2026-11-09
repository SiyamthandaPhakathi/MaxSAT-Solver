package org.solver;


public final class ProbSATWeights {
    private ProbSATWeights(){}

    public  static  double EPS = 1.0;
    public  static  double CB = 2.85; // Constant for break -- CAN BE TUNED 
    public static boolean usePolyWeight = true;
    /**
     * Checks whether the clauses single true literal is the literal of variable 
     * @param formula Bitpacked formula 
     * @param variable variable
     * @param clauseStart beginning index of clause
     * @return boolean - true if literal is variable and literal is true else false
     */
    private static boolean trueLiteralBelongsTo(int[] formula, int variable, int clauseStart){
        int i = clauseStart;
        while (formula[i] != 0){
            int lit = formula[i];
            if (BitPacking.getVarId(lit) == variable && BitPacking.isSAT(lit)){
                return true;
            }
            i++;
        }
        return false;
    }

    /**
     * Computes the break count ie. the number of variables that become unsat if variable is flipped
     * @param formula bitpacked formula
     * @param variable variable to be flipped
     * @param occurrences occurrences of the variable 
     * @param trueLitCount true literal count 
     * @param weightByClauseStart weights at the clause start
     * @return the number of breaks after flipping
     */
    public static double computeBreak(int[] formula, int variable, int[][] occurrences, int[] trueLitCount, long[] weightByClauseStart){
        double breakCost = 0;
        for (int clauseStart : occurrences[variable]){
            if (trueLitCount[clauseStart] == 1 && trueLiteralBelongsTo(formula, variable, clauseStart)){
                breakCost += (weightByClauseStart == null) ? 1.0 : weightByClauseStart[clauseStart];
            }
        }
        return breakCost;
    }

    /**
     * Polynomial scoring function
     * reference: https://github.com/adrianopolus/probSAT/blob/master/probSAT.c
     * @param breakCost number of "breaks"
     * @return "Score"
     */
    public static double weightPoly(double breakCost){
        return Math.pow(1.0 / (EPS + breakCost), CB);
    }

    /**
     * Exponential scoring function
     * reference: https://github.com/adrianopolus/probSAT/blob/master/probSAT.c
     * @param breakCost number of breaks
     * @return "Score"
     */
    public static double weightExp(double breakCost){
        return Math.pow(1/CB, breakCost );
    }

    /**
     * Computes weights/score for each variable in the clause
     * 
     * @param formula bitpackedformula 
     * @param start beginning index of clause
     * @param occurrences occurrences of literals
     * @param trueLitCount Number of true literals
     * @param weightByClauseStart weights at the clause start
     * @return
     */
    public static double[] weightsForClause(int[] formula, int start, int[][] occurrences, int[] trueLitCount, long[] weightByClauseStart){
        int end = start;
        while (formula[end] != 0){
            end++;
        }
        int len = end - start;
        double[] weights = new double[len];
        for (int i = 0; i < len; i++){
            int lit = formula[start + i];
            int var = BitPacking.getVarId(lit);
            // Can be changed to either polynomial or exponential function
            weights[i] = getWeight(computeBreak(formula, var, occurrences, trueLitCount, weightByClauseStart)); 
        }
        return weights;
    }

    public static double getWeight(double breakCost) {
        return usePolyWeight ? weightPoly(breakCost) : weightExp(breakCost);
    }
}
