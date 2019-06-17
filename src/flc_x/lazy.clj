(ns flc-x.lazy
  "This namespace will be moved to flc-x/lazy."
  (:require [flc.process :as process :refer [process*]]
            [flc.component :as component]
            [flc.map-like :as m]))

(defn lazy-program [program]
  (fn [& args]
    (let [p (delay
             (apply program (map deref args)))]
      (process* (delay (process/state @p))
                #(when (realized? p)
                   (process/stop! @p))))))

(defn strict-program [program]
  (fn [& args]
    (let [p (apply program (map deref args))]
      (process* (atom (process/state p))
                #(process/stop! p)))))

(defn lazy [component]
  (component/update-program component lazy-program))

(defn strict [component]
  (component/update-program component strict-program))

(defn remove-unrealized
  "Takes the output from `start!` and removes processes which have delayed unrealized state."
  [started]
  (for [[name process] started
        :let [state (process/state process)]
        :when (or (not (delay? state))
                  (realized? state))]
    [name process]))
