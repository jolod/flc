(ns guide.component.flc-3-higher-order
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

;; This is like flc-2-flattened except it uses composition to reduce the amount of "noise" in the system;
;; only the values of interest are added to the system.

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

(def system
  (merge (flc/constants {:log/filename "log.txt"})
         {:temp-dir (kw-args/component temp-dir {:prefix "FLC-"} {})
          :log/fullname (function #(io/file (.toFile %1) %2) [:temp-dir :log/filename])
          :log (component/component (program/compose (program/clean ->FileLog)
                                                     (closeable #(io/writer % :append true)))
                                    [:log/fullname])}))

(def processes
  (atom nil))

(comment
  (-> processes
      (swap! #(do (flc/stop! %)
                  (flc/start! system)))
      simple/state-map)

  (-> @processes
      simple/state-map
      :log
      (log! "Hi there again!\n")))
