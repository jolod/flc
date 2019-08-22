(ns guide.component.common
  (:require [clojure.java.io :as io]
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

(defn rm-rf! [file]
  (when (.isDirectory file)
    (run! rm-rf! (.listFiles file)))
  (io/delete-file file true))

(defprotocol Log
  (log! [_ message]))

(defrecord FileLog [file]
  Log
  (log! [_ message]
    (.write file message)
    (.flush file)))
