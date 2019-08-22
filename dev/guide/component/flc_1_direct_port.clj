(ns guide.component.flc-1-direct-port
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

;; This is a direct port of the *component* system into a *flc* system.

(def temp-dir
  (program/lifecycle (fn [{:keys [dir prefix attributes path]}]
                       (if dir
                         (Files/createTempDirectory dir prefix (into-array FileAttribute attributes))
                         (Files/createTempDirectory prefix (into-array FileAttribute attributes))))
                     #(rm-rf! (.toFile %))))

(def log
  (program/lifecycle (fn [dir filename]
                       (->FileLog (io/writer (io/file (.toFile dir)
                                                      filename)
                                             :append true)))
                     #(.close (:file %))))

(def system
  {:temp-dir (kw-args/component temp-dir {:prefix "FLC-"} {})
   :log (component/component #(log % "log.txt") [:temp-dir])})

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
