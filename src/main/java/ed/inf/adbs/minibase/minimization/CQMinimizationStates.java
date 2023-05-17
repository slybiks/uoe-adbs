package ed.inf.adbs.minibase.minimization;

import ed.inf.adbs.minibase.base.RelationalAtom;
import ed.inf.adbs.minibase.base.Term;
import ed.inf.adbs.minibase.base.Variable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper class that represents a state of the CQ minimization algorithm, consisting of a set of relational atoms representing the query
 * being minimised a map of variables to vectors of terms that are used to keep track of the possible replacements for each variable
 * in the query in order to determine if a homomorphism exists.
 */
public class CQMinimizationStates {

    private final Set<RelationalAtom> query;
    private final HashMap<Variable, Vector<Term>> mappingsToTry;

    /**
     * Constructs a CQ Minimization state object.
     *
     * @param query         the set of relational atoms representing the current query body.
     * @param mappingsToTry a map of variables to vectors of terms to try as potential substitutions for each variable.
     * @return a CQMinimizationStates object encapsulating the query and mappings to attempt.
     */
    public CQMinimizationStates(Set<RelationalAtom> query, HashMap<Variable, Vector<Term>> mappingsToTry) {
        this.query = query;
        // Identifies a unique set of terms to be applied to each variable instance and converts them into a vector
        this.mappingsToTry = new HashMap<>(mappingsToTry.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, variable -> {
                    Set<Term> uniqueTerms = new HashSet<>(variable.getValue());
                    return new Vector<>(uniqueTerms);
                })));
    }

    /**
     * Get the set of relational atoms representing the query.
     *
     * @return the set of relational atoms representing the query.
     */
    public Set<RelationalAtom> getQuery() {
        return query;
    }

    /**
     * Get the map of variables to vectors of terms to try as potential substitutions for each variable.
     *
     * @return the map of variables to vectors of terms to try as potential substitutions for each variable.
     */
    public HashMap<Variable, Vector<Term>> getMappingsToTry() {
        return mappingsToTry;
    }
}
