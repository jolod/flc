(ns flc-x.graph
  "This namespace will be moved to flc-x/graph, or be removed altogether."
  (:refer-clojure :exclude [compile])
  (:require [flc.program :as program]
            [flc.let-like :as let-like]
            [flc.map-like :as m]
            [clojure.set :as set]))

(defmacro fnk' [args expr]
  `[(fn ~args ~expr) ~(mapv keyword args)])

(defn constants [m]
  (m/fmap (comp vector constantly) (m/->map m)))

(defn compile [computations]
  (let [all-deps (mapcat second (let-like/dependencies computations))
        missing (set/difference (set all-deps) (set (keys computations)))
        ; Note: Prepending and dropping dummy computations works because let-like/arrange is stable.
        computations (concat (map vector missing) ; Note: Only the key; the rest will be nil during destructuring.
                             computations)
        computations (let-like/arrange computations)
        computations (drop (count missing) computations)]
    (fn [m]
      (->> computations
           (let-like/run m)
           :results
           m/->map))))
