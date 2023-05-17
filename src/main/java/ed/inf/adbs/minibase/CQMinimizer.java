package ed.inf.adbs.minibase;

import ed.inf.adbs.minibase.base.*;
import ed.inf.adbs.minibase.minimization.CQMinimizationHelper;
import ed.inf.adbs.minibase.minimization.CQMinimizationStates;
import ed.inf.adbs.minibase.parser.QueryParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Minimization of conjunctive queries
 */
public class CQMinimizer {

    public static void main(String[] args) {

        if (args.length != 2) {
            System.err.println("Usage: CQMinimizer input_file output_file");
            return;
        }

        String inputFile = args[0];
        String outputFile = args[1];

        minimizeCQ(inputFile, outputFile);
    }

    /**
     * CQ minimization procedure
     * <p>
     * Assume the body of the query from inputFile has no comparison atoms
     * but could potentially have constants in its relational atoms.
     */
    public static void minimizeCQ(String inputFile, String outputFile) {
        // TODO: add your implementation
        /*
        Pseudo code for the CQ Minimisation algorithm from PDB Chapter 16, Page 127

        Input: A CQ q(x̄)
        Output: A CQ q*(x̄) that is a core of q(x̄)

        1: S:=Aq
        2: while there exists R(ū) ∈ S such that each variable in x̄
        3:      occurs in Dom(S − {R(ū)}) and (S, x̄) ⟶ (S − {R(ū)}, x̄) do
        4: S : = S − {R(ū)}
        5: return q*(x̄) :– R1(ū1), . . . , Rn(ūn), where S = {R1(ū1), . . . , Rn(ūn)}

        The CQ has a unique core upto variable renaming
        */
        try {
            Query query = QueryParser.parse(Paths.get(inputFile));
            boolean minimised;

            do {
                minimised = false;
                List<Atom> queryBody = query.getBody();

                for (Atom atomToEvict : queryBody) {
                    List<Atom> minimisedQueryBody = new ArrayList<>(queryBody);
                    minimisedQueryBody.remove(atomToEvict);

                    Query minimisedQuery = new Query(query.getHead(), minimisedQueryBody);

                    // If a homomorphism exists from the source query body to the target query body i.e., source query body ∖ {atomToEvict}
                    // Update the current query to the minimised query i.e., source query body ∖ {atomToEvict}
                    // Sets the minimised flag to true, exit the loop, and continue the process of searching for homomorphisms iteratively by using the minimised query
                    if (doesHomomorphismExist(query, minimisedQuery, (RelationalAtom) atomToEvict)) {
                        query = minimisedQuery;
                        minimised = true;
                        break;
                    }
                }
            } while (minimised);

            // Write the minimised query back to the output file
            String outputQuery = query.toString();
            Files.write(Paths.get(outputFile), outputQuery.getBytes());
        } catch (IOException e) {
            System.err.println("An error occurred while writing the minimised query back to file");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("An unexpected error occurred while processing the query");
            e.printStackTrace();
        }
    }

    /**
     * This function checks whether a homomorphism exists from the source query to the target body. The process involves three steps:
     * <p>
     * 1. The function first determines the variables in the removed atom of the source query.
     * 2. It finds the viable candidate terms from the query body that can be used to substitute the variables in the removed atom.
     * 3. Identifies the variables from the removed atom that appear in the relational atoms of the reduced query body.
     * For each such variable, all candidate terms from step 2 are tested by placing them in.
     * If valid mappings can be established and if the homomorphism conditions hold, we return true.
     *
     * @param sourceQuery the source query to check for homomorphism.
     * @param targetQuery the target query to check for equivalence after the detected mappings are applied.
     * @param removedAtom the evicted relational atom from the source query body.
     * @return true if a homomorphism exists, false otherwise.
     */
    public static boolean doesHomomorphismExist(Query sourceQuery, Query targetQuery, RelationalAtom removedAtom) {
        CQMinimizationHelper cqMinimizationHelper = new CQMinimizationHelper(sourceQuery, targetQuery, removedAtom);

        cqMinimizationHelper.findVariableTermsInRemovedAtom();
        cqMinimizationHelper.findCandidateTermsToSubstituteInRemovedAtom();
        cqMinimizationHelper.findHomomorphismsToAttempt();

        return queryHomomorphismIterator(cqMinimizationHelper);

    }

    /**
     * Attempts to find a homomorphism between source and target query bodies using an iterative DFS approach.
     * <p>
     * 1. In the DFS algorithm, the search starts from an initial state and explores all possible paths that can be followed from that state.
     * 2. The algorithm maintains a queue of candidate states to explore during the search process.
     * 3. At each step, the algorithm selects one of the candidate states from the queue, applies some transformations to it,
     * and generates a set of new candidate states based on the applied transformations.
     * 4. The new candidate states are then added to the queue for further exploration.
     * 5. This process is repeated until the queue is empty or a solution is found.
     * 6. If the queue becomes empty and no solution is found, the algorithm backtracks to the previous state and selects another candidate state to explore.
     * 7. The algorithm continues to backtrack until it finds a state with unexplored candidate states.
     * 8. In this method, the backtracking happens implicitly through the use of a queue to maintain the candidate states.
     * 9. If the queue becomes empty and no homomorphism is found, the algorithm returns false, indicating that a homomorphism does not exist.
     *
     * @param cqMinimizationHelper An instance of the CQMinimizationHelper class containing the source query, the target query,
     *                             the removed atom, candidate terms to substitute in the removed atom, variables in the removed atom,
     *                             potential homomorphism mappings, and unique atoms in the source and target queries.
     * @return true if a homomorphism exists between the two query bodies,
     * false if all potential mappings have been exhausted without finding a valid homomorphism.
     */
    public static boolean queryHomomorphismIterator(CQMinimizationHelper cqMinimizationHelper) {
        // Implements an iterative DFS algorithm. It preserves a queue of potential states to explore during the search process
        Queue<CQMinimizationStates> candidateStates = new LinkedList<>();
        candidateStates.add(new CQMinimizationStates(cqMinimizationHelper.getSourceQueryUniqueAtoms(), cqMinimizationHelper.getAttemptHomomorphisms()));

        // Continue looping until there are no more candidate states left to process
        while (!candidateStates.isEmpty()) {
            CQMinimizationStates cqMinimizationStates = candidateStates.remove();
            HashMap<Variable, Vector<Term>> mappingsToTry = cqMinimizationStates.getMappingsToTry();

            // If there are no more transformations to apply, we have essentially gone over all the potential
            // substitutions and hence we can conclude that a homomorphism does not exist at this point
            if (mappingsToTry.isEmpty()) {
                return false;
            }

            // Appending the outcome of each potential mapping from mappingsToTry to the next set of candidate states
            // The mapping includes the results of the transformation after substituting the current variable,
            // as well as the remaining mappings to attempt for the remaining set of variables in the evicted atom.
            mappingsToTry.entrySet().stream()
                    .map(mapped -> {
                        HashMap<Variable, Vector<Term>> unvisitedMappings = new HashMap<>(mappingsToTry);
                        unvisitedMappings.remove(mapped.getKey());
                        return mapped.getValue().stream()
                                .map(term -> new CQMinimizationStates(applyMappings(cqMinimizationHelper.getSourceQueryUniqueAtoms(), mapped.getKey(), term), unvisitedMappings))
                                .collect(Collectors.toList());
                    })
                    .flatMap(List::stream)
                    .forEach(candidateStates::add);

            // If there is any such candidate state for which the homomorphism conditions hold true,
            // we can purge the evicted atom from the source query body and continue with the minimisation procedure
            if (candidateStates.stream().anyMatch(candidateState -> isQueryMinimised(candidateState.getQuery(), cqMinimizationHelper.getTargetQueryUniqueAtoms()))) {
                return true;
            }
        }
        // No homomorphism was found
        return false;
    }

    /**
     * Substitutes the given variable term in the removed atom with the candidate term for substitution
     * and applies the resulting mappings to the set of relational atoms in the source query body.
     *
     * @param sourceQueryUniqueAtoms       the set of relational atoms in the source query body
     * @param variableTermInRemovedAtom    the variable term to be replaced with the candidate term
     * @param candidateTermForSubstitution the candidate term that will replace the variable term
     * @return a set of relational atoms with the variable term substituted with the candidate term
     */
    public static Set<RelationalAtom> applyMappings(Set<RelationalAtom> sourceQueryUniqueAtoms, Variable variableTermInRemovedAtom, Term candidateTermForSubstitution) {
        return sourceQueryUniqueAtoms.stream()
                .map(atom -> {
                    List<Term> substitutedTerms = new ArrayList<>();
                    for (Term term : atom.getTerms()) {
                        if ((term instanceof Variable) && term.equals(variableTermInRemovedAtom)) {
                            substitutedTerms.add(candidateTermForSubstitution);
                        } else {
                            substitutedTerms.add(term);
                        }
                    }
                    RelationalAtom newRelationalAtom = new RelationalAtom(atom.getName(), substitutedTerms);
                    return newRelationalAtom;
                })
                .collect(Collectors.toSet());
    }

    /**
     * Determines whether the source query is minimised, meaning that it has the same structure as the target query, but with some redundant atoms removed.
     * The two query bodies are checked for equivalence by organizing them in ascending order of their hash-codes
     * and comparing the corresponding relational atoms in each list.
     *
     * @param sourceQueryUniqueAtoms a set of relational atoms representing the unique atoms of the source query.
     * @param targetQueryUniqueAtoms a set of relational atoms representing the unique atoms of the target query.
     * @return true if the source query is minimised, false otherwise
     */
    public static boolean isQueryMinimised(Set<RelationalAtom> sourceQueryUniqueAtoms, Set<RelationalAtom> targetQueryUniqueAtoms) {
        if (sourceQueryUniqueAtoms.size() != targetQueryUniqueAtoms.size()) return false;

        List<RelationalAtom> sourceQueryUniqueAtomList = sourceQueryUniqueAtoms.stream()
                .sorted(Comparator.comparingInt(RelationalAtom::hashCode))
                .collect(Collectors.toList());

        List<RelationalAtom> targetQueryUniqueAtomList = targetQueryUniqueAtoms.stream()
                .sorted(Comparator.comparingInt(RelationalAtom::hashCode))
                .collect(Collectors.toList());

        for (RelationalAtom sourceQueryAtom : sourceQueryUniqueAtomList) {
            RelationalAtom targetQueryAtom = targetQueryUniqueAtomList.get(sourceQueryUniqueAtomList.indexOf(sourceQueryAtom));

            if (!areAtomsEqual(sourceQueryAtom, targetQueryAtom) && Objects.equals(sourceQueryAtom.getName(), targetQueryAtom.getName()))
                return false;
        }

        return true;
    }

    /**
     * Compares two RelationalAtoms to check if they are equal in terms of name and number of terms, and if each term is of the same type and has the same value.
     * Term can either be a variable, IntegerConstant or StringConstant.
     * The equality checks are implemented in the respective classes.
     *
     * @param sourceQueryAtom the first RelationalAtom to compare.
     * @param targetQueryAtom the second RelationalAtom to compare.
     * @return true if the two atoms are equal, false otherwise.
     */
    public static boolean areAtomsEqual(RelationalAtom sourceQueryAtom, RelationalAtom targetQueryAtom) {
        return sourceQueryAtom.getName().equals(targetQueryAtom.getName())
                && sourceQueryAtom.getTerms().size() == targetQueryAtom.getTerms().size()
                && IntStream.range(0, sourceQueryAtom.getTerms().size())
                .allMatch(position -> sourceQueryAtom.getTerms().get(position).getClass().equals(targetQueryAtom.getTerms().get(position).getClass()) && sourceQueryAtom.getTerms().get(position).equals(targetQueryAtom.getTerms().get(position)));
    }
}

