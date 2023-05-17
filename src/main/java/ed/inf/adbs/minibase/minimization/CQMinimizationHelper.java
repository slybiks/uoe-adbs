package ed.inf.adbs.minibase.minimization;

import ed.inf.adbs.minibase.base.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides helper methods for the CQMinimization algorithm.
 * Contains methods to find homomorphisms to attempt, candidate terms to substitute in a removed atom,
 * variable terms in a removed atom and unique atoms in the source and target query bodies.
 */
public class CQMinimizationHelper {
    Query sourceQuery;
    Query targetQuery;
    RelationalAtom removedAtom;

    Vector<Term> candidateTermsToSubstituteInRemovedAtom;
    Vector<Variable> variableTermsInRemovedAtom;
    HashMap<Variable, Vector<Term>> attemptHomomorphisms;

    Set<RelationalAtom> sourceQueryUniqueAtoms;
    Set<RelationalAtom> targetQueryUniqueAtoms;

    /**
     * Constructs the CQMinimizationHelper using the source query, target query and evicted relational atom.
     *
     * @param sourceQuery the source query to be minimised.
     * @param targetQuery the target query to which the source query is being minimised.
     * @param removedAtom the relational atom being removed from the source query.
     */
    public CQMinimizationHelper(Query sourceQuery, Query targetQuery, RelationalAtom removedAtom) {
        this.sourceQuery = sourceQuery;
        this.targetQuery = targetQuery;
        this.removedAtom = removedAtom;

        this.sourceQueryUniqueAtoms = getUniqueAtoms(sourceQuery.getBody());
        this.targetQueryUniqueAtoms = getUniqueAtoms(targetQuery.getBody());
        this.attemptHomomorphisms = new HashMap<>();
    }

    /**
     * Finds unique relational atoms in a query body.
     *
     * @param queryBody the query body to be checked for unique relational atoms.
     * @return set of unique relational atoms.
     */
    public Set<RelationalAtom> getUniqueAtoms(List<Atom> queryBody) {
        return Collections.unmodifiableSet(queryBody.stream()
                .filter(atom -> atom instanceof RelationalAtom)
                .map(atom -> (RelationalAtom) atom)
                .collect(Collectors.toSet()));
    }

    /**
     * Finds mappings to attempt by looking for potential candidate terms to substitute
     * for the variable terms of the evicted relational atom.
     * <p>
     * To minimise computations, we exclude variable mappings to self.
     */
    public void findHomomorphismsToAttempt() {
        variableTermsInRemovedAtom.forEach(variableTerm -> {
            candidateTermsToSubstituteInRemovedAtom.remove(variableTerm);
            attemptHomomorphisms.put(variableTerm, candidateTermsToSubstituteInRemovedAtom);
        });
    }

    /**
     * Finds the candidate terms that can be substituted in the removed atom of a given query.
     * It filters the relational atoms in the body of the target query that have the same name as the removed atom,
     * and then collects all the terms of those relational atoms.
     */
    public void findCandidateTermsToSubstituteInRemovedAtom() {
        candidateTermsToSubstituteInRemovedAtom = targetQuery.getBody().stream()
                .filter(atom -> atom instanceof RelationalAtom)
                .filter(atom -> ((RelationalAtom) atom).getName().equals(removedAtom.getName()))
                .flatMap(atom -> ((RelationalAtom) atom).getTerms().stream())
                .distinct()
                .collect(Collectors.toCollection(Vector::new));
    }

    /**
     * Finds variable terms in a removed atom that are not in the head of the source query.
     */
    public void findVariableTermsInRemovedAtom() {
        variableTermsInRemovedAtom = removedAtom.getTerms().stream()
                .filter(term -> term instanceof Variable)
                .map(Variable.class::cast)
                .filter(variable -> !sourceQuery.getHead().getTerms().contains(variable))
                .collect(Collectors.toCollection(Vector::new));
    }

    /**
     * Returns the source query.
     *
     * @return the source query.
     */
    public Query getSourceQuery() {
        return sourceQuery;
    }

    /**
     * Returns the target query.
     *
     * @return the target query.
     */
    public Query getTargetQuery() {
        return targetQuery;
    }

    /**
     * Returns the removed atom.
     *
     * @return the removed atom.
     */
    public RelationalAtom getRemovedAtom() {
        return removedAtom;
    }

    /**
     * Returns the vector of candidate terms to substitute in the removed atom.
     *
     * @return the vector of candidate terms to substitute in the removed atom.
     */
    public Vector<Term> getCandidateTermsToSubstituteInRemovedAtom() {
        return candidateTermsToSubstituteInRemovedAtom;
    }

    /**
     * Returns the vector of variable terms in the removed atom.
     *
     * @return the vector of variable terms in the removed atom.
     */
    public Vector<Variable> getVariableTermsInRemovedAtom() {
        return variableTermsInRemovedAtom;
    }

    /**
     * Returns a hashmap of mappings to attempt for each variable in the removed atom.
     *
     * @return the hashmap of mappings to attempt for each variable in the removed atom.
     */
    public HashMap<Variable, Vector<Term>> getAttemptHomomorphisms() {
        return attemptHomomorphisms;
    }

    /**
     * Returns the set of unique relational atoms in the source query body.
     *
     * @return the set of unique relational atoms in the source query body.
     */
    public Set<RelationalAtom> getSourceQueryUniqueAtoms() {
        return sourceQueryUniqueAtoms;
    }

    /**
     * Returns the set of unique relational atoms in the target query body.
     *
     * @return the set of unique relational atoms in the target query body.
     */
    public Set<RelationalAtom> getTargetQueryUniqueAtoms() {
        return targetQueryUniqueAtoms;
    }
}
