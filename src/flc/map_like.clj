(ns flc.map-like
  "A map-like data structure is an [association list](https://en.wikipedia.org/wiki/Association_list) in reverse order, meaning a sequence (anything `seq`able, including maps) that contain tuples (typically pairs). The first two values in the tuple are the key and value, and the rest are ignored but preserved as much as possible. The tuple may be of any type that can be destructured sequentially, and nil is treated as the nil key and the nil value.

  A map-like data structure is typically used because either order is important, the same key can occur multiple times with different values, or both.

  Construction is done using the regular seq functions. For instance, `concat` is the equivalent of `merge`. `count` gives the size (which is different from the number of distinct keys). There is a special `assoc` (and `dissoc`) function that respect order and makes sure there is only one item with the specified key, but if you find that you use `assoc` a lot you should probably use another data structure instead.

  Maps are preserved as much as possible for performance reasons. That means that if you do an operation that is polymorphic, such as `into`, you probably want to use `seq` or `->map` first so you know what the result will be."
  (:refer-clojure :exclude [keys vals select-keys update get assoc dissoc]))

(defn- -update-nth-seq [xs n f]
  (let [[x & xs'] xs]
    (if (pos? n)
      (cons x (-update-nth-seq xs' (dec n) f))
      (cons (f x) xs'))))

(defn update-nth [xs n f]
  {:pre [(or (sequential? xs)
             (nil? xs))]}
  (if (and (associative? xs)
           (counted? xs)
           (< n (count xs)))
    (clojure.core/update xs n f)
    (-update-nth-seq xs n f)))

(defn- asseq->map [asseq]
  ; Note, this is different from `into {}`, because `into` uses `conj` and `conj` has special treatment for vector tuples when conjoining with a map. The test suite fails if `(into {} asseq)` is used instead.
  ; (into {} (for [[k v] asseq]
  ;            [k v]))
  (reduce (fn [m [k v]]
            (clojure.core/assoc m k v))
          {}
          asseq))

(defn ->map
  "Turns a map-like data structure into a map. `->map` is the identity function for values that test true for `map?`.

  In contrast to `(into {} ...)`, this also works for entries that hold more or less than exactly two elements."
  [coll]
  (if (map? coll)
    coll
    (asseq->map coll)))

(defn fmap
  "Applies `f` to the value of every item in the map-like data structure `map-like`."
  [f map-like]
  (if (map? map-like)
    (reduce-kv #(clojure.core/assoc %1 %2 (f %3)) {} map-like)
    (map #(update-nth % 1 f) map-like)))

(defn fmap-keyed
  "Applies `f` to the value of every item in the map-like data structure `map-like`."
  [f map-like]
  (if (map? map-like)
    (reduce-kv #(clojure.core/assoc %1 %2 (f %2 %3)) {} map-like)
    (map #(update-nth % 1 (partial f (first %))) map-like)))

(defn keys [m]
  (if (map? m)
    (clojure.core/keys m)
    (seq (map first m))))

(defn vals [m]
  (if (map? m)
    (clojure.core/vals m)
    (seq (map second m))))

(defn select-keys [m ks]
  (let [ks (set ks)]
    (filter #(contains? ks (first %)) m)))

; (defn update [m k f & more]
;   (if (map? m)
;     (apply clojure.core/update m k f more)
;     (for [item m]
;       (if (= (first item) k)
;         (update-second item #(apply f % more))
;         item))))

; (defn get
;   "Like `clojure.core/get`, but returns a seq of values instead of just a value. If no keys match `k` then nil, or `d` if specified, is returned.

;   Note: `d` is not put as a single value in a seq, so `[:foo]` or equivalent should be used if you want `:foo` to be the default value if the key is absent. Similarly, `[]` or equivalent should be used if you want a true but empty seq as the default."
;   ([m k]
;    (vals (select-keys m [k])))
;   ([m k d]
;    (or (get m k)
;        d)))

(defn dissoc
  "Removes all items with the specified key."
  [m k]
  (filter #(not= k (first %)) m))

; (defn assoc
;   "Replaces the last occurance of the key with the specified value and removes all other. If the key does not exist then the item is appended. It is in fact allowed to associate more than just a value with a key, through `assoc m k v w`. Note that this means that you cannot associate more than one key at a time, in contrast with `clojure.core/assoc`. If `m` is a map then `clojure.core/map` is used, unless you pass more than just a value in which case it is turned into a seq and you get linear performance."
;   [m k v & more]
;   (if (and (map? m)
;            (nil? more))
;     (clojure.core/assoc m k v)
;     (let [item (cons' k v more)
;           [rev-tail rev-init] (split-with #(not= (first %) k)
;                                           (reverse m))]
;       (if-let [[_ & rev-init] (seq rev-init)]
;         (reverse (concat rev-tail [item] (dissoc rev-init k)))
;         (concat m [item])))))

(defn assoc
  "Replaces the last occurance of the key with the specified value and removes all other. If the key does not exist then the item is appended. It is in fact allowed to associate more than just a value with a key, through `assoc m k v w`. Note that this means that you cannot associate more than one key at a time, in contrast with `clojure.core/assoc`. If `m` is a map then `clojure.core/map` is used, unless you pass more than just a value in which case it is turned into a seq and you get linear performance."
  [m k v]
  (if (map? m)
    (clojure.core/assoc m k v)
    (let [item [k v]
          [rev-tail rev-init] (split-with #(not= (first %) k)
                                          (reverse m))]
      (if-let [[_ & rev-init] (seq rev-init)]
        (reverse (concat rev-tail [item] (dissoc rev-init k)))
        (concat m [item])))))
