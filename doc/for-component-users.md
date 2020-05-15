# *flc* for *component* users

This is a guide written for users experienced with *[component]*. See also [dev/guide/component](../dev/guide/component/component.clj).

[component]: https://github.com/stuartsierra/component

## Quick start

Replace

```clojure
(ns guide
  (:require [com.stuartsierra.component :as sierra]))

(def system ...)

(defn -main []
  (sierra/start-system system))
```

with

```clojure
(ns guide
  (:require [com.stuartsierra.component :as sierra]
            [flc.core :as flc]
            flc-x.component))

(def system ...)

(defn -main []
  (flc/start! (flc-x.component/system system)))
```

Everything else is left the same, including `system`.

Now you can try writing new components using *flc*.

## Prefix

I will assume

```clojure
(ns guide
  (:require [com.stuartsierra.component :as sierra]
            [flc.core :as flc]
            [flc.program :as program]
            [flc.component :as component]))
```

for the rest of the document.

## How to define components

Let's assume you have

```clojure
(ns guide.component
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as sierra])
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
    (.write file message)))
```

and want to write two components: one that creates and cleans up a temporary directory, and one that logs messages to a file which will be put in the temporary directory. Perhaps not the most useful setup, but it serves as a small yet non-trivial example anyway.

### With *component*

Here I have written what I believe is idiomatic to *component*, e.g. idempotent `start` and `stop`.

```clojure
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

(def system
  (atom {:temp-dir (map->TempDir {:prefix "FLC-"})
         :log (-> (map->FileLog {:filename "log.txt"}) (sierra/using {:dir :temp-dir}))}))
```

Start with

```clojure
(swap! system sierra/start-system)
```

which returns

```clojure
{:temp-dir #guide.component.TempDir{:dir nil
                                    :prefix "FLC-"
                                    :attributes nil
                                    :path #object[sun.nio.fs.UnixPath 0x2ea5da17 "/tmp/FLC-17639739956320481659"]}
 :log #guide.component.FileLog{:file #object[java.io.BufferedWriter 0x358da46c "java.io.BufferedWriter@358da46c"]
                               :filename "log.txt"
                               :dir #guide.component.TempDir{:dir nil
                                                             :prefix "FLC-"
                                                             :attributes nil
                                                             :path #object[sun.nio.fs.UnixPath 0x2ea5da17 "/tmp/FLC-17639739956320481659"]}}}
```

### With *flc*

The direct port of the code above to *flc* would be:

```clojure
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
```

Start (and/or re-start) with

```clojure
(-> processes
    (swap! #(do (flc/stop! %)
                (flc/start! system)))
    simple/state-map)
```

which returns

```clojure
{:temp-dir #object[sun.nio.fs.UnixPath 0x6b3c897d "/tmp/FLC-5105516994676766552"]
 :log #guide.component.FileLog{:file #object[java.io.BufferedWriter 0x1afb9748 "java.io.BufferedWriter@1afb9748"]}}
```

## Discussion

Below I compare the differences between the *component* and *flc* versions.

### Stopping modified components

We immediately notice that *flc* separates started components from the recipe for starting them. In *flc*, the stop function that is called is the one that was defined during start. In contrast, if you change your *component* record while it is started, then the **new** stop method would be called but with the **old** record value. If you want to patch the stop behavior while running, the *component* way is what you want. If you want to evolve a component while it is running, then the *flc* way is what you want.

### Configuration

In *component* you often have something like

```clojure
(defn mk-system [config]
  {:temp-dir (map->TempDir (:temp-dir config))
   :log (-> (map->FileLog (:log config)) (sierra/using {:dir :temp-dir}))})

(def system
  (atom (mk-system {:temp-dir {:prefix "FLC-"}
                    :log {:filename "log.txt"}})))
```

which is pretty nice.

If you use keyword components with *flc* you can do the same; otherwise you have a couple of options.

#### Configuration à la *component*

Use `flc-x.kw-args/component` (also re-exported by `flc-x.simple` as `kw-component`), as `temp-dir` does above. The second argument to `flc-x.kw-args/component` serves as the configuration map in this case.

#### Configuration as components

One way is to add the configuration as components (not *a* component). You could do the same with *component*:

```clojure
; component

(defn mk-system [config]
  (merge config
         {:temp-dir (-> (map->TempDir {}) (sierra/using {:prefix :temp-dir/prefix}))
          :log (-> (map->FileLog (:log config)) (sierra/using {:filename :log/filename
                                                               :dir :temp-dir}))}))

(def system
  (atom (mk-system {:temp-dir/prefix "FLC-"
                    :log/filename "log.txt"})))
```

If you don't want to specify the configuration in a flat/namespaced way, then you can of course write a function that does the translation.

In *flc* it is basically the same, except you merge with `(constants config)` instead of just `config`. See below.

### No unpacking of maps

In the *component* version the `start` method for `FileLog` contains `(:path (:dir this))`. `:path` is the key in which the `TempDir` component stores the path of the temporary directory.

In the *flc* version that layer is not necessary.

This difference is also noticable when writing the log component. The way I did it above was to add the `Lifecycle` protocol to an existing record. This way I can use the log record directly. A downside of extending existing records is that you need to make use of Clojure 1.10's new feature of metadata extensions in order to have more than one `Lifecycle` implementation for a record.

The alternative would be to write a new component,

```clojure
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
```

This extra layer of nesting is visible to all consumers: all components that depend on `LogComponent` must first look up the field where `LogComponent` is "injected", and then look up the key `:log` key in that value in order to get the value that you can pass to `log!` and actually do useful work.

In other words, *component* components do not compose. Next, I show how *flc* programs *do* compose.

### Higher-order components

Since components in *flc* are so lightweight and does not wrap values in maps it is easy to create generic components such as `closeable` below, and write something like:

```clojure
(defn closeable [f]
  (program/lifecycle f #(.close %)))

(defn function [f deps]
  (component/component (program/clean f) deps))

(def system
  (merge (flc/constants {:log/filename "log.txt"
                         :temp-dir/prefix "FLC-"})
         {:temp-dir (kw-args/component temp-dir {} {:prefix :temp-dir/prefix})
          :log/fullname (function #(io/file (.toFile %1) %2) [:temp-dir :log/filename])
          :log (component/component (program/compose (program/clean ->FileLog)
                                                     (closeable #(io/writer % :append true)))
                                    [:log/fullname])}))
```

The result is something akin to `with-open`, and this enables us to not depend on the implementation details of `FileLog`, i.e. that it has a field `:file`. (You can imagine that `FileLog` is intended to be an implementation detail and returned by another function that just promises to return something that extends `Log`.)

## Exception handling

*component* has built-in support for exception handling. In *flc* exception handling is ad hoc. The *flc-x.try* extension wraps all programs in a *try*, and makes the state values represent success or failure. This is transparent to other programs; the wrapper also unpacks the arguments before calling the original program.

You can replicate the behavior of *component* with regards to exceptions, but out of the box with *flc-x.try* all components that do not depend on the failed component will start. You can then decide what to do; try to restart the failed component and it dependents, or stop the whole system.

When stopping you can similarly choose strategy. You might perhaps want to start as little as possible upon failure but stop as much as possible despite failures.
