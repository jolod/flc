(ns flc.component
  (:require [flc.map-like :as m]))

; (defn component
;   ([program]
;    (component program nil))
;   ([program dependencies]
;    [program dependencies]))

; (def program first)

; (def dependencies second)

; (defn update-program [component f]
;   (m/update-first component f))

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
