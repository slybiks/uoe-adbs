package ed.inf.adbs.minibase.base.operators;

import ed.inf.adbs.minibase.Utils;
import ed.inf.adbs.minibase.base.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The ProjectOperator is a relational algebra operator which retrieves tuples from a child operator and performs projection on them.
 * Projection involves selecting a subset of attributes from the given tuples and discarding the rest.
 */
public class ProjectOperator extends Operator {
    private final Operator childOperator;
    private final List<RelationalAtom> relationalAtoms;
    private final List<Term> projectedTerms;

    // This operator is blocking because it must wait until it has observed the entire output from its child operator before generating a unique tuple output set.
    // Hence, the tuples need to be buffered
    private final List<Tuple> tupleBuffer = new ArrayList<>();

    /**
     * Constructs a ProjectOperator with the specified relational atoms, projected terms and child operator.
     *
     * @param relationalAtoms the relational atoms which contain the terms that will be used for projection.
     * @param projectedTerms  the terms to be selected for projection from the relational atoms.
     * @param childOperator   the child operator whose tuples are to be projected.
     */
    public ProjectOperator(List<RelationalAtom> relationalAtoms, List<Term> projectedTerms, Operator childOperator) {
        if (doesRelationalAtomContainAllProjectedTermsSanityCheck(relationalAtoms, projectedTerms))
            throw new IllegalArgumentException("Output variables contain non-existent input variables");

        this.relationalAtoms = relationalAtoms;
        this.projectedTerms = projectedTerms;
        this.childOperator = childOperator;
    }

    /**
     * This method retrieves the next tuple to be projected from the child operator, if it exists.
     *
     * @return the next tuple to be projected, or null if there are no more tuples.
     */
    @Override
    public Tuple getNextTuple() {
        Tuple nextChildTuple;

        while ((nextChildTuple = childOperator.getNextTuple()) != null) {
            Tuple projectedTuple = retrieveTupleAfterApplyingProjection(nextChildTuple, relationalAtoms, projectedTerms);
            if (!tupleBuffer.contains(projectedTuple)) {
                tupleBuffer.add(projectedTuple);
                return projectedTuple;
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
     * Performs a sanity check to ensure that all the terms which are required to perform the projection are present in the
     * relational atoms to be projected. Throws an exception if any term in projected terms  is not present in any relational atom.
     *
     * @param relationalAtoms the list of relational atoms to be projected.
     * @param projectedTerms  the list of terms that are to be projected.
     * @return true if all projected terms are present in the relational atoms.
     * @throws IllegalArgumentException if any term in the projected terms is not present in any relational atom.
     */
    private boolean doesRelationalAtomContainAllProjectedTermsSanityCheck(final List<RelationalAtom> relationalAtoms, final List<Term> projectedTerms) {
        Set<Term> relationalTerms = relationalAtoms.stream().flatMap(relationalAtom -> relationalAtom.getTerms().stream()).collect(Collectors.toSet());
        return !relationalTerms.containsAll(projectedTerms);
    }

    /**
     * Applies projection to the given tuple by extracting only the specified projected terms.
     *
     * @param currentTuple    the tuple to apply projection to.
     * @param relationalAtoms the list of relational atoms.
     * @param projectedTerms  the list of terms that are to be projected.
     * @return a new tuple with only the specified projected terms.
     * @throws UnsupportedOperationException if relational atoms are empty or if the size of the tuple does not match the number of terms found in the relational atom.
     */
    public static Tuple retrieveTupleAfterApplyingProjection(final Tuple currentTuple, final List<RelationalAtom> relationalAtoms, final List<Term> projectedTerms) {
        if (relationalAtoms.isEmpty())
            throw new UnsupportedOperationException("Unable to perform projection as relational atoms appear to be empty");

        int relationalAtomsSize = relationalAtoms.stream().mapToInt(relationalAtom -> relationalAtom.getTerms().size()).sum();

        if (relationalAtomsSize != currentTuple.getRelationalTerms().size())
            throw new UnsupportedOperationException("Tuple size does not match the number of terms found in the relational atom");

        List<Constant> substitutedConstants = new ArrayList<>();

        for (Term projectedTerm : projectedTerms) {
            Constant substitutedConstant = (projectedTerm instanceof Variable) ? substituteVariablesWithActualValues(relationalAtoms, projectedTerm, currentTuple) : (Constant) projectedTerm;
            substitutedConstants.add(substitutedConstant);
        }

        return new Tuple(substitutedConstants);
    }

    /**
     * Substitutes variables in the projected term with their actual values, based on the given relational atoms and current tuple.
     *
     * @param relationalAtoms the list of relational atoms to use for substitution.
     * @param projectedTerm   the term to substitute variables with actual values.
     * @param currentTuple    the current tuple containing the values to substitute.
     * @return the constant value obtained after substituting variables with actual values in the projected term.
     * @throws IllegalArgumentException if the attempted substitution is invalid due to empty values, non-constant values being substituted or if the substituted values contradict each other.
     */
    public static Constant substituteVariablesWithActualValues(final List<RelationalAtom> relationalAtoms, final Term projectedTerm, final Tuple currentTuple) {
        Object substitutedValues = Utils.matchTermsToValuesInTuple(currentTuple, projectedTerm, relationalAtoms).orElseThrow(() -> new IllegalArgumentException("The attempted substitution is invalid as the values being substituted are empty"));

        if (!(substitutedValues instanceof List<?> && ((List<?>) substitutedValues).stream().allMatch(Constant.class::isInstance))) {
            throw new IllegalArgumentException("The attempted substitution is invalid as the value being substituted is not a constant or a list of constants");
        }

        List<Constant> substitutedConstants = (List<Constant>) substitutedValues;
        boolean isValueUniform = substitutedConstants.stream().allMatch(substitutedConstants.get(0)::equals);
        if (!isValueUniform)
            throw new IllegalArgumentException("The projected term was found to contain duplicate values across the tuple which is not permissible");
        return substitutedConstants.get(0);
    }
}
