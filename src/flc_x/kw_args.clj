(ns flc-x.kw-args
  "This namespace will be moved to flc-x/kw-args."
  (:require [flc.map-like :as m]
            [flc.component :as component]))

(defn component [program init deps-map]
  (component/component (fn kw-program [& args]
                         (program (into init (map vector (m/keys deps-map) args))))
                       (m/vals deps-map)))
