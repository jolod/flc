(ns guide.component.component
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
  (:import [guide.component.common FileLog]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defrecord TempDir [dir prefix attributes path]
  sierra/Lifecycle
  (start [this]
    (if path
      this
      (assoc this :path (if dir
                          (Files/createTempDirectory dir prefix (into-array FileAttribute attributes))
                          (Files/createTempDirectory prefix (into-array FileAttribute attributes))))))
  (stop [this]
    (when path
      (rm-rf! (.toFile path)))
    (assoc this :path nil)))

(extend-type FileLog
  sierra/Lifecycle
  (start [this]
    (if (:file this)
      this
      (assoc this :file (io/writer (io/file (.toFile (:path (:dir this)))
                                            (:filename this))
                                   :append true))))
  (stop [this]
    (when-let [file (:file this)]
      (.close file))
    (assoc this :file nil)))

(defrecord LogComponent [dir filename log]
  sierra/Lifecycle
  (start [this]
    (prn "START!")
    (if log
      this
      (assoc this :log (->FileLog (io/writer (io/file (.toFile (:path dir))
                                                      filename)
                                             :append true)))))
  (stop [this]
    (when-let [file (:file log)]
      (.close file))
    (assoc this :log nil)))

(def system1
  {:temp-dir (map->TempDir {:prefix "FLC-"})
   :log (-> (map->FileLog {:filename "log.txt"}) (sierra/using {:dir :temp-dir}))})

(def system2
  {:temp-dir (map->TempDir {:prefix "FLC-"})
   :log (-> (map->LogComponent {:filename "log.txt"}) (sierra/using {:dir :temp-dir}))})

(def system (atom system2))

(comment
  (swap! system sierra/start-system)

  (:log (:log @system))

  (-> @system :log :log (log! "Hello!\n"))

  (swap! system sierra/stop-system))
