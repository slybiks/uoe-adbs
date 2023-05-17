package ed.inf.adbs.minibase.base.operators;

import ed.inf.adbs.minibase.Utils;
import ed.inf.adbs.minibase.base.*;

import java.io.FileWriter;
import java.io.IOError;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The SumOperator is responsible for computing the sum of the (products of the)
 * provided product (sum aggregate) terms, for every group defined by the specified aggregate terms.
 * This operation is performed on the data supplied by the child operator.
 */
public class SumOperator extends Operator {
    private final Operator childOperator;

    private final List<RelationalAtom> relationalAtoms;
    private final List<Term> aggregateTerms;
    private final List<Term> sumAggregateTerms;

    private final Map<Tuple, Integer> tupleAggregateSum = new HashMap<>();
    // If there are no aggregate terms, simply use a placeholder tuple as the key
    private final Tuple NO_GROUP_BY_TERMS = new Tuple(new ArrayList<>());

    /**
     * Constructs a SumOperator with the specified relational atoms, group by aggregate terms, sum aggregate terms and child operator.
     *
     * @param relationalAtoms   the list of relational atoms that defines the schema of the tuples returned by this operator.
     * @param aggregateTerms    the list of terms to group by.
     * @param sumAggregateTerms the list of terms to calculate the sum of.
     * @param childOperator     the child operator to retrieve data from.
     * @throws IllegalArgumentException if the output aggregate variables contain non-existent input variables.
     */
    public SumOperator(List<RelationalAtom> relationalAtoms, List<Term> aggregateTerms, List<Term> sumAggregateTerms, Operator childOperator) {
        if (isRelationalAtomMissingSomeAggregateTermsSanityCheck(relationalAtoms, aggregateTerms) || isRelationalAtomMissingSomeSumTermsSanityCheck(relationalAtoms, sumAggregateTerms))
            throw new IllegalArgumentException("Output aggregate variables contain non-existent input variables");

        this.relationalAtoms = relationalAtoms;
        this.aggregateTerms = aggregateTerms;
        this.sumAggregateTerms = sumAggregateTerms;
        this.childOperator = childOperator;
    }

    /**
     * Retrieves the next tuple from this operator, computing the sum aggregate for each group.
     * This method blocks until a tuple is available from the child operator, and then reads
     * the entire output from the child operator to process the logic for computing a running sum
     * aggregate for each group defined by the given aggregate terms.
     *
     * @return returns null once all tuples have been read and processed.
     * This is because the core logic of the operator stores each aggregated tuple along with its aggregate sum (of products) in a map,
     * so there is no need to return any additional data.
     */

    @Override
    public Tuple getNextTuple() {
        Tuple nextChildTuple;

        while ((nextChildTuple = childOperator.getNextTuple()) != null) {
            Tuple aggregatedTuple;

            if (!aggregateTerms.isEmpty()) {
                aggregatedTuple = retrieveTupleAfterApplyingProjectionUtil(nextChildTuple, aggregateTerms);
            } else {
                aggregatedTuple = NO_GROUP_BY_TERMS;
            }

            Integer sumForAggregatedTuple = determineProductOfTermsForAggregatedTuple(nextChildTuple);

            int currentSum = tupleAggregateSum.getOrDefault(aggregatedTuple, 0);
            tupleAggregateSum.put(aggregatedTuple, currentSum + sumForAggregatedTuple);
        }

        return null;
    }

    /**
     * Resets the state of the child operator.
     */
    @Override
    public void reset() {
        childOperator.reset();
    }

    /**
     * Overrides the dump operator in the Operator class to handle the output writing in a different way.
     * This method is responsible for retrieving all the aggregated tuples by calling the getNextTuple() method
     * which handles the group by aggregation over the sum operator and then writes the output to a file or to console,
     * depending on the output stream set by the user.
     * <p>
     * The method iterates over the map of tuple aggregates and sums, and for each entry,
     * it retrieves the aggregated tuple and its corresponding sum.
     * If the aggregated tuple does not have any group by terms (i.e., it's just the sum aggregate),
     * it writes the sum value to the output stream.
     * Otherwise, it adds the sum as an additional term to the aggregated tuple and writes the tuple to the output stream.
     * If the output stream is a file, it writes each tuple to a new line, otherwise it prints each tuple to console.
     * At the end of the method, it flushes the output stream.
     * If there is an IOException while writing to the output stream, it throws an IOError with the corresponding error message.
     *
     * @return None.
     */

    @Override
    public void dump() {
        this.getNextTuple();

        FileWriter outputFileWriter = Utils.getFileWriter();

        try {
            for (Map.Entry<Tuple, Integer> entry : tupleAggregateSum.entrySet()) {
                Tuple aggregatedTuple = entry.getKey();
                Integer sum = entry.getValue();

                if (aggregatedTuple.equals(NO_GROUP_BY_TERMS)) {
                    String sumString = sum.toString();
                    if (outputFileWriter != null) {
                        outputFileWriter.write(sumString + System.lineSeparator());
                    } else {
                        System.out.println(sumString);
                    }
                } else {
                    IntegerConstant sumConstant = new IntegerConstant(sum);
                    aggregatedTuple.getRelationalTerms().add(sumConstant);

                    if (outputFileWriter != null) {
                        String aggregatedTupleString = Utils.join(aggregatedTuple.getRelationalTerms(), ", ");
                        outputFileWriter.write(aggregatedTupleString + System.lineSeparator());
                    } else {
                        System.out.println(aggregatedTuple);
                    }
                }
            }
            if (outputFileWriter != null) {
                outputFileWriter.flush();
            }
        } catch (IOException e) {
            System.err.println("Error writing tuples : " + e.getMessage());
            throw new IOError(e.getCause());
        }
    }

    /**
     * Checks if the given list of relational atoms contains all the aggregate terms specified in the input.
     *
     * @param relationalAtoms a list of relational atom objects to check for the presence of the aggregate terms.
     * @param aggregateTerms  a list of term objects representing the aggregate terms to search for.
     * @return true if there are no aggregate terms or if all the aggregate terms are present in the given list of relational atoms, false otherwise.
     */
    private boolean isRelationalAtomMissingSomeAggregateTermsSanityCheck(final List<RelationalAtom> relationalAtoms, final List<Term> aggregateTerms) {
        Set<Term> relationalTerms = relationalAtoms.stream().flatMap(relationalAtom -> relationalAtom.getTerms().stream()).collect(Collectors.toSet());
        if (aggregateTerms.isEmpty()) {
            return false;
        } else {
            return !relationalTerms.containsAll(aggregateTerms);
        }
    }

    /**
     * Performs a sanity check to verify if all the sum aggregate terms are present in the given list of relational atoms.
     *
     * @param relationalAtoms   the list of relational atoms to be checked for the presence of sum aggregate terms.
     * @param sumAggregateTerms the list of sum aggregate terms to be checked for their presence in the given list of relational atoms.
     * @return true if the provided list of sum aggregate terms is empty,
     * which is considered invalid as the sum operator cannot function without any terms to aggregate and,
     * false if all sum aggregate terms are constant values,
     * or if all the sum aggregate terms are present in the given list of relational atoms.
     */
    private boolean isRelationalAtomMissingSomeSumTermsSanityCheck(final List<RelationalAtom> relationalAtoms, final List<Term> sumAggregateTerms) {
        Set<Term> relationalTerms = relationalAtoms.stream().flatMap(relationalAtom -> relationalAtom.getTerms().stream()).collect(Collectors.toSet());
        if (sumAggregateTerms.isEmpty()) {
            return true;
        } else if (sumAggregateTerms.stream().allMatch(term -> term instanceof Constant)) {
            return false;
        } else {
            return !relationalTerms.containsAll(sumAggregateTerms.stream().filter(term -> !(term instanceof Constant)).collect(Collectors.toSet()));
        }
    }

    /**
     * This method retrieves a tuple that results after applying a projection with the given terms on the input tuple.
     * Under the hood, the group by variables are essentially a projection of a tuple.
     * Hence, this method can be used to retrieve the values of group by variables from the input tuple.
     * <p>
     * This method simply calls the static method {@code retrieveTupleAfterApplyingProjection}
     * in the {@link ProjectOperator} class with the given tuple, relational atoms, and terms as arguments.
     * <p>
     * This method can be broadly applied to two scenarios:
     * <ul>
     * <li> To retrieve a tuple containing only the aggregate terms or group by variables
     * that appear outside the sum operator in the query head for grouping purposes.
     * </li>
     * <li> To determine the values for the product terms in the sum aggregate from the original tuple.</li>
     * </ul>
     *
     * @param tuple The input tuple on which the projection is to be applied.
     * @param terms The list of terms to be projected.
     * @return The output tuple after the projection has been applied.
     */
    private Tuple retrieveTupleAfterApplyingProjectionUtil(final Tuple tuple, final List<Term> terms) {
        return ProjectOperator.retrieveTupleAfterApplyingProjection(tuple, relationalAtoms, terms);
    }

    /**
     * Determines the product of the sum aggregate terms for the given tuple after substitution.
     * If the sum aggregate contains only a constant value and no product terms, we can directly return that constant value.
     *
     * @param currentTuple the tuple for which the product of sum aggregate terms needs to be determined.
     * @return the product of the sum aggregate terms for the given tuple after substitution.
     * @throws IllegalArgumentException if the sum aggregate operator is empty.
     */
    private Integer determineProductOfTermsForAggregatedTuple(final Tuple currentTuple) {
        if (sumAggregateTerms.isEmpty())
            throw new IllegalArgumentException("The sum aggregate operator was found to be empty which is unexpected at this point");

        Integer runningProduct = 1;

        if (sumAggregateTerms.size() == 1 && sumAggregateTerms.get(0) instanceof Constant) {
            return ((IntegerConstant) sumAggregateTerms.get(0)).getValue();
        }

        Tuple substitutedSumAggregateTerms = retrieveTupleAfterApplyingProjectionUtil(currentTuple, sumAggregateTerms);

        for (Constant substitutedSumAggregateTerm : substitutedSumAggregateTerms.getRelationalTerms()) {
            runningProduct *= ((IntegerConstant) substitutedSumAggregateTerm).getValue();
        }

        return runningProduct;
    }
}
