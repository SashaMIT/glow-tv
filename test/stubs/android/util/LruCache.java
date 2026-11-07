package android.util;

import java.util.HashMap;
import java.util.Map;

/** Replaces android.jar's LruCache, which throws "Stub!", while the tests run on the computer. */
public class LruCache<K, V> {
    private final Map<K, V> map = new HashMap<>();

    public LruCache(int maxSize) {}

    public final V get(K key) { return map.get(key); }
    public final V put(K key, V value) { return map.put(key, value); }
    public final void evictAll() { map.clear(); }
    protected int sizeOf(K key, V value) { return 1; }
}
