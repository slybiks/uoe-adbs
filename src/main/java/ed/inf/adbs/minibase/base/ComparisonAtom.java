package ed.inf.adbs.minibase.base;

public class ComparisonAtom extends Atom {

    private Term term1;

    private Term term2;

    private ComparisonOperator op;

    public ComparisonAtom(Term term1, Term term2, ComparisonOperator op) {
        this.term1 = term1;
        this.term2 = term2;
        this.op = op;
    }

    public Term getTerm1() {
        return term1;
    }

    public Term getTerm2() {
        return term2;
    }

    public ComparisonOperator getOp() {
        return op;
    }

    private int compareValues(final Constant constant1, final Constant constant2) {
        if (!constant1.getClass().equals(constant2.getClass())) {
            throw new UnsupportedOperationException("The comparison atom expression contains constants with conflicting data types");
        }

        if (constant1.getClass().equals(IntegerConstant.class)) {
            return Integer.compare(((IntegerConstant) constant1).getValue(), ((IntegerConstant) constant2).getValue());
        } else if (constant1.getClass().equals(StringConstant.class)) {
            return ((StringConstant) constant1).getValue().compareTo(((StringConstant) constant2).getValue());
        } else {
            throw new UnsupportedOperationException("The type of constant used is not supported");
        }
    }

    public boolean evaluateComparisonCondition() {
        if (term1 instanceof Variable || term2 instanceof Variable) {
            throw new UnsupportedOperationException("Can't invoke this on the current atom unless both terms are constants");
        }

        Constant constant1 = (Constant) term1;
        Constant constant2 = (Constant) term2;

        if (getOp().equals(ComparisonOperator.EQ)) {
            return constant1.equals(constant2);
        } else if (getOp().equals(ComparisonOperator.NEQ)) {
            return !constant1.equals(constant2);
        } else {
            int comparisonResult = compareValues(constant1, constant2);
            switch (getOp()) {
                case GT:
                    return comparisonResult > 0;
                case GEQ:
                    return comparisonResult >= 0;
                case LT:
                    return comparisonResult < 0;
                case LEQ:
                    return comparisonResult <= 0;
                default:
                    throw new UnsupportedOperationException("Unsupported comparison operator type");
            }
        }
    }

    /**
     * Checks whether the comparison atom is contained in a given relational atom.
     *
     * @param relationalAtom the relational atom to check for containment.
     * @return true if the comparison atom is contained in the relational atom, false otherwise.
     */
    public boolean isContainedInRelationalAtom(final RelationalAtom relationalAtom) {
        Term term1 = this.getTerm1();
        Term term2 = this.getTerm2();

        // If both terms are constants, this turns into a truthy or falsy check like 1 != 1 or 3 > 12
        if ((term1 instanceof Constant) && (term2 instanceof Constant))
            return true;

        // Check containment of comparison terms in the current relational atom
        boolean term1InRelationalAtom = relationalAtom.getTerms().contains(term1) && term1 instanceof Variable;
        boolean term2InRelationalAtom = relationalAtom.getTerms().contains(term2) && term2 instanceof Variable;

        return term1InRelationalAtom || term2InRelationalAtom;
    }


    @Override
    public String toString() {
        return term1 + " " + op + " " + term2;
    }

}
