(ns flc-x.component-test
  (:require [expectations.clojure.test :refer :all]
            [flc-x.component :refer :all]
            [flc-x.simple :as simple]
            [flc.process :refer [process*]]
            [flc.core :as core]
            [com.stuartsierra.component :as component]))

(defrecord MyComponent [x log]
  component/Lifecycle
  (start [this]
    (swap! log conj :start)
    (assoc this :started true))
  (stop [this]
    (swap! log conj :stop)
    (dissoc this :started)))

(defn my-component []
  (map->MyComponent {:log (atom [])}))

(defexpect flc.component-test
  (let [system {:x [#(process* :X)]
                :my-component (-> (my-component) (component/using [:x]) component)}
        started (simple/start! system)
        states (into {} (core/states started))]
    (expect #{:x :my-component} (set (keys states)))
    (expect :X (-> states :x))
    (expect [:start] (-> states :my-component :log deref))))

(defexpect ->flc-test
  (let [system' (component/system-map :x :X
                                      :my-component (-> (my-component) (component/using [:x])))
        started (simple/start! (system system'))
        states (into {} (core/states started))]
    (expect #{:x :my-component} (set (keys states)))
    (expect :X (-> states :x))
    (expect [:start] (-> states :my-component :log deref))))
