package org.solver;
import java.util.*;

/**
 * 
 * Preprocessor that runs before AlgorrithmM to simplify 
 * the formula nad eagerly resolve foced variables. It implements unit propagation and detects unsatisfiable formulas.
 */
public class Preprocessor {
    public static class Result{
        public final int[] simplifiedFormula;
        public final int numClauses;
        public final boolean[] forced;
        public final boolean[] forcedValue;
        public final boolean unsat;

        Result(int[] simplifiedFormula, int numClauses, boolean[] forced, boolean[] forcedValue, boolean unsat){
            this.simplifiedFormula = simplifiedFormula;
            this.numClauses = numClauses;
            this.forced = forced;
            this.forcedValue = forcedValue;
            this.unsat = unsat;
        }

        public static Result run(int[] flatFormula, int numVariables){
            int numClauses = 0;
            for (int lit : flatFormula) if (lit == 0) numClauses++;

            int[] clauseStart = new int[numClauses + 1];
            int[] lits = new int[flatFormula.length - numClauses];
            int[] litClause = new int[lits.length];
            int clauseIndex = 0, position = 0, cursorStart = 0;
            for (int lit : flatFormula){
                if (lit == 0){
                    clauseStart[clauseIndex] = cursorStart;
                    clauseIndex++;
                    cursorStart = position;
                } else{
                    lits[position] = lit;
                    litClause[position] = clauseIndex;
                    position++;
                }
            }
            clauseStart[numClauses] = position;

            int[] clauseLen = new int[numClauses];
            for (int c = 0; c < numClauses; c++) clauseLen[c] = clauseStart[c + 1] - clauseStart[c];

            int litRange = (numVariables + 1) * 2;
            int[] occStart = new int[litRange + 1];
            for (int lit : lits) occStart[litIndex(lit) + 1]++;
            for (int i = 0; i < litRange; i++) occStart[i + 1] += occStart[i];

            int[] occClauses = new int[lits.length];
            int[] cursor = occStart.clone();
            for(int i = 0; i < lits.length; i++){
                int li = litIndex(lits[i]);
                occClauses[cursor[li]++] = litClause[i];
            }

            boolean[] assigned = new boolean[numVariables + 1];
            boolean[] value = new boolean[numVariables + 1];
            boolean[] satisfied = new boolean[numClauses];
            int[] remainingLen = clauseLen.clone();

            int[] stack = new int[numClauses + lits.length + 1];
            int stackPointer = 0;

            for(int c = 0; c < numClauses; c++){
                if (clauseLen[c] == 0) return unsatResult(numVariables);
                if (clauseLen[c] == 1) stack[stackPointer++] = lits[clauseStart[c]];
            }

            while (stackPointer > 0){
                int lit = stack[--stackPointer];
                int varID = Math.abs(lit);
                boolean val = lit > 0;
                if (assigned[varID]){
                    if (value[varID] != val) return unsatResult(numVariables);
                    continue;
                }
                assigned[varID] = true;
                value[varID] = val;

                int positionIndex = litIndex(lit);
                for (int i = occStart[positionIndex]; i < occStart[positionIndex + 1]; i++){
                    satisfied[occClauses[i]] = true;
                }

                // Search for where the literal is false
                int negativeIndex = litIndex(-lit);
                for (int i = occStart[negativeIndex]; i < occStart[negativeIndex + 1]; i++){
                    int c = occClauses[i];
                    if (satisfied[c]) continue;
                    if (--remainingLen[c] == 0) return unsatResult(numVariables);
                    if (remainingLen[c] == 1){
                        for (int j = clauseStart[c]; j < clauseStart[c + 1]; j++){
                            int l = lits[j];
                            if (!assigned[Math.abs(l)]){
                                stack[stackPointer++] = l;
                                break;
                            }
                        }
                    }
                }
            }

            int outClauseCount = 0, outLen = 0;
            for (int c = 0; c < numClauses; c++){
                if (satisfied[c]) continue;
                outClauseCount++;
                for (int j = clauseStart[c]; j < clauseStart[c + 1]; j++){
                    if (!assigned[Math.abs(lits[j])]) outLen++;
                }
                outLen++;
            }

            int[] out = new int[outLen];
            int w = 0;

            for (int c = 0; c < numClauses; c++){
                if(satisfied[c]) continue;
                for (int j = clauseStart[c]; j < clauseStart[c + 1]; j++){
                    if (!assigned[Math.abs(lits[j])]) out[w++] = lits[j];
                }
                out[w++] = 0;
            }

            return new Result(out, outClauseCount, assigned, value, false);
        }

        private static int litIndex(int lit){
            int varID = Math.abs(lit);
            return varID * 2 + (lit > 0 ? 0 : 1);
        }

        private static Result unsatResult(int numVariables){
            return new Result(null, 0, new boolean[numVariables + 1], new boolean[numVariables + 1], true);
        }

    }
}
