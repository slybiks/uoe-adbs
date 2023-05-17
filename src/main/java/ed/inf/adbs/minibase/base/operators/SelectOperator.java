package ed.inf.adbs.minibase.base.operators;

import ed.inf.adbs.minibase.Utils;
import ed.inf.adbs.minibase.base.*;

import java.util.Collections;
import java.util.List;

/**
 * The SelectOperator filters tuples based on clauses specified in the comparison atoms.
 */
public class SelectOperator extends Operator {
    private final Operator childOperator;
    private final RelationalAtom relationalAtom;
    private final List<ComparisonAtom> comparisonAtoms;

    /**
     * Constructs a ScanOperator with the specified relational atom, comparison atoms and child operator.
     *
     * @param childOperator   the operator that will generate tuples to be filtered.
     * @param relationalAtom  the relation from which the tuple will be filtered.
     * @param comparisonAtoms the conditions against which the tuples will be filtered.
     */
    public SelectOperator(RelationalAtom relationalAtom, List<ComparisonAtom> comparisonAtoms, Operator childOperator) {
        if (doesRelationalAtomContainConstantsSanityCheck(relationalAtom))
            throw new IllegalArgumentException("The relational atom should not have any constants embedded in them at this point");

        this.childOperator = childOperator;
        this.relationalAtom = relationalAtom;
        this.comparisonAtoms = comparisonAtoms;
    }

    /**
     * This method returns the next tuple that satisfies the selection condition, if it exists.
     *
     * @return the next tuple that satisfies the selection condition, or null if there are no more tuples.
     */
    @Override
    public Tuple getNextTuple() {
        Tuple currentTuple;
        while ((currentTuple = childOperator.getNextTuple()) != null) {
            if (satisfiesSelectionConditionPredicates(currentTuple)) {
                return currentTuple;
            }
        }
        return null;
    }

    /**
     * This method resets the state of the operator so that it can start returning tuples from the beginning.
     */
    @Override
    public void reset() {
        childOperator.reset();
    }

    /**
     * This method checks if the relational atom contains any constants.
     *
     * @param relationalAtom the relational atom to be checked.
     * @return true if the relational atom contains constants, false otherwise.
     */
    private boolean doesRelationalAtomContainConstantsSanityCheck(final RelationalAtom relationalAtom) {
        return relationalAtom.getTerms().stream().anyMatch(Constant.class::isInstance);
    }

    /**
     * This method checks if a given tuple satisfies all the specified selection condition predicates.
     *
     * @param currentTuple the tuple to be checked.
     * @return true if the tuple satisfies all the selection condition predicates, false otherwise.
     */
    private boolean satisfiesSelectionConditionPredicates(final Tuple currentTuple) {
        for (ComparisonAtom comparisonAtom : comparisonAtoms) {
            if (!evaluateSelectionPredicateOver(currentTuple, comparisonAtom)) {
                return false;
            }
        }
        return true;
    }

    /**
     * This method evaluates a selection predicate over a given tuple and comparison atom.
     *
     * @param currentTuple   the tuple to be evaluated.
     * @param comparisonAtom the comparison atom to be evaluated.
     * @return true if the comparison atom evaluates to true over the tuple, false otherwise.
     */
    private boolean evaluateSelectionPredicateOver(final Tuple currentTuple, final ComparisonAtom comparisonAtom) {
        if (currentTuple.getRelationalTerms().size() != relationalAtom.getTerms().size()) {
            throw new UnsupportedOperationException("Tuple size does not match the number of terms found in the relational atom");
        }

        if (!hasRelationalAtomTermsInComparisonAtom(comparisonAtom)) return false;

        ComparisonAtom comparisonConditionWithActualValues = createComparisonConditionWithActualValues(currentTuple, comparisonAtom);
        return comparisonConditionWithActualValues.evaluateComparisonCondition();
    }


    /**
     * Creates a new ComparisonAtom object by substituting the variable terms in the given comparison atom
     * with the actual Constant values found in the current tuple.
     *
     * @param currentTuple   the tuple whose actual values should be used for the substitution.
     * @param comparisonAtom the comparison atom with variable terms to be substituted.
     * @return a new ComparisonAtom object with the variable terms substituted with actual Constant values.
     */
    private ComparisonAtom createComparisonConditionWithActualValues(final Tuple currentTuple, final ComparisonAtom comparisonAtom) {
        Constant term1 = createComparisonConditionWithActualValuesUtil(currentTuple, comparisonAtom.getTerm1());
        Constant term2 = createComparisonConditionWithActualValuesUtil(currentTuple, comparisonAtom.getTerm2());

        return new ComparisonAtom(term1, term2, comparisonAtom.getOp());
    }

    /**
     * This method creates a constant value for a term in a comparison atom by substituting any variables with their corresponding values from the provided tuple.
     *
     * @param currentTuple       The tuple to use for substituting variables with values.
     * @param comparisonAtomTerm The term to substitute variables with values.
     * @return The constant value for the provided term in the comparison atom.
     * @throws IllegalArgumentException If the attempted substitution is invalid because the value being substituted is not a constant.
     * @assert all constant values obtained for the given term from the tuple are all equal.
     */
    private Constant createComparisonConditionWithActualValuesUtil(final Tuple currentTuple, final Term comparisonAtomTerm) {
        List<Constant> constantValues = (List<Constant>) Utils.matchTermsToValuesInTuple(currentTuple, comparisonAtomTerm, Collections.singletonList(relationalAtom))
                .orElseThrow(() -> new IllegalArgumentException("The attempted substitution is invalid as the value being substituted is not a constant"));
        assert constantValues.stream().allMatch(constantValues.get(0)::equals);

        return constantValues.get(0);
    }

    /**
     * This method checks whether the terms used in the comparison atom are present in the relational atom.
     *
     * If both terms in the comparison atom are constants, then this method always returns true.
     * If exactly one of the terms is a variable, then this method checks if the variable is present in the relational atom.
     * If both terms are variables and are not part of the relational atom, then this method returns false.
     *
     * @param comparisonAtom the comparison atom whose terms need to be checked against the relational atom.
     * @return true if the terms in the comparison atom are present in the relational atom, false otherwise.
     */
    private boolean hasRelationalAtomTermsInComparisonAtom(final ComparisonAtom comparisonAtom) {
        Term term1 = comparisonAtom.getTerm1();
        Term term2 = comparisonAtom.getTerm2();

        List<Term> relationalAtomTerms = relationalAtom.getTerms();

        return (!(term1 instanceof Variable) || relationalAtomTerms.contains(term1)) && (!(term2 instanceof Variable) || relationalAtomTerms.contains(term2));
    }

    public RelationalAtom getRelationalAtom() {
        return relationalAtom;
    }
}
