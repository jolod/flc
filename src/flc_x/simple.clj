(ns flc-x.simple
  "This namespace will be moved to flc-x/simple."
  (:require [flc.process :as process]
            [flc.program :as program]
            [flc.component :as component]
            [flc.map-like :as m]
            [flc.core :as core]
            [flc-x.kw-args :as kw-args]))

(def process process/process)

(def lifecycle program/lifecycle)

(def constants core/constants)

(defn components
  "Takes a map-like sequence with [name component] elements where `component` is either a component or on the form `[program deps ...]`, and returns a map-like sequence components. The values following the program will be flattened one level and concatenated. In particular, both `[program :foo :bar]` and `[program [:foo :bar]]` work. Thus, this function can be used to ''normalize'' ways of writing components.

  For consistency and predictability also `[program [:foo] :bar]`, `[program [:foo] [:bar]]` etc work. While you could do `[program [[:foo] :bar]] to depend on components `[:foo]` and `:bar`, this is not the intended use. If you have a component named by a collection then for clarity prefer to use flc.component/component directly or write another high-level function more suited to your use case."
  [comps]
  (m/fmap (fn [component]
            (if (sequential? component)
              (let [[program & deps] component]
                (component/component program (seq (apply concat (map #(if (or (nil? %)
                                                                              (coll? %))
                                                                        %
                                                                        [%])
                                                                     deps)))))
              component))
          comps))

(defn start!
  "Calls `components` on the argument first. Do not use if you use collections as component names."
  [comps]
  (core/start! (components comps)))

(def states core/states)

(def stop! core/stop!)

(defn state-map
  "The composition of `flc.core/states` and `flc.map-like/->map`."
  [processes]
  (m/->map (core/states processes)))

(def kw-component kw-args/component)
