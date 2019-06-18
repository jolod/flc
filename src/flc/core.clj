(ns flc.core
  (:require [flc.let-like :as let-like]
            [flc.process :as process :refer [process]]
            [flc.program :as program]
            [flc.component :as component :refer [component]]
            [flc.map-like :as m]))

(defn constants
  "Takes a map-like sequence of names and values, and returns a map-like sequence of components where the programs has the values as states."
  [m]
  (m/fmap #(component (program/constant %))
          m))

(defn -processify
  "Takes a sequence of computations on the form [name [f names]] and updates the function to extract the state from all arguments.

  Subject to name change."
  [components]
  (m/fmap (fn [[f deps]]
            [(fn [& args]
               (apply f (map process/state args)))
             deps])
          components))

(defn -map-state-from-args [component]
  (component/update-program component
                            #(fn [& args]
                               (apply % (map process/state args)))))

(defn ->let-like
  "Turns a sequence of components into a sequence of computations that works with e.g. `arrange` and `run` from `flc.let-like`."
  [components]
  (->> components
       (m/fmap -map-state-from-args)
       (m/fmap component/->let-like)))

(defn start! [components]
  (->> components
       ->let-like
       let-like/arrange
       let-like/run
       :results))

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
