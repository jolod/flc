(ns flc-x.partial
  "This namespace will be moved to flc-x/try."
  (:require [flc.process :as process]
            [flc.core :as core]
            [flc.component :as component :refer [component]]
            [flc.let-like :as let-like]
            [flc.map-like :as m]
            [flc-x.simple :as simple]
            [clojure.set :as set]))

(defn -core-start! [started components]
  (-> (m/fmap #(component (constantly %))
              started)
      (concat components)
      core/start!
      (m/select-keys (m/keys components))))

(defn subgraph [graph member? add edges vertices]
  {:pre [(ifn? edges)]}
  (loop [graph graph
         queue vertices]
    (if-let [[vertex & queue] (seq queue)]
      (if (member? graph vertex)
        (recur graph queue)
        (let [adjacent (edges vertex)]
          (recur (add graph vertex adjacent)
                 (into queue adjacent))))
      graph)))

(defn reach [edges vertices]
  (subgraph #{}
            contains?
            (fn [visited vertex _] (conj visited vertex))
            edges
            vertices))

(defn transpose [graph]
  (apply merge-with
         set/union
         (concat (for [[k] graph]
                   {k (set [])})
                 (for [[k vs] graph
                       v vs]
                   {v (set [k])}))))

(defn system [components]
  {:started []
   :components (->> components
                    simple/components
                    (m/fmap component/->let-like)
                    let-like/arrange
                    (m/fmap #(apply component %)))})

(defn started [system]
  (:started system))

(defn dependencies [components]
  (m/fmap component/dependencies components))

(defn component-names [system]
  (m/keys (:components system)))

(defn start+!
  ([system]
   (start+! system (component-names system)))
  ([{:as system :keys [components started]} names]
   (let [to-start (set/difference (reach (m/->map (dependencies components))
                                         names)
                                  (set (m/keys started)))
         new-started (->> components
                          (filter (comp to-start first))
                          (-core-start! started))]
     {:system (update system :started #(concat new-started %))
      :started new-started})))

(def start!
  (comp :system start+!))

(defn stop+!
  ([system]
   (stop+! system (component-names system)))
  ([{:as system :keys [components started]} names]
   (let [dependents (m/->map (transpose (dependencies components)))
         stop-names (set/intersection (reach dependents names)
                                      (set (map first started)))
         {started false to-stop true} (group-by #(contains? stop-names (first %)) started)]
     {:system (assoc system :started started)
      :stopped (doall (m/fmap process/stop! to-stop))})))

(def stop!
  (comp :system stop+!))

(defn restart+!
  ([system]
   (restart+! system (component-names system)))
  ([system names]
   (let [{:keys [system stopped]} (stop+! system names)]
     (start+! system (m/keys stopped)))))

(def restart!
  (comp :system restart+!))

(defn inject+!
  "Updates or adds the components to the system. Any components in `components` that have been started already will be restarted and so will any (transitively) depedent components that have been started. Returns the same as `start+!`."
  [system components]
  (let [components (m/->map (simple/components components))
        {:keys [system stopped]} (stop+! system (m/keys components))]
    (-> system
        (update :components (fn [old-components]
                              (reduce (fn [old-components component]
                                        (apply m/assoc old-components component))
                                      old-components
                                      components)))
        (start+! (m/keys stopped)))))

(def inject!
  (comp :system inject+!))
