(ns flc.component
  (:require [flc.map-like :as m]))

(defn component
  ([program]
   (component program nil))
  ([program dependencies]
   {::program program
    ::dependencies dependencies}))

(def program ::program)

(def dependencies ::dependencies)

(defn update-program [component f]
  (update component ::program f))

(def ->let-like (juxt program dependencies))
