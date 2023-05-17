package ed.inf.adbs.minibase.base;

import java.lang.reflect.GenericDeclaration;
import java.util.List;

/**
 * Represents the schema of a relational table.
 */
public class RelationalSchema {

    private String relationName;
    private List<GenericDeclaration> relationTermTypes;
    private String relationFileLocation;

    /**
     * Constructor for RelationalSchema class.
     *
     * @param relationName         The name of the relation.
     * @param relationTermTypes    The types of the relation terms, using a GenericDeclaration to account for both IntegerConstant.
     *                             and StringConstant types.
     * @param relationFileLocation The file location of the relation.
     */
    public RelationalSchema(String relationName, List<GenericDeclaration> relationTermTypes, String relationFileLocation) {
        this.relationName = relationName;
        this.relationTermTypes = relationTermTypes;
        this.relationFileLocation = relationFileLocation;
    }

    /**
     * Returns the name of the relation.
     *
     * @return The name of the relation.
     */
    public String getRelationName() {
        return relationName;
    }

    /**
     * Returns the types of the relation terms.
     *
     * @return The types of the relation terms, using a GenericDeclaration to account for both IntegerConstant and StringConstant types.
     */
    public List<GenericDeclaration> getRelationTermTypes() {
        return relationTermTypes;
    }

    /**
     * Returns the file location of the relation.
     *
     * @return The file location of the relation.
     */
    public String getRelationFileLocation() {
        return relationFileLocation;
    }
}
