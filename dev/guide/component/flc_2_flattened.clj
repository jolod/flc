(ns guide.component.flc-2-flattened
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

;; This is like flc-1-direct-port except the internal variables have been externalized and put into the system.
;; That means that they are seen in the system and can be inspected at runtime.

;; Importantly, :log/fullname is a computed value, it is not just configuration.

(def temp-dir
  (program/lifecycle (fn [{:keys [dir prefix attributes path]}]
                       (if dir
                         (Files/createTempDirectory dir prefix (into-array FileAttribute attributes))
                         (Files/createTempDirectory prefix (into-array FileAttribute attributes))))
                     #(rm-rf! (.toFile %))))

(defn closeable [f]
  (program/lifecycle f #(.close %)))

(def system
  {:temp-dir (kw-args/component temp-dir {:prefix "FLC-"} {})
   :log/filename (component/component (program/constant "log.txt"))
   :log/fullname (component/component (program/clean #(io/file (.toFile %1) %2)) [:temp-dir :log/filename])
   :log/file (component/component (closeable #(io/writer % :append true)) [:log/fullname])
   :log (component/component (program/clean ->FileLog) [:log/file])})

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
      (log! "Hi there again!\n"))

  (-> @processes
      simple/state-map
      :log
      :file
      .flush))
