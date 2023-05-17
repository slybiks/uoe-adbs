package ed.inf.adbs.minibase.datastructures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A multi-valued map implementation using a {@link HashMap} to store mappings from keys to lists of values.
 *
 * @param <K> The type of the keys in the map.
 * @param <V> The type of the values in the map.
 */
public class MultiMap<K, V> {
    // The underlying map that stores the mappings from keys to lists of values.
    private final Map<K, List<V>> map = new HashMap<>();

    /**
     * Associates the specified value with the specified key in this map.
     *
     * @param key   The key with which to associate the value.
     * @param value The value to be associated with the key.
     */
    public void put(K key, V value) {
        List<V> values = map.computeIfAbsent(key, k -> new ArrayList<>());
        values.add(value);
    }

    /**
     * Returns the list of values to which the specified key is mapped, or an empty list if this map contains no
     * mapping for the key.
     *
     * @param key The key whose associated values are to be returned.
     * @return The list of values to which the specified key is mapped, or an empty list if this map contains no mapping for the key.
     */
    public List<V> get(K key) {
        return map.getOrDefault(key, new ArrayList<>());
    }

    /**
     * Returns true if this map contains a mapping for the specified key.
     *
     * @param key The key whose presence in this map is to be tested.
     * @return true if this map contains a mapping for the specified key, false otherwise.
     */
    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    /**
     * Removes the mapping for the specified key if it is present and contains the specified value.
     *
     * @param key   The key whose mapping is to be removed.
     * @param value The value whose presence in the mapping is to be tested and removed if present.
     * @return true if the mapping was removed, false otherwise.
     */
    public boolean remove(K key, V value) {
        List<V> values = map.get(key);
        if (values == null) {
            return false;
        }
        boolean removed = values.remove(value);
        if (values.isEmpty()) {
            map.remove(key);
        }
        return removed;
    }

    /**
     * Removes all mappings from this map.
     */
    public void clear() {
        map.clear();
    }
}

