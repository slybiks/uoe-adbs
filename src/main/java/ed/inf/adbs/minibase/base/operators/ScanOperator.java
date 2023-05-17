package ed.inf.adbs.minibase.base.operators;

import ed.inf.adbs.minibase.base.*;

import java.io.FileNotFoundException;
import java.io.File;
import java.lang.reflect.GenericDeclaration;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * A ScanOperator that reads tuples from a relation and creates a Tuple object for each record in the relation.
 * This operator returns one Tuple at a time.
 */
public class ScanOperator extends Operator {
    private RelationalAtom relationalAtom;
    private RelationalSchema relationalSchema;
    private Scanner scanner;

    /**
     * Constructs a ScanOperator with the specified relational schema and relational atom.
     *
     * @param relationalSchema The relational schema of the relation to be scanned.
     * @param relationalAtom   The relational atom that represents the part of the relation to be scanned.
     */
    public ScanOperator(RelationalSchema relationalSchema, RelationalAtom relationalAtom) {
        this.relationalAtom = relationalAtom;
        this.relationalSchema = relationalSchema;
        this.scanner = getScanner();
    }

    /**
     * Returns the next tuple in the relation, or null if the end of the relation is reached.
     *
     * @return The next tuple in the relation, or null if the end of the relation is reached.
     * @throws RuntimeException if an error occurs while scanning the records.
     */
    @Override
    public Tuple getNextTuple() {
        try {
            String nextLine = null;
            if (scanner.hasNextLine()) {
                nextLine = scanner.nextLine();
            }
            return (nextLine != null) ? createTupleObjectFromRecord(nextLine) : null;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("An error occurred while scanning the records");
        }
    }

    /**
     * Resets the scan operator to the beginning of the relation by resetting the scanner.
     */
    @Override
    public void reset() {
        this.scanner = getScanner();
    }

    /**
     * Returns the relational atom that represents the part of the relation to be scanned.
     *
     * @return The relational atom that represents the part of the relation to be scanned.
     */
    public RelationalAtom getRelationalAtom() {
        return relationalAtom;
    }

    /**
     * Returns the relational schema of the relation to be scanned.
     *
     * @return The relational schema of the relation to be scanned.
     */
    public RelationalSchema getRelationalSchema() {
        return relationalSchema;
    }

    /**
     * Creates a Tuple object from a record read from the relation file.
     *
     * @param record The record from the relation file
     * @return The Tuple object created from the record.
     * @throws RuntimeException if an error occurs while extracting terms from the record or creating the tuple.
     */
    private Tuple createTupleObjectFromRecord(final String record) {
        List<String> terms = new ArrayList<>();

        try (Scanner scanner = new Scanner(record)) {
            scanner.useDelimiter(",");
            while (scanner.hasNext()) {
                terms.add(scanner.next().trim().replaceAll("'", ""));
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("An error occurred while extracting terms from the record");
        }

        try {
            if (relationalSchema.getRelationTermTypes().size() != terms.size()) {
                throw new IllegalArgumentException("There is a mismatch in the number of terms found and the number of attributes defined in the schema for this relation");
            }

            List<Constant> relationalTerms = new ArrayList<>();
            for (int i = 0; i < relationalAtom.getTerms().size(); i++) {
                GenericDeclaration constantClass = relationalSchema.getRelationTermTypes().get(i);
                Constant constant = constantClass.equals(StringConstant.class) ? new StringConstant(terms.get(i)) : new IntegerConstant(Integer.parseInt(terms.get(i)));
                relationalTerms.add(constant);
            }
            return new Tuple(relationalTerms);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("An error occurred while creating the tuple");
        }
    }

    /**
     * Returns a scanner object for the relation file that contains the records for the current relational atom.
     * Performs a simple sanity check to verify if the relation file exists in the database, prior to creating a scanner object.
     *
     * @return Scanner object for the relation file.
     * @throws RuntimeException if there is an error while ceating the scanner object.
     */
    private Scanner getScanner() {
        try {
            File relationFile = new File(relationalSchema.getRelationFileLocation());

            String[] fileNameParts = relationFile.getName().split("\\.");

            String fileExtension = fileNameParts[1];
            String relationName = fileNameParts[0];

            boolean isFile = relationFile.isFile();
            boolean hasCsvExtension = fileExtension.equals("csv");

            boolean hasMatchingRelationName = relationName.equals(relationalSchema.getRelationName());
            boolean inFilesDirectory = relationFile.getParentFile().getName().equals("files");

            if (isFile && hasCsvExtension && hasMatchingRelationName && inFilesDirectory) {
                return new Scanner(relationFile);
            }
        } catch (FileNotFoundException e) {
            System.err.println("The file specified for scanning the relation is not valid. Check if the file exists in the given location");
            e.printStackTrace();
        }
        return null;
    }
}
