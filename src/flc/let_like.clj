(ns flc.let-like
  (:require [clojure.set :as set]
            [flc.utils :refer [=>]]
            [flc.map-like :as m]))

(defn stable-dependency-sort
  "Accepts a sequence of `[node incoming]` where `incoming` is a sequence of nodes. Topologically sorts the nodes so that any node `u` that is incoming to (points to) another node `v` occurs before `v`. The order is stable with regards to the order of the first value in the tuple (`node`). If a node occurs as key multiple times then the last occurance takes precendence (see `flc.map-like/->map`).

  Returns a tuple of sorted nodes and a map of ambiguous nodes. The keys in the map are the nodes that could not be sorted, either because they have a node (transitively) pointing to them which is not defined in the input, or because they are part of a cycle. The values are the corresponding `incoming` values (turned into sets).

  Example: `[[:x], [:z], [:xy [:y :x]], [:y], [:rec [:rec]], [:foo [:bar]]]` becomes `[[:x :z :y :xy], {:rec #{:rec} :foo #{:bar}}]`."
  [incoming-adjacents]
  (let [node->incoming (m/->map (m/fmap set incoming-adjacents))
        node->outgoing (apply merge-with
                              set/union
                              (for [[k vs] node->incoming
                                    v vs]
                                {v (set [k])}))
        indices (m/->map (map vector
                              (m/keys incoming-adjacents)
                              (range)))
        with-index (juxt #(get indices % 0) identity)]
    (loop [sorted []
           queue (into (sorted-map)
                       (for [[to idx] indices
                             :when (empty? (node->incoming to))]
                         [idx to]))
           node->incoming node->incoming]
      (if-let [[index node] (first queue)]
        (let [adjacents (get node->outgoing node)
              node->incoming (reduce (=> update disj node)
                                     (dissoc node->incoming node)
                                     adjacents)]
          (recur (conj sorted node)
                 (into (dissoc queue index)
                       (->> adjacents
                            (filter (comp empty? node->incoming))
                            (map with-index)))
                 node->incoming))
        [sorted
         (when (seq node->incoming)
           node->incoming)]))))

(defn dependencies
  "Maps a sequence of computations into a sequence of dependencies.

  Example: `[[:x [f]], [:y [g [:x]]]] becomes [[:x nil], [:y [:x]]]`."
  [computations]
  (m/fmap second computations))

(defn arrange
  "Sorts a map-like sequence of computations into a sequence that can be executed sequentially. A computation has the form `[name [f deps]]` where `name` is any value, `f` is a function and `deps` is a sequence of names refering to other computations. (If there are more than one computation with the same name, then all but the last of them are discarded.)

  The order of computations is preserved as much as possible, so if the sequence of computations are already listed in a way that would work in a `let` binding then that order is preserved. If not then dependencies are ''moved up'' as much as needed. This yields predictable execution sequences. (If a map is passed then there is no guarentee on the order, other than that dependencies will occur before they are needed.)

  If there are any circular or missing dependencies then an exception is thrown. `ex-data` of the exception contains all computations that could be sorted in `:sorted` and a map of the problematic dependencies is in `:ambiguous` and the full dependency sequence is in `:dependencies`."
  [computations]
  (let [deps (dependencies computations)
        [order ambiguous] (stable-dependency-sort deps)]
    (when ambiguous
      (throw (ex-info "Ambiguous ordering"
                      {:sorted order
                       :ambiguous ambiguous
                       :dependencies deps})))
    (map (juxt identity (m/->map computations)) order)))

(defn bind*
  "Considered private for now."
  [{:keys [results env]} binding']
  (let [[name [func arg-names]] binding'
        result (apply func (map #(get env %) arg-names))]
    {:results (conj results [name result])
     :env (assoc env name result)}))

(defn run
  "Takes a sequence of bindings, where a binding has the form `[name [f names]]`, and returns a map-like *vector* with elements `[name result]` where `result` is the result from applying the corresponding function `f` with the results from the bindings named in `names`. If a name occurs multiple times then shadowing is applied, just like in a `let` binding. In contrast to a `let` binding, if a name has not been bound before being used as a dependency then it will resolve to `nil`.

  The return sequence is ordered to match `bindings`.

  Optionally, a map of names can be given to provide initial values."
  ([bindings]
   (run {} bindings))
  ([env bindings]
   (->> bindings
        (reduce bind*
                {:results []
                 :env env})
        :results)))

(defn missing
  "Takes a sequence of `[name names]` where `names` are the dependencies of `name` (such as given by the `dependencies` function). Returns a sequence of `[name missing-names]`, ordered as the input sequence, where `missing-names` is a set of the names that does not occur before `name`.

  Examples

      [[:x nil], [:y [:x]]] => [[:x #{}], [:y #{}]]
      [[:x nil], [:y [:x]]] => [[:y #{:x}]]
      [[:x nil], [:x [:x]]] => [[:x #{}], [:x #{}]]

  The last example shows that shadowing is supported."
  [dependencies]
  (->> (for [[name deps] dependencies]
         [name [(fn [& args]
                  (->> (map vector deps args)
                       (filter #(nil? (second %))) ; If a dependency is nil, it is missing.
                       (map first)
                       set)) ; Always return a set, i.e. not nil.
                deps]])
       run))

(defn complete?
  "Returns true if there are no missing dependencies in the given bindings."
  [bindings]
  (->> bindings
       dependencies
       missing
       m/vals
       (every? empty?)))
