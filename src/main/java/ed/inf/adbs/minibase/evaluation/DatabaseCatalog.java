package ed.inf.adbs.minibase.evaluation;

import ed.inf.adbs.minibase.base.IntegerConstant;
import ed.inf.adbs.minibase.base.RelationalSchema;
import ed.inf.adbs.minibase.base.StringConstant;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.GenericDeclaration;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class DatabaseCatalog {
    /**
     * Singleton database catalog object which contains all the relational schemas, the path to the database directory,
     * and the schema file path.
     */
    private static DatabaseCatalog catalog;

    private Map<String, RelationalSchema> schemas = new HashMap<>();
    private String databaseDirectory;
    private String schemaFilePath;

    /**
     * Private constructor for the DatabaseCatalog class.
     */
    private DatabaseCatalog() {
    }

    /**
     * Thread-safe implementation of the singleton pattern for initialising the database catalog.
     *
     * @return The singleton instance of the DatabaseCatalog.
     */
    public static DatabaseCatalog getCatalog() {
        if (catalog == null) {
            synchronized (DatabaseCatalog.class) {
                if (catalog == null) {
                    catalog = new DatabaseCatalog();
                }
            }
        }
        return catalog;
    }

    /**
     * Loads the relations from the schema and creates a RelationalSchema object for each relation, storing them in the catalog's schemas map.
     *
     * @param databaseDirectory the path to the database directory.
     */
    public void initialise(final String databaseDirectory) { // Loads the relations from the schema
        this.databaseDirectory = databaseDirectory;

        try {
            File schemaFile = new File(this.getSchemaFilePath());
            Scanner schemaFileScanner = new Scanner(schemaFile);

            while (schemaFileScanner.hasNextLine()) {
                // Scan each line of schema.txt
                String line = schemaFileScanner.nextLine();

                // Split the line of text read from schema.txt by spaces to extract the specification for each relation
                // Store each specification in an array
                String[] schemaDescriptorArray = line.split(" ");

                // Determine the relation name
                final String relationName = Arrays.stream(schemaDescriptorArray)
                        .findFirst()
                        .orElseThrow(() -> new NoSuchElementException("Relation not found. Please check the schema definition"));

                // Determine the relation term types which can either be ints or strings
                // These are abstracted using a GenericDeclaration
                final List<GenericDeclaration> relationTermTypes = Arrays.stream(schemaDescriptorArray)
                        .skip(1)
                        .map(this::getTermFromSchemaTermTypeDefinition)
                        .collect(Collectors.toList());

                // Get the relation file path
                final String relationFilePath = this.getRelationFilePath(relationName);

                RelationalSchema relationalSchema = new RelationalSchema(relationName, relationTermTypes, relationFilePath);
                this.getSchemas().put(relationName, relationalSchema);
            }
        } catch (FileNotFoundException e) {
            System.err.println("An error occurred while trying to read the database schema. Either the schema file is nonexistent, or an incorrect file path has been specified");
            e.printStackTrace();
        }
    }

    /**
     * Returns the GenericDeclaration type for a given term type definition specified in the schema.
     *
     * @param termTypeDefinition the term type definition as specified in the schema.
     * @return a GenericDeclaration representing the type of the term.
     * @throws IllegalArgumentException if the specified type is invalid.
     */
    private GenericDeclaration getTermFromSchemaTermTypeDefinition(final String termTypeDefinition) {
        switch (termTypeDefinition) {
            case "string":
                return StringConstant.class;
            case "int":
                return IntegerConstant.class;
            default:
                throw new IllegalArgumentException("The specified type is invalid. Please check the schema definition");
        }
    }

    /**
     * Builds and returns the path to a given relation file.
     *
     * @param relationName the name of the relation for which the file path needs to be generated.
     * @return the path to the relation file as a string.
     */
    public String getRelationFilePath(final String relationName) {
        return Paths.get(databaseDirectory, "files", relationName + ".csv").toString();
    }

    /**
     * Builds and returns the path to the database schema file.
     *
     * @return A string representing the path to the database schema file.
     */
    public String getSchemaFilePath() {
        schemaFilePath = Paths.get(databaseDirectory, "schema.txt").toString();
        return schemaFilePath;
    }

    /**
     * Returns a map of all the relational schemas, where the keys are the relation names and the values are the corresponding RelationalSchema objects.
     *
     * @return a map of all the relational schemas.
     */
    public Map<String, RelationalSchema> getSchemas() { // Returns all the relational schemas
        return schemas;
    }
}
