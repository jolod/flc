(ns guide.integrant-suspend
  (:require [clojure.java.io :as io]
            [flc.core :as flc]
            [flc.component :as component :refer [component]]
            [flc-x.kw-args :as kw-args]
            [flc.program :as program]
            [flc-x.simple :as simple]
            [flc.process :as process]
            [flc.map-like :as m]
            [flc.utils :refer [=>]]))

(defn uuid [& _]
  (process/process* (java.util.UUID/randomUUID)))

(defn suspendable
  ([program]
   (suspendable program process/stop! (constantly program)))
  ([program suspend resumer]
   (suspendable program suspend resumer (fn [_])))
  ([program suspend resumer stop!]
   (fn program' [& args]
     (let [process (apply program args)]
       (merge {:suspend (fn []
                          (let [suspended (suspend process)]
                            (fn [& new-args]
                              (let [new-program (if (= args new-args)
                                                  (suspendable (resumer suspended) suspend resumer)
                                                  (do (stop! suspended)
                                                      program'))]
                                (apply new-program new-args)))))}
              process)))))

(defn suspend! [processes]
  (rseq (vec (m/fmap #((:suspend %)) processes))))

(defn subst-programs [components programs]
  {:pre [(sequential? components)]}
  (for [[[name component] [name' program]] (map vector components programs)]
    (do (assert (= name name') [name name'])
        [name (component/update-program component (constantly program))])))

(let [components (->> {:uuid (component (suspendable uuid identity #(constantly %)))}
                      flc/arrange)
      started (->> components
                   flc/start!)
      states (simple/state-map started)
      states' (->> started
                   suspend!
                   (subst-programs components)
                   flc/start!
                   simple/state-map)
      states'' (->> components
                    flc/start!
                    simple/state-map)]
  [(and (= states states')
        (not= states states''))
   states
   states'
   states''])
