(ns flc-x.try
  "This namespace will be moved to flc-x/try."
  (:require [flc.process :as process :refer [process*]]
            [flc.component :as component]
            [flc.map-like :as m]
            [flc.utils :refer :all]))

(defn success
  "Constructor for successes."
  [x]
  {:success x})

(defn failure
  "Constructor for failures."
  [x]
  {:failure x})

(defn success? [x]
  (and (map? x)
       (contains? x :success)))

(defn failure? [x]
  (and (map? x)
       (contains? x :failure)))

(defmacro try-then [body then & more]
  `(let [foo# (try
                {::value ~body}
                ~@more)]
     (if (and (map? foo#)
              (contains? foo# ::value))
       (~then (::value foo#))
       foo#)))

(defn ->try [program start-failed dependencies-failed]
  (fn [& args]
    (if-let [failures (seq (filter failure? args))]
      (process* (failure (dependencies-failed {:args args :failures failures})))
      (try-then (apply program (map :success args))
                #(process/update-state % success)
                (catch Exception e
                  (process* (failure (start-failed {:args args :exception e}))))))))

(defn map-components [f components]
  (m/fmap-keyed (fn [name component]
                  (component/update-program component (=> f name (component/dependencies component))))
                components))

(defn try-all [components]
  (map-components (fn [program name deps]
                    (->try program
                           (fn [{:keys [args exception]}]
                             (ex-info "Component failed to start"
                                      {:reason :start
                                       :component name
                                       :args args
                                       :dependencies deps}
                                      exception))
                           (fn [{:keys [args]}]
                             (ex-info "Component had dependencies that failed to start"
                                      {:reason :dependency
                                       :component name
                                       :args args
                                       :dependencies deps
                                       :failures (for [[dep arg] (map vector deps args)
                                                       :when (failure? arg)]
                                                   dep)}))))
                  components))

(defn states->graph
  "Given the states from processes wrapped in `->try` (e.g. via `try-all`), return a failure graph represented by a map-like sequence, where the keys are the names of the failing components and the values are the names of the dependencies that caused it to fail (or empty if the component itself failed)."
  [states]
  (seq (for [[name state] states
             :when (failure? state)
             :let [e (:failure state)]]
         [name (:failures (ex-data e))])))
