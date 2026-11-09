package org.solver;
import java.util.Arrays;
import java.util.Random;
/**
 * Convert the processor output purely to optimise search times
 *  -Use dense variable IDs
 *  -packed 32-bit literals
 *  -Implement clause offsets instead of zero delimiter
 *
 *  literals:
 *  -bits 1-31 = varID
 *  -bit 0 = sign
 *
 *  positive literal:
 *      (varID << 1)
 *  negative literal:
 *      (varID <<1) | 1
 *
 *  Per-variable hot data (state, locked) is packed into a single int per
 *  variable (varData) so that one cache-line fetch serves both fields
 *  instead of scattering them across two separate arrays:
 *
 *      bit 0 = state (true/false)
 *      bit 1 = locked (true/false)
 */


public class BitPacking {

    //Read-Only data use in search

    public static int[] hardLiterals; //Packed hard literals with no delimiters in the array
    /**
     *  Clause offests for hard clauses
     *  Clause c lies between: hardLiterals[hardClauseStart[c]] and hardLiterals[hardClauseStart[c+1]-1]
     *  length = number of hard clauses +1
     */
    public static int[] hardClauseStart;
    public static int[] softLiterals;
    public static int[] softClauseStart;

    /**
     * Weight for every soft clause
     * softWeight[c] corresponds to softLiterals[softClauseStart[c] to softClauseStart[c + 1] - 1]
     */
    public static long[] softWeights;

    //Read-Write to be mutated in search

    /**
     * Packed per-variable data: state (bit 0), locked (bit 1).
     * Break costs are intentionally not tracked here — the search computes
     * them fresh from occurrence lists every time, so a maintained running
     * count here would just be dead weight.
     */
    public static int[] varData;

    private static final int STATE_MASK = 0x1;
    private static final int LOCKED_MASK = 0x2;

    //maps the variable id used by solver to the original variable ID within the solver
    public static int[] originalVariableID;
    public static int totalOriginalVariables;
    //Number of variables used by solver after preprocessing
    public static int solverNumVariables;

    //True for variables made by  the softClauseRelaxer
    public static boolean[] blockingVariable;

    //After preprocessing is it SAT or not
    public static boolean unsat;

    public static final long HARD = -1L;
    public static int[] combinedFormulaSplit;
    public static long[] clauseWeightSplit;


    public static void split(int[] parserOutput){
        if (parserOutput == null) {
            combinedFormulaSplit = null;
            clauseWeightSplit = null;
            return;
        }

        if (!Parser.isWCNF) {
            // Parser's CNF output is already plain literals + 0 delimiters.
            combinedFormulaSplit = parserOutput;
            clauseWeightSplit = new long[Parser.numClauses];
            Arrays.fill(clauseWeightSplit, HARD);
            return;
        }

        int topWeight = Parser.topWeight;

        //Make buffer the size of the output gauranteed to be less than or equal to the original formula
        int[] buffer = new int[parserOutput.length];
        int size = 0;
        //Will be at most as many weights as there are clauses
        long[] weightBuffer = new long[Parser.numClauses];
        int clauseIndex = 0;

        int i = 0;
        while (i < parserOutput.length) {
            int weight = parserOutput[i];
            i++;

            int clauseStart = i;
            while (i < parserOutput.length && parserOutput[i] != 0) {
                i++;
            }
            int clauseEnd = i;
            if (i < parserOutput.length && parserOutput[i] == 0) {
                i++;
            }

            int clauseLen = clauseEnd - clauseStart;
            System.arraycopy(parserOutput, clauseStart, buffer, size, clauseLen);
            size += clauseLen;
            buffer[size++] = 0;

            weightBuffer[clauseIndex++] = (weight == topWeight) ? HARD : weight;
        }

        combinedFormulaSplit = Arrays.copyOf(buffer, size);
        clauseWeightSplit = weightBuffer;
    }

    public static class SplitView {
        public final int[] hardFormula;
        public final int[] softFormula;
        public final long[] softWeights;
        public SplitView(int[] hardFormula, int[] softFormula, long[] softWeights) {
            this.hardFormula = hardFormula;
            this.softFormula = softFormula;
            this.softWeights = softWeights;
        }
    }

    public static SplitView getSplitView(){
        if (combinedFormulaSplit == null){
            return new SplitView(null, null, null);
        }

        int[] hardBufffer = new int[combinedFormulaSplit.length];
        int hardSize = 0;
        int[] softBuffer = new int[combinedFormulaSplit.length];
        int softSize = 0;
        long[] weightBuffer = new long[clauseWeightSplit.length];
        int weightSize = 0;

        int clauseIndex = 0;
        int start = 0;
        for (int i = 0; i < combinedFormulaSplit.length; i++) {
            if (combinedFormulaSplit[i] == 0){
                int len = i - start;
                if (clauseWeightSplit[clauseIndex] == HARD) {
                    System.arraycopy(combinedFormulaSplit, start, hardBufffer, hardSize, len);
                    hardSize += len;
                    hardBufffer[hardSize++] = 0;
                } else {
                    System.arraycopy(combinedFormulaSplit, start, softBuffer, softSize, len);
                    softSize += len;
                    softBuffer[softSize++] = 0;
                    weightBuffer[weightSize++] = clauseWeightSplit[clauseIndex];
                }
                clauseIndex++;
                start = i + 1;
            }
        }
        return new SplitView(Arrays.copyOf(hardBufffer, hardSize), Arrays.copyOf(softBuffer, softSize), Arrays.copyOf(weightBuffer, weightSize));
    }


    //Take processor output and convert to our final data representation
    public static void loadFromPreprocessed(int numVariables, Preprocessor.Result hardResult, int[] softFormula, long[] softWeights){
        if (hardResult.unsat) {
            unsat = true;
            clearSolverData();
            return;
        }
        unsat = false;
        createDataBlocks(numVariables, hardResult.simplifiedFormula, softFormula, softWeights,hardResult.forced, hardResult.forcedValue);
    }

    //Data blocks creaation happens here 
    public static void createDataBlocks(int numVariables, int[] hardFormula, int[] softFormula, long[] incomingSoftWeights,boolean[] forced, boolean[] forcedValue){
        if (numVariables <= 0) {
            throw new IllegalArgumentException("Must have at least one variable");
        }
        if(hardFormula == null && softFormula == null){
            throw new IllegalArgumentException("Must provide at least a hard or soft formula.");
        }

        totalOriginalVariables = numVariables;
        /**
         * Dense variable mapping after preprocessing
         * If variables after preprocessing are 1, 5, 1000 map it to 1,2,3
         *
         */
        int[] denseMap = buildDenseVariableMap(numVariables, hardFormula, softFormula, forced);
        solverNumVariables = countMappedVariables(denseMap);
        originalVariableID = new int[solverNumVariables + 1];
        boolean[] blockingOriginal = identifyBlockingvariables(numVariables,hardFormula,softFormula);

        blockingVariable = new boolean[solverNumVariables +1];

        for (int original = 1; original <= numVariables; original++) {
            int dense = denseMap[original];
            if (dense != 0) {
                originalVariableID[dense] = original;
                blockingVariable[dense] = blockingOriginal[dense];
            }
        }

        varData = new int[solverNumVariables + 1];

        //Pack the hard clauses and store them in a zero free formual
        if (hardFormula != null) {
            int hardClauseCount = countClauses(hardFormula);
            hardClauseStart = new int[hardClauseCount + 1];
            int literalCount = countLiterals(hardFormula);
            hardLiterals = new int[literalCount];
            packFormula(hardFormula, denseMap, hardLiterals, hardClauseStart);
        } else {
            hardLiterals = new int[0];
            hardClauseStart = new int[] {0};
        }

        //Pack the soft clauses
        if (softFormula != null) {
            int softClauseCount = countClauses(softFormula);
            softClauseStart = new int[softClauseCount + 1];
            int literalCount = countLiterals(softFormula);
            softLiterals = new int[literalCount];
            packFormula(softFormula, denseMap, softLiterals, softClauseStart);

            if (incomingSoftWeights == null) {
                softWeights = new long[softClauseCount];
            } else {
                if (incomingSoftWeights.length != softClauseCount) {
                    throw new IllegalArgumentException("Soft weight count does not match soft clause count.");
                }
                softWeights = Arrays.copyOf(incomingSoftWeights, incomingSoftWeights.length);
            }
        } else {
            softLiterals = new int[0];
            softClauseStart = new int[] {0};
            softWeights = new long[0];
        }

        //random assignment
        Random rd = new Random(1);
        for (int varID = 1; varID <= solverNumVariables; varID++) {
            int originalID = originalVariableID[varID];
            if (forced != null && forced[originalID]) {
                setLocked(varID, true);
                setState(varID, forcedValue != null && forcedValue[originalID]);
            }else if(blockingVariable[varID]){
                setState(varID, false);
            }
             else {
                setState(varID, rd.nextBoolean());
            }
        }
    }

    /**
     * Build a map using the preprocessed original literals to a dense continuos sequence of ID's.
     */

    private static int[] buildDenseVariableMap(int numVariables, int[] hardFormula, int[] softFormula, boolean[] forced) {
        int[] denseMap = new int[numVariables + 1];
        int nextDenseID = 1;

        if (hardFormula != null) {
            for (int literal : hardFormula) {
                if (literal == 0) continue;
                int originalID = Math.abs(literal);
                if (originalID <= 0 || originalID > numVariables) {
                    throw new IllegalArgumentException("Variable ID exceeds numVariables: " + originalID);
                }
                if (denseMap[originalID] == 0) {
                    denseMap[originalID] = nextDenseID++;
                }
            }
        }

        if (softFormula != null) {
            for (int literal : softFormula) {
                if (literal == 0) continue;
                int originalID = Math.abs(literal);
                if (originalID <= 0 || originalID > numVariables) {
                    throw new IllegalArgumentException("Variable ID exceeds numVariables: " + originalID);
                }
                if (denseMap[originalID] == 0) {
                    denseMap[originalID] = nextDenseID++;
                }
            }
        }

        if (forced != null) {
            int limit = Math.min(forced.length - 1, numVariables);
            for (int originalID = 1; originalID <= limit; originalID++) {
                if (forced[originalID] && denseMap[originalID] == 0) {
                    denseMap[originalID] = nextDenseID++;
                }
            }
        }
        return denseMap;
    }

    private static boolean[] identifyBlockingvariables(int numVariables, int[] hardFormula, int[] softFormula){
        boolean[] positiveInHard = new boolean[numVariables + 1];
        boolean[] blocking = new boolean[numVariables + 1];

        //Find positive vars in the hard formula
        if (hardFormula != null) {
            for (int literal : hardFormula) {
                if (literal > 0) {
                    int variable = literal;

                    if (variable <= numVariables) {
                        positiveInHard[variable] = true;
                    }
                }
            }
        }

        //Relaxed blocking variable will appear as negative unary clause (-y)
        if (softFormula != null) {
            int clauseStart = 0;

            for (int i = 0; i <= softFormula.length; i++) {

                if (i == softFormula.length || softFormula[i] == 0) {

                    int clauseLength = i - clauseStart;

                    if (clauseLength == 1) {
                        int literal = softFormula[clauseStart];

                        if (literal < 0) {
                            int variable = -literal;

                            if (variable <= numVariables
                                    && positiveInHard[variable]) {
                                blocking[variable] = true;
                            }
                        }
                    }

                    clauseStart = i + 1;
                }
            }
        }

        return blocking;
    }

    private static int countMappedVariables(int[] denseMap) {
        int count = 0;
        for (int i = 1; i < denseMap.length; i++) {
            if (denseMap[i] != 0) {
                count++;
            }
        }
        return count;
    }


    /**
     * take the a raw formula and remove the 0 delimiters, swap to our new dense IDs and bit pack the literal with ID and sign
     */
    private static void packFormula(int[] source, int[] denseMap, int[] destination, int[] clauseStart) {
        int destinationIndex = 0;
        int clauseIndex = 0;
        clauseStart[0] = 0;

        for (int literal : source) {
            if (literal == 0) {
                clauseIndex++;
                clauseStart[clauseIndex] = destinationIndex;
                continue;
            }

            int originalID = Math.abs(literal);
            int denseID = denseMap[originalID];
            if (denseID == 0) {
                throw new IllegalStateException("Variable was not included in dense mapping: " + originalID);
            }

            int sign = literal < 0 ? 1 : 0;
            destination[destinationIndex++] = (denseID << 1) | sign;
        }
    }

    private static int countClauses(int[] formula) {
        if (formula == null || formula.length == 0) return 0;
        int count = 0;
        for (int literal : formula) {
            if (literal == 0) count++;
        }
        return count;
    }

    private static int countLiterals(int[] formula) {
        if (formula == null) return 0;
        int count = 0;
        for (int literal : formula) {
            if (literal != 0) count++;
        }
        return count;
    }

    public static boolean getState(int varID) {
        return (varData[varID] & STATE_MASK) != 0;
    }

    public static void setState(int varID, boolean value) {
        if (value) {
            varData[varID] |= STATE_MASK;
        } else {
            varData[varID] &= ~STATE_MASK;
        }
    }

    public static void flipVar(int varID) {
        varData[varID] ^= STATE_MASK;
    }

    private static void setLocked(int varID, boolean value) {
        if (value) {
            varData[varID] |= LOCKED_MASK;
        } else {
            varData[varID] &= ~LOCKED_MASK;
        }
    }

    public static int getVarId(int bitPackedVar) {
        return bitPackedVar >> 1;
    }

    public static int getSign(int bitPackedVar) {
        return bitPackedVar & 1;
    }

    public static boolean isSAT(int bitPackedVar) {
        int varID = bitPackedVar >> 1;
        int sign = bitPackedVar & 1;
        return getState(varID) != (sign != 0);
    }

    public static boolean isLocked(int varID) {
        return varData != null && (varData[varID] & LOCKED_MASK) != 0;
    }

    public static boolean isBlockingVariable(int varID) {
        return blockingVariable != null
                && varID > 0
                && varID < blockingVariable.length
                && blockingVariable[varID];
    }

    public static int getOriginalVariableID(int denseVariableID) {
        return originalVariableID[denseVariableID];
    }

    //clear all static objects in the event program is ran in the same jvm
    private static void clearSolverData() {
        solverNumVariables = 0;
        hardLiterals = null;
        hardClauseStart = null;
        softLiterals = null;
        softClauseStart = null;
        softWeights = null;
        varData = null;
        originalVariableID = null;
        blockingVariable = null;

    }

}