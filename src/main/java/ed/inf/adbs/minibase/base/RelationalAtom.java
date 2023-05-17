package ed.inf.adbs.minibase.base;

import ed.inf.adbs.minibase.Utils;

import java.util.List;
import java.util.Objects;

public class RelationalAtom extends Atom {
    private String name;

    private List<Term> terms;

    private SumAggregate sumAggregate = null;

    public RelationalAtom(String name, List<Term> terms) {
        this.name = name;
        this.terms = terms;
    }

    public void setSumAggregate(SumAggregate sumAggregate) {
        this.sumAggregate = sumAggregate;
    }

    public String getName() {
        return name;
    }

    public List<Term> getTerms() {
        return terms;
    }

    public SumAggregate getSumAggregate() { return sumAggregate; }

    @Override
    public String toString() {
        return name + "(" + Utils.join(terms, ", ") + ")";
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof RelationalAtom)) return false;

        RelationalAtom relationalAtom = (RelationalAtom) object;

        return relationalAtom.getName().equals(this.getName()) && relationalAtom.getTerms().equals(this.getTerms());
    }

    @Override
    public int hashCode() {
        int result = getName() != null ? getName().hashCode() : 0;
        result = 31 * result + (getTerms() != null ? getTerms().hashCode() : 0);
        return result;
    }
}
