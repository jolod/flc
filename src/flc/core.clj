(ns flc.core
  (:require [flc.let-like :as let-like]
            [flc.process :as process]
            [flc.program :as program]
            [flc.component :as component]
            [flc.map-like :as m]))

(defn constants
  "Takes a map-like sequence of names and values, and returns a map-like sequence of components where the programs have the values as states."
  [m]
  (m/fmap #(component/component (program/constant %))
          m))

(defn map-programs
  "Rewrites the components so that the program is replaced by the return value of `f`, which takes the program as the argument."
  [f components]
  (m/fmap #(component/update-program % f) components))

(defn map-programs+
  "Like `update-programs` where each program is replaced by the return value of `f`, but `f` takes the name and the component instead of the program. The program is accessed via `flc.component/program` instead. This is useful when you need the name and/or the dependencies of a program to produce the new program."
  [f components]
  (m/fmap-keyed (fn [name component]
                  (component/update-program component (constantly (f name component))))
                components))

(defn ->let-like
  "Turns a sequence of components into a sequence of computations that works with e.g. `arrange` and `run` from `flc.let-like`."
  [components]
  (->> components
       (map-programs #(fn [& args]
                        (apply % (map process/state args))))
       (m/fmap component/->let-like)))

(defn start!*
  "Like `start!`, but does not arrange the components first, and thus support name shadowing."
  [components]
  (->> components
       ->let-like
       let-like/run
       rseq))

(defn start!
  "Arrange the map-like sequence of named components in dependency order (if necessary) and then runs each program with the arguments as specified by its dependencies. Returns a map-like sequence of named processes, in reverse start order."
  [components]
  (->> components
       ->let-like
       let-like/arrange
       let-like/run
       rseq))

(defn arrange
  "Arranged the map-like sequence of components in dependency order, such that the first component has no dependencies and the second one can make use of the first, etc."
  [components]
  (->> components
       (m/fmap (juxt identity component/dependencies))
       let-like/arrange
       (m/fmap first)))

(defn states
  "Takes the output of `start!` and returns a map-like sequence of states, in starting order (i.e. reverse input order)."
  [started]
  (reduce (fn [r [name process]]
            (cons [name (process/state process)] r))
          nil
          started))

(defn stop!
  "Takes the output from `start!` and runs all the stop functions in order; i.e. in reverse start order. Returns a map-like sequence of the return values of the stop functions, in the order performed."
  [started]
  (doall (m/fmap process/stop! started)))
