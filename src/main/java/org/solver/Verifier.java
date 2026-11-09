package org.solver;


public class Verifier {


    public static boolean verifyCNF(
            boolean[] assignment,
            int[] formula) {

        validateInputs(assignment, formula);
        boolean clauseHasLiteral = false;
        boolean clauseSatisfied = false;

        for (int literal : formula) {

            if (literal == 0) {
                //vlausehasLiteral means we are at 0
                if (!clauseHasLiteral) {
                    System.out.println(
                            "Verification FAILED: formula contains an empty clause.");
                    return false;
                }
                if (!clauseSatisfied) {
                    System.out.println(
                            "Verification FAILED: an unsatisfied clause was found.");
                    return false;
                }

                clauseHasLiteral = false;
                clauseSatisfied = false;
                continue;
            }

            int varID = Math.abs(literal);
            validateVariableID(varID, assignment.length);

            clauseHasLiteral = true;
            boolean variableValue = assignment[varID];

            /*
             * Positive literal:
             *   x  is true when variableValue == true
             * Negative literal:
             *   -x is true when variableValue == false
             */
            boolean literalSatisfied = literal > 0? variableValue: !variableValue;
            if (literalSatisfied) {
                clauseSatisfied = true;
            }
        }

        /*
         * A valid formula should end with a 0 delimiter.
         *
         * If there are literals left here, the final clause was not
         * terminated correctly. Reject it rather than silently accepting
         * malformed input.
         */
        if (clauseHasLiteral) {
            System.out.println("Verification FAILED: final clause is missing a 0 delimiter.");
            return false;
        }

        System.out.println("Verification PASSED: all CNF clauses are satisfied.");
        return true;
    }

    /**
     * Verifies a WCNF formula after it has been split into hard and soft
     * components.
     *
     * Hard clauses must all be satisfied.
     * Soft clauses may be violated.
     *
     * @param assignment assignment[varID] gives variable value
     * @param hardFormula hard clauses, literals separated by 0
     * @param softFormula soft clauses, literals separated by 0
     * @param softWeights one weight for every soft clause
     * @return true if all hard clauses are satisfied
     */
    public static boolean verifyWCNF(boolean[] assignment,int[] hardFormula,int[] softFormula,long[] softWeights) {

        if (assignment == null) {
            throw new IllegalArgumentException("Assignment cannot be null.");
        }

        if (hardFormula == null) {
            throw new IllegalArgumentException("Hard formula cannot be null.");
        }

        //First verify the hard part.
         
        if (!verifyCNF(assignment, hardFormula)) {
            System.out.println(
                    "WCNF verification FAILED: hard clauses are not satisfied.");
            return false;
        }

        /*
         * Then calculate the soft cost.
         *
         * Soft clauses are allowed to be false.
         */
        long softCost = calculateSoftCost(assignment,softFormula,softWeights);

        System.out.println("WCNF verification PASSED: all hard clauses are satisfied.");
        System.out.println("Verified soft cost = " + softCost);
        return true;
    }

    /**
     * Calculates the total weight of violated soft clauses.
     *
     * A satisfied soft clause contributes 0.
     * A violated soft clause contributes its weight.
     */
    public static long calculateSoftCost(
            boolean[] assignment,
            int[] softFormula,
            long[] softWeights) {

        if (assignment == null) { throw new IllegalArgumentException( "Assignment cannot be null.");}

        if (softFormula == null) { return 0L;}

        if (softWeights == null) {throw new IllegalArgumentException("Soft weights cannot be null when a soft formula exists.");}

        int expectedClauses = countClauses(softFormula);

        if (expectedClauses != softWeights.length) {throw new IllegalArgumentException("Number of soft clauses (" + expectedClauses +") does not match number of soft weights (" + softWeights.length + ").");}

        long cost = 0L;

        int clauseIndex = 0;
        boolean clauseHasLiteral = false;
        boolean clauseSatisfied = false;

        for (int literal : softFormula) {
            if (literal == 0) {
                
                //Empty soft clauses are always violated.
                if (!clauseHasLiteral) {
                    cost = safeAdd(cost,softWeights[clauseIndex]);

                    clauseIndex++;
                    clauseHasLiteral = false;
                    clauseSatisfied = false;
                    continue;
                }

                if (!clauseSatisfied) {cost = safeAdd(cost,softWeights[clauseIndex]);
                }
                clauseIndex++;

                clauseHasLiteral = false;
                clauseSatisfied = false;
                continue;
            }

            int varID = Math.abs(literal);
            validateVariableID(varID, assignment.length);
            clauseHasLiteral = true;
            boolean variableValue = assignment[varID];

            boolean literalSatisfied =literal > 0? variableValue: !variableValue;

            if (literalSatisfied) {
                clauseSatisfied = true;
            }
        }

        /*
         * Reject malformed soft formula.
         */
        if (clauseHasLiteral) {throw new IllegalArgumentException("Soft formula final clause is missing a 0 delimiter.");
        }

        if (clauseIndex != softWeights.length) {throw new IllegalArgumentException("Soft clause/weight count mismatch.");
        }

        return cost;
    }

    /**
     * Convenience method for a plain CNF formula when the caller only
     * wants a boolean result.
     */
    public static boolean verify(boolean[] assignment,int[] flatFormula) {
        return verifyCNF(assignment, flatFormula);
    }

    /**
     * Counts clauses 
     */
    private static int countClauses(int[] formula) {
        int count = 0;
        for (int literal : formula) {
            if (literal == 0) {
                count++;
            }
        }

        return count;
    }

    /**
     * Validates assignment and formula references.
     */
    private static void validateInputs(boolean[] assignment,int[] formula) {

        if (assignment == null) {throw new IllegalArgumentException("Assignment cannot be null.");
        }

        if (formula == null) {throw new IllegalArgumentException("Formula cannot be null.");
        }
    }

    /**
     * Checks that a variable ID can be safely indexed into the assignment.
     */
    private static void validateVariableID(
            int varID,
            int assignmentLength) {

        if (varID <= 0) {throw new IllegalArgumentException("Variable ID must be greater than zero.");
        }

        if (varID >= assignmentLength) {throw new IllegalArgumentException("Variable ID " + varID +" exceeds assignment size " + assignmentLength + ".");}
    }

    /**
     * Adds two non-negative costs while detecting long overflow.
     */
    private static long safeAdd(long a, long b) {

        if (b < 0) {
            throw new IllegalArgumentException(
                    "Soft clause weights must not be negative.");
        }

        if (Long.MAX_VALUE - a < b) {
            throw new ArithmeticException(
                    "Soft cost overflow.");
        }

        return a + b;
    }
}