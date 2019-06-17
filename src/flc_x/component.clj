(ns flc-x.component
  "This namespace will be moved to flc-x/component."
  (:require [flc.program :as program]
            [flc-x.kw-args :as kw-args]
            [com.stuartsierra.component :as component]))

(defn component [record]
  (kw-args/component (program/lifecycle component/start component/stop)
                     record
                     (component/dependencies record)))
