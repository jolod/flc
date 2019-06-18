(ns flc-x.component
  "This namespace will be moved to flc-x/component."
  (:require [flc.program :as program]
            [flc.map-like :as m]
            [flc-x.kw-args :as kw-args]
            [com.stuartsierra.component :as component]))

(defn component
  "Turns an object that implements the Lifecycle protocol into an flc component."
  [record]
  (kw-args/component (program/lifecycle component/start component/stop)
                     record
                     (component/dependencies record)))

(defn system
  "Turns a system map into a sequence of flc components."
  [system-map]
  (m/fmap component system-map))
