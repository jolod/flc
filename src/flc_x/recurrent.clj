(ns flc-x.recurrent
  "This namespace will be moved to flc-x/recurrent."
  (:require [flc.map-like :as m]
            [flc.component :as component]))

(defn recurrent [f]
  {:pre [(ifn? f)]}
  (vary-meta f assoc ::recurrent? true))

(defn recurrent? [x]
  (::recurrent? (meta x)))

(defn step'
  "Takes a map-like sequence of components in `components` and a map-like sequence of programs (presumably from `process/stop!`) in `stopped`. `stopped` should be in reverse order compared to `components`. If the program in `stopped` is recurrent, then the program in `components` is replaced by the program in `stopped`, and the new components are returned as the first value in the returned tuple. If there are any programs in `stopped` that cannot be matched up with `components` a then those programs are returned as the second value in the return tuple (otherwise the second value is nil)."
  [components stopped]
  (loop [components (reverse components)
         stopped stopped
         result nil]
    (if-let [[[name :as component] & components'] (seq components)]
      (if-let [[[name' program] & stopped'] (seq stopped)]
        (if (= name name')
          (recur components'
                 stopped'
                 (cons (if (recurrent? program)
                         (m/update-nth component 1 #(component/update-program % (constantly program)))
                         component)
                       result))
          (recur components'
                 stopped
                 (cons component result)))
        [(into result components) (seq stopped)])
      [result (seq stopped)])))

(def step
  (comp first step'))
