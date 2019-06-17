(ns flc-x.log
  "This namespace will be moved to flc-x/log."
  (:require [flc.process :as process]
            [flc.component :as component]
            [flc.utils :refer [=>]]
            [flc.map-like :as m]))

(defn log-program [program {:keys [starting! started! stopping! stopped!] :as fns}]
  (fn [& args]
    (when starting!
      (starting! args))
    (let [process (apply program args)]
      (when started!
        (started! (process/state process)))
      (process/wrap-stop process (fn [stop]
                                   (when stopping!
                                     (stopping! (process/state process)))
                                   (let [ret (stop)]
                                     (when stopped!
                                       (stopped! ret))
                                     ret))))))

(defn log-component [component fns]
  (let [[name component] component]
    [name (component/update-program component (=> log-program (m/fmap #(partial % name) fns)))]))

(defn log-all [fns components]
  (map #(log-component % fns) components))

(defn fns [f]
  (into {}
        (for [k [:starting! :started! :stopping! :stopped!]]
          [k (fn [& args]
               (f [k args]))])))
