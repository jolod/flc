(ns benchmark
  (:require [criterium.core :as criterium]
            [flc-x.graph :as graph :refer [fnk']]
            [plumbing.core :as plumbing :refer [fnk sum]]
            [plumbing.graph :as plumbing-graph]))

(defn stats [{:keys [xs] :as m}]
;   (assert (contains? m :xs))
  (let [n  (count xs)
        m  (/ (sum identity xs) n)
        m2 (/ (sum #(* % %) xs) n)
        v  (- m2 (* m m))]
    {:n n   ; count
     :m m   ; mean
     :m2 m2 ; mean-square
     :v v   ; variance
     }))

(def stats-graph
  {:n  (fnk' [xs]   (count xs))
   :m  (fnk' [xs n] (/ (sum identity xs) n))
   :m2 (fnk' [xs n] (/ (sum #(* % %) xs) n))
   :v  (fnk' [m m2] (- m2 (* m m)))})

(def stats-plumbing
  {:n  (fnk [xs]   (count xs))
   :m  (fnk [xs n] (/ (sum identity xs) n))
   :m2 (fnk [xs n] (/ (sum #(* % %) xs) n))
   :v  (fnk [m m2] (- m2 (* m m)))})

(defmacro huge-graph [n macro]
  (let [map-contents (mapcat identity
                             (for [[from to] (partition 2 1 (map #(str "a" %) (range n)))]
                               [(keyword to) `(~macro [~(symbol from)] ~(symbol from))]))]
    `(hash-map ~@map-contents)))

(defn set-deps [f dep]
  (vary-meta f
             (fn [meta]
               (update meta
                       :plumbing.fnk.impl/positional-info
                       assoc
                       1
                       [dep]))))

(defn huge-graph-plumatic [n]
  (let [f (fnk [x] x)]
    (into {}
          (for [[from to] (partition 2 1 (map #(keyword (str "a" %))
                                              (range n)))]
            [to (set-deps f from)]))))

(def benchmarks
  (concat #_[[:sum #(reduce + 0 (range 100))]]
          (let [m {:xs [1 2 3 6]}]
            [[:stats-fn #(stats m)]
             #_[:stats-plumbing #((plumbing.graph/compile stats-plumbing) m)]
             [:stats-plumbing-compiled (let [compiled (plumbing.graph/compile stats-plumbing)]
                                         #(compiled m))]
             [:stats-flc #((graph/compile stats-graph) m)]
             [:stats-flc-compiled (let [compiled (graph/compile stats-graph)]
                                    #(compiled m))]])
          (let [m {:a0 :init}
                graph-plumbing (huge-graph 100 fnk)
                graph-flc (huge-graph 100 fnk')]
            [#_[:huge-plumbing #((plumbing.graph/compile graph-plumbing) m)]
             [:huge-plumbing-compiled (let [compiled (plumbing.graph/compile graph-plumbing)]
                                        #(compiled m))]
             [:huge-flc #((graph/compile graph-flc) m)]
             [:huge-flc-compiled (let [compiled (graph/compile graph-flc)]
                                   #(compiled m))]])))

(defn test-compile []
;   (prn ((plumbing.graph/compile {:x (set-deps (fnk [a] a) :b)}) {:b 3}))
;   (prn)
;   (prn (huge-graph-plumatic 10))
;   (prn)
;   (prn ((plumbing.graph/compile (huge-graph-plumatic 10)) {:a0 :init}))
;   (prn ((graph/compile (huge-graph 10 fnk')) {:a0 :init}))
;   (prn (meta (set-deps (fnk [x y z] x)
;                        :whatever)))
  (let [doit (fn [graph-plumbing graph-flc]
               (let [n 100
                     m {:a0 :init}

                     _ (prn :plumbing-compile)
                     _ (time (plumbing.graph/compile graph-plumbing))
                     _ (time (doseq [_ (range n)] (plumbing.graph/compile graph-plumbing)))
                     compiled (time (plumbing.graph/compile graph-plumbing))
                     _ (prn :plumbing-call)
                     _ (time (compiled m))
                     _ (time (doseq [_ (range n)] (compiled m)))
                     _ (time (doseq [_ (range n)] (compiled m)))

                     _ (prn :flc-compile)
                     _ (time (graph/compile graph-flc))
                     _ (time (doseq [_ (range n)] (graph/compile graph-flc)))
                     compiled (time (graph/compile graph-flc))
                     _ (prn :flc-call)
                     _ (time (compiled m))
                     _ (time (doseq [_ (range n)] (compiled m)))
                     _ (time (doseq [_ (range n)] (compiled m)))
                     ]))]
    (let [graph-plumbing (huge-graph 2 fnk)
          graph-flc (huge-graph 2 fnk')]
      (prn 2)
      (doit graph-plumbing graph-flc))
    (let [graph-plumbing (huge-graph 10 fnk)
          graph-flc (huge-graph 10 fnk')]
      (prn 10)
      (doit graph-plumbing graph-flc))
    (let [graph-plumbing (huge-graph 100 fnk)
            graph-flc (huge-graph 100 fnk')]
        (prn 100)
        (doit graph-plumbing graph-flc))))

(defn test-jit-compiled []
  (let [compiled (graph/compile (huge-graph 100 fnk'))
        times (doall (for [n (range 1000)]
                       (do
                         (print (inc n) " ")
                         (time (compiled {:a0 :init})))))]))

(def the-test test-compile)

(defn -main [& args]
  (if (= "test" (first args))
    (the-test)
    (let [bench (if (= "quick" (first args))
                  #(criterium/quick-bench (%))
                  #(criterium/bench (%)))]
      (doseq [[name f] benchmarks]
        (println name)
        (bench f)))))
