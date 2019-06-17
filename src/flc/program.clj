(ns flc.program
  (:require [flc.process :as process :refer [process]]))

(defn lifecycle [start stop]
  (fn [& args]
    (process (apply start args)
             stop)))

(defn clean [f]
  (lifecycle f (fn [_])))

(defn constant [x]
  (clean (constantly x)))

; (defn update-state [program f]
;   (comp #(process/update-state % f) program))
