package ed.inf.adbs.minibase.base;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Tuple {
    private List<Constant> relationalTerms;

    public Tuple(List<Constant> relationalTerms) {
        this.relationalTerms = relationalTerms;
    }

    public Tuple(List<Constant> relationalTerms1, List<Constant> relationalTerms2) {
        List<Constant> allTerms = new ArrayList<>();

        allTerms.addAll(relationalTerms1);
        allTerms.addAll(relationalTerms2);

        this.relationalTerms = allTerms;
    }

    public List<Constant> getRelationalTerms() {
        return relationalTerms;
    }

    @Override
    public String toString() {
        return this.relationalTerms.stream().map(Constant::toString).collect(Collectors.joining(","));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tuple tuple = (Tuple) o;
        return relationalTerms.equals(tuple.relationalTerms);
    }

    @Override
    public int hashCode() {
        return getRelationalTerms().hashCode();
    }
}
