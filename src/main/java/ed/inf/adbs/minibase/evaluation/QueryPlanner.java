package ed.inf.adbs.minibase.evaluation;

import ed.inf.adbs.minibase.Utils;
import ed.inf.adbs.minibase.base.*;
import ed.inf.adbs.minibase.base.operators.*;
import ed.inf.adbs.minibase.datastructures.MultiMap;
import ed.inf.adbs.minibase.datastructures.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The QueryPlanner class provides methods to build a query plan tree for a given source query, and provides access to
 * the root operator of the resulting query plan.
 */
public class QueryPlanner {
    // Singleton query planner object
    // Responsible for building the query plan tree
    private static volatile QueryPlanner queryPlanner;

    private Query sourceQuery;
    private Operator rootOperator;

    private QueryPlanner() {
    }

    /**
     * Instantiates and returns a new query planner that is used later to build the query plan tree.
     * The class adopts the singleton pattern to guarantee the existence of only one instance of the query planner throughout the query evaluation lifecycle.
     * This approach enables easy access to the query planner from any part of the Minibase application,
     * thereby serving as a convenient way to manage a central resource or service, and offering centralized control over the planner.
     *
     * @return None
     */
    public static QueryPlanner getQueryPlanner() {
        // Thread-safe implementation of the singleton pattern for initialising the query planner
        if (queryPlanner == null) {
            queryPlanner = new QueryPlanner();
        }
        return queryPlanner;
    }

    /**
     * Initializes the QueryPlanner with the given source query by renaming terms and building the query plan tree.
     *
     * @param inputQuery the input query to be evaluated.
     * @throws UnsupportedOperationException if the inputQuery is null, empty, or malformed.
     */
    public void initialiseQueryPlanner(final Query inputQuery) {
        if (inputQuery.isNull())
            throw new UnsupportedOperationException("Unable to proceed with the query evaluation as the given query was found to be empty or malformed");
        sourceQuery = Utils.buildNewQueryWithRenamedTerms(inputQuery);
        buildQueryPlanTree();
    }

    /**
     * Builds a query plan tree for the given source query.
     *
     * @return None
     */
    private void buildQueryPlanTree() {
        RelationalAtom queryHead = sourceQuery.getHead();
        List<Atom> queryBody = sourceQuery.getBody();

        // Filter all the relational atoms from the query body
        List<RelationalAtom> relationalAtoms = Utils.filterAndCast(queryBody, RelationalAtom.class);

        // Filter all the comparison atoms from the query body
        List<ComparisonAtom> comparisonAtoms = Utils.filterAndCast(queryBody, ComparisonAtom.class);

        // Get the sum aggregate operator from the query head (which may be null)
        SumAggregate aggregateOperator = queryHead.getSumAggregate();

        // Create scans for all the relational atoms
        List<ScanOperator> scanOperators = createScanOperators(relationalAtoms);

        // Identify the entire set of operators that will form the query tree
        List<Operator> childOperators = getChildOperators(scanOperators, comparisonAtoms);

        // Generate the query tree by either performing joins iteratively or
        // if there is only one child operator, the root of the query tree thus far,
        // is simply the relational atom from that child operator
        Pair<List<RelationalAtom>, Operator> queryTree = (childOperators.size() == 1)
                ? new Pair<>(Collections.singletonList(getJoinChild(childOperators.get(0))), childOperators.get(0))
                : performJoinsIteratively(scanOperators, comparisonAtoms, childOperators);

        // If the query head does not involve any aggregation, then projection becomes the root
        if (aggregateOperator == null) {
            setProjectionAsRootOperator(queryHead, queryTree.getFirst(), queryTree.getSecond());
        } else {
            setSumAggregateAsRootOperator(queryHead, queryTree.getFirst(), queryTree.getSecond(), aggregateOperator.getProductTerms());
        }
    }

    /**
     * Creates a list of scan operators for a given list of relational atoms, where each scan operator is
     * associated with a single relational atom and corresponding relational schema.
     * <p>
     * The scan operator reads the records from a relation using the information supplied by the relational atom's schema.
     *
     * @param relationalAtoms a list of relational atoms for which scan operators are to be created.
     * @return a list of scan operators for the relational atoms occurring in the query body.
     * @throws Exception if any error occurs while creating a scan operator for a relational atom.
     */
    private List<ScanOperator> createScanOperators(final List<RelationalAtom> relationalAtoms) {
        return relationalAtoms.stream().map(relationalAtom -> {
            RelationalSchema relationalSchema = DatabaseCatalog.getCatalog().getSchemas().get(relationalAtom.getName());
            try {
                return new ScanOperator(relationalSchema, relationalAtom);
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }).collect(Collectors.toList());
    }

    /**
     * Checks if a given comparison atom has at most one variable in it.
     *
     * @param comparisonAtom the comparison atom to check.
     * @return true if the comparison atom has at most one variable, false otherwise.
     */
    public boolean hasAtMostOneVariableInComparison(final ComparisonAtom comparisonAtom) {
        boolean hasVariable = (comparisonAtom.getTerm1() instanceof Variable);
        if (comparisonAtom.getTerm2() instanceof Variable) hasVariable = true;
        return !hasVariable || comparisonAtom.getTerm1() instanceof Variable != comparisonAtom.getTerm2() instanceof Variable;
    }

    /**
     * Checks if the given comparison atom involves two variables and one of them is present in the given relational atom,
     * indicating that it represents a join condition between two relations in the query.
     *
     * @param comparisonAtom the comparison atom to be checked.
     * @param relationalAtom the relational atom to be checked against.
     * @return true if the comparison atom involves two variables and one of them is present in the given relational atom, false otherwise.
     */
    public boolean isComparisonConditionBetweenRelationalAtomVariables(final ComparisonAtom comparisonAtom, final RelationalAtom relationalAtom) {
        return !hasAtMostOneVariableInComparison(comparisonAtom) &&
                (relationalAtom.getTerms().contains(comparisonAtom.getTerm1()) ^ relationalAtom.getTerms().contains(comparisonAtom.getTerm2()));
    }

    /**
     * Checks if the given comparison condition involves variables that belong to the same relational atom,
     * which would indicate that it is a selection predicate used to filter tuples within the relation.
     *
     * @param comparisonAtom the comparison condition to check.
     * @param relationalAtom the relational atom to compare the variables with.
     * @return true if the comparison condition involves variables from the same relational atom
     * or if the comparison atom contains exactly one variable and a constant,
     * false otherwise.
     */
    public boolean isComparisonConditionWithinRelationalAtom(final ComparisonAtom comparisonAtom, final RelationalAtom relationalAtom) {
        return hasAtMostOneVariableInComparison(comparisonAtom) ||
                (relationalAtom.getTerms().contains(comparisonAtom.getTerm1()) && relationalAtom.getTerms().contains(comparisonAtom.getTerm2()));
    }

    /**
     * This method takes in a list of ScanOperators (to go over all the relational atoms in the query body),
     * the list of all ComparisonAtoms and a boolean indicating whether the comparison atoms to be uprooted
     * are selection predicates (standalone, within relation comparisons) or join predicates.
     * <p>
     * For standalone comparison atoms, it checks if the comparison condition has exactly one variable (and a constant),
     * is strictly within any of the relational atoms (if there are two variables),
     * and does not compare variables from different relational atoms.
     * <p>
     * For join comparisons, it checks if the comparison condition involves variables from different relational atoms.
     * <p>
     * If either of these conditions are true in their respective cases, the ComparisonAtom is considered relevant and added to the set of relevant ComparisonAtoms.
     * <p>
     * Finally, it returns the set of relevant ComparisonAtoms as a list.
     *
     * @param scanOperators          a list of scan operators representing the relational atoms in the query body.
     * @param comparisonAtoms        the list of all comparison atoms to be evaluated for relevance.
     * @param isStandaloneComparison a flag indicating if the comparison atoms are being evaluated as standalone or join predicates.
     * @return a list of comparison atoms that are relevant for evaluating selection or join operations.
     */
    private List<ComparisonAtom> filterComparisonAtoms(final List<ScanOperator> scanOperators, final List<ComparisonAtom> comparisonAtoms, final boolean isStandaloneComparison) {
        Set<ComparisonAtom> relevantComparisonAtoms = new HashSet<>();

        for (ComparisonAtom comparisonAtom : comparisonAtoms) {

            if (isStandaloneComparison) {
                boolean isWithinRelationalAtom = scanOperators.stream().anyMatch(scanOperator -> isComparisonConditionWithinRelationalAtom(comparisonAtom, scanOperator.getRelationalAtom()));

                boolean notBetweenRelationalAtomVariables = scanOperators.stream().noneMatch(scanOperator -> isComparisonConditionBetweenRelationalAtomVariables(comparisonAtom, scanOperator.getRelationalAtom()));

                if (isWithinRelationalAtom || notBetweenRelationalAtomVariables) {
                    relevantComparisonAtoms.add(comparisonAtom);
                }
            } else scanOperators.forEach(scanOperator -> {
                if (isComparisonConditionBetweenRelationalAtomVariables(comparisonAtom, scanOperator.getRelationalAtom())) {
                    relevantComparisonAtoms.add(comparisonAtom);
                }
            });
        }
        return new ArrayList<>(relevantComparisonAtoms);
    }

    /**
     * Returns a list of join child operators.
     * <p>
     * This method first separates the given comparison atoms into two lists, one for standalone comparison atoms
     * (i.e., those within a relational atom) and another for comparison atoms in join conditions.
     * <p>
     * Then, the method iterates through the given scan operators and adds either a SelectOperator or ScanOperator
     * to the appropriate list. If there are standalone comparison atoms associated with a scan operator, a SelectOperator
     * is created and added to the selectOperators list. Otherwise, the scan operator is added to the scanOperatorsNoSelection
     * list.
     * <p>
     * Finally, the method concatenates the two lists of operators and returns the result.
     *
     * @param scanOperators   a list of ScanOperators to create join child operators for
     * @param comparisonAtoms a list of comparison atoms to use in creating join child operators
     * @return a list of join child operators for the given scan operators and comparison atoms
     */
    private List<Operator> getChildOperators(final List<ScanOperator> scanOperators, final List<ComparisonAtom> comparisonAtoms) {
        // Create two separate lists: one for SelectOperators and the other for ScanOperators
        List<SelectOperator> selectOperators = new ArrayList<>();
        List<ScanOperator> scanOperatorsNoSelection = new ArrayList<>();

        List<ComparisonAtom> standaloneComparisonAtoms = filterComparisonAtoms(scanOperators, comparisonAtoms, true);

        // Iterate through scan operators and add either a SelectOperator or ScanOperator to the appropriate list
        for (ScanOperator scanOperator : scanOperators) {
            List<ComparisonAtom> comparisonAtomsForSelect = standaloneComparisonAtoms.stream()
                    .filter(comparisonAtom -> comparisonAtom.isContainedInRelationalAtom(scanOperator.getRelationalAtom()))
                    .collect(Collectors.toList());
            if (!comparisonAtomsForSelect.isEmpty()) {
                selectOperators.add(new SelectOperator(scanOperator.getRelationalAtom(), comparisonAtomsForSelect, scanOperator));
            } else {
                scanOperatorsNoSelection.add(scanOperator);
            }
        }
        // Merge the two lists of operators together
        return Operator.concatenateLists(selectOperators, scanOperatorsNoSelection);
    }

    /**
     * Iterates over the list of scan operators in reverse order, and returns the first relational atom to which the given
     * comparison atom belongs. The relational atom is the one that contains exactly one of the comparison atom's terms.
     * <p>
     * This method only receives comparison atoms with join predicates as we stub (impute) the selection predicates at the
     * {@link #groupJoinConditionsByRelationalAtomWithMatchingPredicate} level.
     *
     * @param comparisonAtom the comparison atom for which the matching relational atom is to be found.
     * @param scanOperators  the list of scan operators corresponding to all the relational atoms in the query body.
     * @return the relational atom containing at least one of the comparison atom's terms.
     * @throws RuntimeException throws a runtime exception if the terms associated with the comparison atom do not appear
     *                          in any of the scan operators.
     */
    private RelationalAtom getRelationalAtomForJoinMatchingPredicate(final ComparisonAtom comparisonAtom, final List<ScanOperator> scanOperators) {
        RelationalAtom relationalAtomForJoinMatchingPredicate = null;

        ListIterator<ScanOperator> iterator = scanOperators.listIterator(scanOperators.size());

        while (iterator.hasPrevious()) {
            ScanOperator scanOperator = iterator.previous();
            if (scanOperator.getRelationalAtom().getTerms().contains(comparisonAtom.getTerm1()) || scanOperator.getRelationalAtom().getTerms().contains(comparisonAtom.getTerm2())) {
                relationalAtomForJoinMatchingPredicate = scanOperator.getRelationalAtom();
                break;
            }
        }

        if (relationalAtomForJoinMatchingPredicate == null) {
            throw new RuntimeException("The terms associated with the current comparison atom do not appear in any of the scan operators");
        }

        return relationalAtomForJoinMatchingPredicate;
    }

    /**
     * Groups the comparison atoms that belong to join conditions based on their corresponding relational atoms
     * which means that it does so only for those comparison atoms whose terms belong to different relational atoms.
     * <p>
     * It takes the complete list of comparison atoms and a list of scan operators representing the relational atoms in the query body.
     * The output is a MultiMap that maps each relational atom to its corresponding join predicate(s).
     * <p>
     * To avoid a comparison atom from appearing in the relational atom value list of two relations that need to be joined,
     * the key to which the comparison atom is assigned is determined by {@link #getRelationalAtomForJoinMatchingPredicate}.
     *
     * @param comparisonAtoms the list of all comparison atoms to be filtered and mapped.
     * @param scanOperators   the list of scan operators representing relational atoms in the query body.
     * @return a MultiMap where each entry corresponds to a relational atom and the comparison atoms that have one of their terms belonging to that relational atom.
     */
    private MultiMap<RelationalAtom, ComparisonAtom> groupJoinConditionsByRelationalAtomWithMatchingPredicate(final List<ComparisonAtom> comparisonAtoms, final List<ScanOperator> scanOperators) {
        List<ComparisonAtom> joinPredicates = filterComparisonAtoms(scanOperators, comparisonAtoms, false);
        MultiMap<RelationalAtom, ComparisonAtom> joinPredicatesAssociatedWithRelationalAtom = new MultiMap<>();
        for (ComparisonAtom comparisonAtom : joinPredicates) {
            RelationalAtom relationalAtomForJoinMatchingPredicate = getRelationalAtomForJoinMatchingPredicate(comparisonAtom, scanOperators);
            joinPredicatesAssociatedWithRelationalAtom.put(relationalAtomForJoinMatchingPredicate, comparisonAtom);
        }

        return joinPredicatesAssociatedWithRelationalAtom;
    }

    /**
     * Returns the relational atom that serves as the input for the join operation, which is the relational atom produced
     * by the child of the join operator.
     * <p>
     * The child of a join operator can either be a select or scan.
     *
     * @param childOperator the child operator of the join operator.
     * @return the relational atom produced by the child operator of the join operator.
     */
    private RelationalAtom getJoinChild(final Operator childOperator) {
        return Stream.of(childOperator)
                .filter(SelectOperator.class::isInstance)
                .map(SelectOperator.class::cast)
                .findFirst()
                .map(SelectOperator::getRelationalAtom)
                .orElseGet(() -> {
                    assert childOperator instanceof ScanOperator;
                    return ((ScanOperator) childOperator).getRelationalAtom();
                });
    }

    /**
     * Perform joins iteratively using the provided list of scan operators, comparison atoms, and join child operators.
     *
     * @param scanOperators      the list of scan operators.
     * @param comparisonAtoms    the list of comparison atoms.
     * @param joinChildOperators the list of join child operators.
     * @return A pair consisting of the list of left tree relational atoms and the current left deep join tree.
     */
    private <T1, T2> Pair<T1, T2> performJoinsIteratively(final List<ScanOperator> scanOperators, final List<ComparisonAtom> comparisonAtoms, final List<Operator> joinChildOperators) {
        MultiMap<RelationalAtom, ComparisonAtom> joinPredicatesAssociatedWithRelationalAtom = groupJoinConditionsByRelationalAtomWithMatchingPredicate(comparisonAtoms, scanOperators);

        Iterator<Operator> joinChildOperatorsIterator = joinChildOperators.iterator();
        Pair<Operator, Operator> firstPairOfChildren = new Pair<>(joinChildOperatorsIterator.next(), joinChildOperatorsIterator.next());

        // Create initial left child join operator using the first pair of children that were obtained from the list of join child operators
        // Set it as the current left child of the join
        JoinOperator currentLeftDeepJoinTree = new JoinOperator(firstPairOfChildren.getFirst(), firstPairOfChildren.getSecond());

        // Get the left and right relational atoms for the first join
        // For the initial join, the relational atoms on the left and right sub-trees are separate entities
        RelationalAtom leftTreeRelationalAtom = getJoinChild(firstPairOfChildren.getFirst());
        RelationalAtom rightTreeRelationalAtom = getJoinChild(firstPairOfChildren.getSecond());

        // As we construct the query tree from the bottom-up, any joins that have already been visited or performed
        // will form the left-hand side of the tree, supporting the idea of a "left-deep" join

        // In contrast, the right child is a single relational atom
        // that needs to be combined with the relations that have already been joined
        // i.e., we append a relational atom from the right side to a collection of relations
        // that have been joined up until now on the left side
        List<RelationalAtom> leftTreeRelationalAtoms = Collections.singletonList(leftTreeRelationalAtom);

        // Set the left and right relational atoms and join conditions for the first join
        currentLeftDeepJoinTree.setLeftChildRelationalAtoms(leftTreeRelationalAtoms);
        currentLeftDeepJoinTree.setRightChildRelationalAtoms(rightTreeRelationalAtom);
        currentLeftDeepJoinTree.setJoinPredicates(joinPredicatesAssociatedWithRelationalAtom.get(rightTreeRelationalAtom));
        // Following the intuition provided above for the "left-deep" join,
        // leftRelationalAtoms needs to be updated to include the present right relational atom
        leftTreeRelationalAtoms = new ArrayList<>(leftTreeRelationalAtoms);
        leftTreeRelationalAtoms.add(rightTreeRelationalAtom);

        // Proceed to the next join operator and continue constructing the query tree in a similar fashion
        // until all remaining operators have been processed
        while (joinChildOperatorsIterator.hasNext()) {
            // Get the right relational atom
            Operator childOperator = joinChildOperatorsIterator.next();
            rightTreeRelationalAtom = getJoinChild(childOperator);

            // Create new join operator using the current left child and right child and join conditions
            JoinOperator joinOperator = new JoinOperator(currentLeftDeepJoinTree, childOperator);
            joinOperator.setLeftChildRelationalAtoms(new ArrayList<>(leftTreeRelationalAtoms));
            joinOperator.setRightChildRelationalAtoms(rightTreeRelationalAtom);
            joinOperator.setJoinPredicates(joinPredicatesAssociatedWithRelationalAtom.get(rightTreeRelationalAtom));

            // Update leftRelationalAtoms to include the right relational atom and set currentLeftDeepJoinTree
            // to the new join operator to maintain a record of the latest join operation
            // and the advancement of the tree from the root
            currentLeftDeepJoinTree = joinOperator;
            leftTreeRelationalAtoms.add(rightTreeRelationalAtom);
        }

        return (Pair<T1, T2>) new Pair<>(leftTreeRelationalAtoms, currentLeftDeepJoinTree);
    }

    /**
     * Sets a ProjectionOperator as the root operator for the query evaluation.
     *
     * @param queryHead       the head of the input query.
     * @param relationalAtoms a list of relational atoms that the project operator will work with (could be single relational atom or a list of them).
     * @param childOperator   the child operator of the project operator which are the lower levels of the tree that have been built up so far.
     */
    private <T> void setProjectionAsRootOperator(final RelationalAtom queryHead, T relationalAtoms, Operator childOperator) {
        rootOperator = new ProjectOperator((List<RelationalAtom>) relationalAtoms,
                new ArrayList<>(queryHead.getTerms()),
                childOperator);
    }

    /**
     * Sets the root operator of the query plan tree to a SumOperator that performs sum aggregation on a list of relational atoms.
     *
     * @param queryHead       the head of the input query.
     * @param relationalAtoms a list of relational atoms that the sum operator will work with.
     * @param childOperator   the child operator of the sum operator which are the lower levels of the tree that have been built up so far.
     * @param sumAggregate    a list of terms who sum or sum of products need to be determined from the relational atoms.
     * @param <T>             the type of the input relational atoms (could either be a singleton relational atom or a list of them).
     */
    private <T> void setSumAggregateAsRootOperator(final RelationalAtom queryHead, T relationalAtoms, Operator childOperator, List<Term> sumAggregate) {
        rootOperator = new SumOperator((List<RelationalAtom>) relationalAtoms,
                new ArrayList<>(queryHead.getTerms()),
                sumAggregate,
                childOperator);
    }

    /**
     * Returns the root operator of the query plan tree.
     *
     * @return the root operator of the query plan tree.
     */
    public Operator getRootOperator() {
        return rootOperator;
    }

}
