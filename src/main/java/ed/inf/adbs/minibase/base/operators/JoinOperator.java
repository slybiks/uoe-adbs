package ed.inf.adbs.minibase.base.operators;

import ed.inf.adbs.minibase.Utils;
import ed.inf.adbs.minibase.base.*;
import ed.inf.adbs.minibase.evaluation.QueryPlanner;

import java.util.Collections;
import java.util.*;

/**
 * The JoinOperator represents the relational algebra operator used in the generation of a left deep join tree.
 * The operator combines the results of its two child operators using join predicates to produce a unique tuple set.
 */
public class JoinOperator extends Operator {
    private final Operator leftChildOperator;
    private final Operator rightChildOperator;

    private Tuple outerChildTuple;
    private Tuple innerChildTuple;

    private List<ComparisonAtom> joinPredicates = null;
    private List<RelationalAtom> leftChildRelationalAtoms = null;
    private RelationalAtom rightChildRelationalAtom = null;

    /**
     * Constructs a JoinOperator with a left and right child operator.
     *
     * @param leftChildOperator  the left child operator for the join.
     * @param rightChildOperator the right child operator for the join.
     * @return a JoinOperator instance with the specified left and right child operators.
     */
    public JoinOperator(Operator leftChildOperator, Operator rightChildOperator) {
        this.leftChildOperator = leftChildOperator;
        this.rightChildOperator = rightChildOperator;
        this.setChildTuples();
    }

    /**
     * Retrieves the next tuple by scanning through the tuples in the outer child operator and merging them with tuples in the inner child operator,
     * provided that the join condition predicates are satisfied.
     * For each tuple in the outer child operator, the inner relational atom is fully scanned to determine if the join condition is satisfied and the tuples can be merged.
     * If a match is found, the combined tuple is returned.
     * If there are no more tuples to merge, the method returns null.
     *
     * @return the next tuple, or null if there are no more tuples to merge.
     */
    @Override
    public Tuple getNextTuple() {
        while (outerChildTuple != null) {

            if ((innerChildTuple = rightChildOperator.getNextTuple()) == null) {
                rightChildOperator.reset();
                innerChildTuple = rightChildOperator.getNextTuple();
                outerChildTuple = leftChildOperator.getNextTuple();
            }

            if (outerChildTuple != null && satisfiesJoinConditionPredicatesOverRelations()) {
                return new Tuple(outerChildTuple.getRelationalTerms(), innerChildTuple.getRelationalTerms());
            }

        }

        return null;
    }

    /**
     * Resets the state of the operator by resetting both the left and right child operators.
     */
    @Override
    public void reset() {
        leftChildOperator.reset();
        rightChildOperator.reset();
    }

    /**
     * Returns the list of constants that substitute the given variable in the outer child tuple for the left relational atoms.
     *
     * @param term the term to be substituted.
     * @return the list of constants that substitute the given variable.
     */
    private List<Constant> getVariableSubstitutionInTupleForLeftRelationalAtoms(final Term term) {
        return (List<Constant>) Utils.matchTermsToValuesInTuple(outerChildTuple, term, leftChildRelationalAtoms)
                .orElse(Collections.emptyList());
    }

    /**
     * Returns the constant value that matches the given variable term in the inner child tuple's right relational atom.
     * If no match is found, returns null.
     *
     * @param term the variable term to match against the inner child tuple's right relational atom.
     * @return the constant value that matches the variable term, or null if no match is found.
     */
    private Constant getVariableSubstitutionInTupleForRightRelationalAtom(final Term term) {
        List<Constant> constants = (List<Constant>) Utils.matchTermsToValuesInTuple(innerChildTuple, term, Collections.singletonList(rightChildRelationalAtom))
                .orElse(Collections.emptyList());
        if (constants.isEmpty()) {
            return null;
        }
        return constants.get(0);
    }

    /**
     * Replaces any variables in the given comparison atom with their corresponding values in the current
     * tuples of the left and right child operators.
     *
     * @param comparisonAtom the comparison atom to be resolved.
     * @return a new comparison atom with all variables replaced by their corresponding values.
     */
    private ComparisonAtom resolveVariableSubstitutionsInComparisonAtom(final ComparisonAtom comparisonAtom) {

        Constant term1Sub = getResolvedTerm(comparisonAtom.getTerm1());
        Constant term2Sub = getResolvedTerm(comparisonAtom.getTerm2());

        return new ComparisonAtom(term1Sub, term2Sub, comparisonAtom.getOp());
    }

    /**
     * Resolves variable substitutions in a comparison atom and returns a constant value.
     *
     * @param term the comparison atom term for which the variable substitutions need to be resolved.
     * @return a constant from the resolved variable substitutions.
     * @throws IllegalArgumentException if the term is neither a Constant nor a Variable, or if valid substitutions were not found for the term on either side of the comparison expression, or if conflicting values were found for the same term.
     */
    private Constant getResolvedTerm(final Term term) {
        if (term instanceof Constant) {
            return (Constant) term;
        }

        if (!(term instanceof Variable)) {
            throw new IllegalArgumentException("Term must be a constant or a variable");
        }

        List<Constant> leftTermSubs = getVariableSubstitutionInTupleForLeftRelationalAtoms(term);

        Constant rightTermSub = (rightChildRelationalAtom.getTerms().contains(term)) ?
                getVariableSubstitutionInTupleForRightRelationalAtom(term) : null;

        if (leftTermSubs.isEmpty() && rightTermSub == null) {
            throw new IllegalArgumentException("Valid substitutions were not found for term on either side of the comparison expression " + term);
        }

        if (rightTermSub != null && leftTermSubs.stream().filter(substitution -> !substitution.equals(rightTermSub)).findAny().isPresent()) {
            throw new IllegalArgumentException("Conflicting values found for the same term " + term);
        }

        return (rightTermSub != null) ? rightTermSub : leftTermSubs.get(0);
    }

    /**
     * Checks if the variable substitutions in the right relational atom are consistent with those in the left relational atoms
     * for the current tuple pair being processed.
     *
     * Inherently handles equi-joins as the condition will fail when the variable values are not equal across relational atoms
     *
     * @return true if there are no conflicting variable substitutions, false otherwise
     */
    private boolean hasNoConflictingSubstitutions() {
        return rightChildRelationalAtom.getTerms().stream()
                .noneMatch(term -> {
                    assert term instanceof Variable : "Expected variable but found constant";
                    List<Constant> substitutedValues = getVariableSubstitutionInTupleForLeftRelationalAtoms(term);
                    return substitutedValues.stream()
                            .anyMatch(substitutedValue -> !substitutedValue.equals(getVariableSubstitutionInTupleForRightRelationalAtom(term)));
                });
    }

    /**
     * Evaluates all join predicates on the current inner and outer child tuples by resolving variable substitutions in the
     * comparison atoms, and returns true if all join predicates are satisfied.
     *
     * @return true if all join predicates are satisfied on the current inner and outer child tuples, false otherwise.
     * @throws UnsupportedOperationException if a within-relation comparison atom is found in any of the join predicates.
     */
    private boolean evaluateJoinPredicatesOnTuples() {
        QueryPlanner queryPlanner = QueryPlanner.getQueryPlanner();

        return joinPredicates.stream().allMatch(joinPredicate -> {
            if (leftChildRelationalAtoms.stream().anyMatch(relationalAtom -> queryPlanner.isComparisonConditionWithinRelationalAtom(joinPredicate, relationalAtom)))
                throw new UnsupportedOperationException("Unexpected relation comparison atom found. Please verify the structure of the comparison atom");

            ComparisonAtom resolveVariableSubstitutionsInComparisonAtom = resolveVariableSubstitutionsInComparisonAtom(joinPredicate);
            return resolveVariableSubstitutionsInComparisonAtom.evaluateComparisonCondition();
        });
    }

    /**
     * Checks if the current join operation satisfies the join condition predicates over the relations.
     * Throws an exception if the variables haven't been initialised correctly or if the join sub-tree doesn't have a left-deep child tree, a right relational atom, and join conditions to carry out the join operation.
     *
     * @return true if the join operation satisfies the join condition predicates over the relations; false otherwise.
     * @throws IllegalArgumentException if the join sub-tree doesn't have a left-deep child tree, a right relational atom, and join conditions to carry out the join operation, or if the variables haven't been initialized correctly.
     */
    private boolean satisfiesJoinConditionPredicatesOverRelations() {
        if (leftChildRelationalAtoms == null || rightChildRelationalAtom == null || joinPredicates == null)
            throw new IllegalArgumentException("We expect the join sub-tree to have a left-deep child tree, a right relational atom and join conditions to carry out the join operation. The variables haven't been initialised correctly. Please check the QueryPlanner class for the core logic");
        return hasNoConflictingSubstitutions() && evaluateJoinPredicatesOnTuples();
    }

    /**
     * Sets the pointers to the tuples of the left and right child operators to prepare for the execution of getNextTuple.
     */
    private void setChildTuples() {
        this.outerChildTuple = leftChildOperator.getNextTuple();
        this.innerChildTuple = null;
    }

    /**
     * Sets the list of join condition predicates.
     *
     * @param joinPredicates the list of join condition predicates
     */
    public void setJoinPredicates(List<ComparisonAtom> joinPredicates) {
        this.joinPredicates = joinPredicates;
    }

    /**
     * Sets the relational atoms of the left child operator.
     *
     * @param leftChildRelationalAtoms The list of relational atoms of the left child operator.
     */
    public void setLeftChildRelationalAtoms(List<RelationalAtom> leftChildRelationalAtoms) {
        this.leftChildRelationalAtoms = leftChildRelationalAtoms;
    }

    /**
     * Sets the right child relational atom.
     *
     * @param rightChildRelationalAtom the right child relational atom to be set.
     */
    public void setRightChildRelationalAtoms(RelationalAtom rightChildRelationalAtom) {
        this.rightChildRelationalAtom = rightChildRelationalAtom;
    }
}
