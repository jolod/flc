(ns guide.component.flc-4-subsystem
  (:require [guide.component.common :refer :all]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as sierra]
            [flc.core :as flc]
            [flc.component :as component]
            [flc-x.kw-args :as kw-args]
            [flc.program :as program]
            [flc-x.simple :as simple]
            [flc.process :as process]
            [flc.map-like :as m]
            [flc.utils :refer [=>]])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

;; This is like flc-3-higher-order except is introduces a subsystem. The subsystem isolates all the components
;; needed by a particular component. It is effectively like having a `let` in `let`. Like
;;     (let [x 3
;;           y 4
;;           d (let [x2 (* x x)
;;                   y2 (* y y)]
;;               (Math/sqrt (+ x2 y2)))]
;;       ...)
;; Since a subsystem is just a sequence of components you can also apply any extensions to it, e.g. async start.
;;
;; The subsystem function automatically derives its dependencies from the subcomponents dependencies.
;; Note also that the subsystem does not order the subcomponents for you; it is really like a `let`.

(def temp-dir
  (program/lifecycle (fn [{:keys [dir prefix attributes path]}]
                       (if dir
                         (Files/createTempDirectory dir prefix (into-array FileAttribute attributes))
                         (Files/createTempDirectory prefix (into-array FileAttribute attributes))))
                     #(rm-rf! (.toFile %))))

(defn closeable [f]
  (program/lifecycle f #(.close %)))

(defn function [f deps]
  (component/component (program/clean f) deps))

(defn subsystem
  ([components]
   {:pre [(sequential? components)]}
   (subsystem (=> get (last (m/keys components)))
              components))
  ([f components]
   (let [names (set (m/keys components))
         deps (->> components
                   (m/vals)
                   (mapcat component/dependencies)
                   (filter #(not (contains? names %))))]
     (component/component (fn [& args]
                            (let [constants (flc/constants (map vector deps args))
                                  processes (flc/start! (concat constants components))]
                              (process/process* (f (simple/state-map processes))
                                                #(flc/stop! processes))))
                          deps))))

(defn system []
  (merge (flc/constants {:log/filename "log.txt"
                         :temp-dir/prefix "FLC-"})
         {:temp-dir (kw-args/component temp-dir {} {:prefix :temp-dir/prefix})
          :log/fullname (function #(io/file (.toFile %1) %2) [:temp-dir :log/filename])
          :log (subsystem [[:file (component/component (closeable #(io/writer % :append true)) [:log/fullname])]
                           [:log (function ->FileLog [:file])]])}))

(def processes
  (atom nil))

(comment
  (-> processes
      (swap! #(do (flc/stop! %)
                  (flc/start! (system))))
      simple/state-map)

  (-> @processes
      simple/state-map
      :log
      (log! "Hi there again!\n")))
