(ns flc.program
  (:require [flc.process :as process :refer [process]]))

(defn lifecycle
  "Returns a program that applies its arguments to the start function and start functions return value is the returned process' state. The stop function gets the state as its argument and the resulting return value is returned when stopping."
  [start stop]
  (fn [& args]
    (process (apply start args)
             stop)))

(defn clean
  "A program with no stop behavior; stopping it returns nil."
  [start]
  (lifecycle start (fn [_])))

(defn constant
  "The constant program. The state is always the given argument, and stopping does nothing and returns nil."
  [x]
  (clean (constantly x)))

(defn compose
  "Compose two or more programs. For two programs, `(compose g f)`, the state returned by `f` will be given as the argument to `g`, and the stop function will first run the stop function returned by `g` and then the stop function returned by `f`.

  If given no arguments then the identity program is returned. If given only one program then that program is returned. If given more than two arguments `compose` performs a right fold."
  ([]
   (clean identity))
  ([f]
   f)
  ([g f]
   (fn [& args]
     (let [f-process (apply f args)
           g-process (g (process/state f-process))]
       (process/update-stop g-process (constantly #(do (process/stop! g-process)
                                                       (process/stop! f-process)))))))
  ([h g & fs]
   (compose h (apply compose g fs))))
