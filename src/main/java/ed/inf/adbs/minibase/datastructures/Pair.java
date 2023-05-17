package ed.inf.adbs.minibase.datastructures;

/**
 * A simple generic class to store a pair of values.
 *
 * @param <T1> the type of the first value in the pair.
 * @param <T2> the type of the second value in the pair.
 */
public class Pair<T1, T2> {
    private final T1 first;
    private final T2 second;

    /**
     * Creates a new Pair with the specified values.
     *
     * @param first  the first value in the pair.
     * @param second the second value in the pair.
     */
    public Pair(T1 first, T2 second) {
        this.first = first;
        this.second = second;
    }

    /**
     * Returns the first value in the pair.
     *
     * @return the first value in the pair.
     */
    public T1 getFirst() {
        return first;
    }

    /**
     * Returns the second value in the pair.
     *
     * @return the second value in the pair.
     */
    public T2 getSecond() {
        return second;
    }
}

