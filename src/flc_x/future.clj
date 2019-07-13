(ns flc-x.future
  "This namespace will be moved to flc-x/future."
  (:refer-clojure :exclude [await])
  (:require [flc.process :as process :refer [process*]]
            [flc.component :as component]
            [flc.map-like :as m]))

(defn ->future [program]
  (fn [& args]
    (let [p (delay (apply program (map deref args)))]
      (process* (future (process/state @p))
                #(process/stop! @p)))))

(defn future-all [components]
  (m/fmap #(component/update-program % ->future) components))

(defn await
  "Takes the output of `start!` and blocks until all futures have been realized. Returns the input otherwise unchanged."
  [started]
  (doseq [process (m/vals started)
          :let [state (process/state process)]
          :when (future? state)]
    @state)
  started)
