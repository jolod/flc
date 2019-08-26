# _flc_ for _integrant_ users

This is a guide written for users experienced with _[integrant]_. I'm not particularly experienced with *integrant* myself, so feedback is appreciated.

[integrant]: https://github.com/weavejester/integrant

## Quick start

Replace

```clojure
(ns guide
  (:require [integrant.core :as integrant]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :as resp]))

(defmethod ig/init-key :adapter/jetty [_ {:keys [handler] :as opts}]
  (jetty/run-jetty handler (-> opts (dissoc :handler) (assoc :join? false))))

(defmethod ig/halt-key! :adapter/jetty [_ server]
  (.stop server))

(defmethod ig/init-key :handler/greet [_ {:keys [name]}]
  (fn [_] (resp/response (str "Hello " name))))

(def config
  {:adapter/jetty {:port 8080, :handler (ig/ref :handler/greet)}
   :handler/greet {:name "Alice"}})

(defn -main []
  (ig/init config))
```

with

```clojure
(ns guide
  (:require [flc-x.simple :refer [start! kw-component lifecycle]]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :as resp]))

(def jetty
  (lifecycle (fn [{:keys [handler] :as opts}]
               (jetty/run-jetty handler (-> opts (dissoc :handler) (assoc :join? false))))
             (fn [server]
               (.stop server))))

(def greet
  (lifecycle (fn [{:keys [name]}]
               (fn [_] (resp/response (str "Hello " name))))
             (fn [_])))

(def system
  {:adapter/jetty (kw-component jetty {:port 8080} {:handler :handler/greet})
   :handler/greet (kw-component greet {:name "Alice"} {})})

(defn -main []
  (ig/init config))
```

## Initializing and halting

Instead of multimethods you use the `flc.program/lifecycle` function (ex-exported by [flc-x/simple]). You can also use `flc.program/process` directly which `lifecycle` makes use of, or `flc.program/clean` instead of `lifecycle` when there is no clean-up needed (i.e. when you write `(lifecycle ... (fn [_]))`. However, to make it easier for you, the reader, I will stick to only using `lifecycle`.

If you want to keep the keyword argument style of *integrant*, also use [flc-x/kw-args] as above, but that is optional. (`flc-x.simple` re-exports `flc-x.kw-args/component` as `kw-component`.) There is no real reason to have keyword arguments in `greet`, but for `jetty` it is useful since it allows you to pass arbitrary configuration to the server implementation.

In general, translate

```clojure
(defmethod ig/init-key :foo [_ args]
  ...)

(defmethod ig/halt-key! :foo [_ state]
  ...)
```

to

```clojure
(def foo
  (lifecycle (fn [args]
               ...)
             (fn [state]
               ...)))
```

## Configuration

Assuming the definitions above, replace

```clojure
(def config
  {:foo {:config-x :X
         :a-dependency (ig/ref :bar)}})
```

with

```clojure
(def system
  {:foo (kw-component foo {:config-x :X}
                          {:a-dependency :bar})})
```

In [flc-x/kw-args], the configuration and the dependencies are separated.

### Configuration à la *integrant*

`system` can be built from a configuration map and an implementation map. Instead of using a special tag I will split the configuration values into two submaps: conf and deps.

```clojure
(def config
  {:adapter/jetty {:conf {:port 8080}
                   :deps {:handler :handler/greet}}
   :handler/greet {:conf {:name "Alice"}}})

(def impls
  {:adapter/jetty jetty
   :handler/greet greet})
```

`config` and `impls` can easily be combined to produce

```clojure
{:adapter/jetty (kw-component jetty {:port 8080} {:handler :handler/greet})
 :handler/greet (kw-component greet {:name "Alice"} {})})
```

which is the form that `flc.core/start!` expects.

## Derived keywords

With *integrant* you can do

```clojure
(derive :adapter/jetty :adapter/ring)
```

and then initialize and reference `:adapter/ring` instead of `:adapter/jetty`.

### For creating aliases

*flc* does not have derived keywords, but you can get the aliasing effect by defining

```clojure
(defn alias [k]
  (component (lifecycle identity (fn [_]))
             [k]))
```

and using it like

```clojure
(def system {:adapter/jetty ...
             :adapter/ring (alias :adapter/jetty)})
```

Since `system` is a map (or [map-like][flc.map-like]) you can `merge` (or `concat`) the aliases and the rest of the system, if you want that separation.

### For creating multiple instances of the same key/component

Instead of using composite keys like

```clojure
{[:adapter/jetty :example/web-1] {:port 8080, :handler #ig/ref :handler/greet}
 [:adapter/jetty :example/web-2] {:port 8081, :handler #ig/ref :handler/greet}
 :handler/greet {:name "Alice"}}
```

use

```clojure
{:example/web-1 (kw-component jetty {:port 8080} {:handler :handler/greet})
 :example/web-2 (kw-component jetty {:port 8081} {:handler :handler/greet})
 :handler/greet (kw-component greet {:name "Alice"})}
```

Just like in a `let` you can call the same function twice and bind the result to different names.

## Composite keys and references

There is no special treatment of vector keys in *flc*.

## Refsets

The example from *integrant*'s documentation for refsets is

```clojure
(defmethod ig/init-key :const/name [_ {:keys [name]}]
  name)

(defmethod ig/init-key :handler/greet-all [_ {:keys [names]}]
  (fn [_] (resp/response (str "Hello " (clojure.string/join ", " names)))))

(derive :const.name/alice :const/name)
(derive :const.name/bob   :const/name)

(def config
  {:handler/greet-all {:names #ig/refset :const/name}
   :const.name/alice  {:name "Alice"}
   :const.name/bob    {:name "Bob"}})
```

Similarly to aliases above, you can introduce a "refset" in our system through a lifecycle that just creates a vector of the dependencies when started.

```clojure
(defn function [f & deps]
  (component (lifecycle f (fn [_]))
             deps))

(defn constant [x]
  (function (constantly x)))

(def greet-all
  (lifecycle (fn [names]
               (fn [_] (resp/response (str "Hello " (clojure.string/join ", " names)))))
             (fn [_])))

(def system
  {:const.name/alice (constant "Alice")
   :const.name/bob (constant "Bob")
   :const.name (function vector :const.name/alice :const.name/bob)
   :handler/greet-all (component greet-all [:const.name])})
```

If desired, the `:const.name` item in the map can be calculated from the rest of the map and merged in. You could use `derive` like *integrant* does, or use naming conventions.

## Prepping

Since you in *flc* have control of all the data and implementations all the way, you can prep either the configuration itself, prep when building the system, or even prep one the system has been built. For instance, since `jetty` and `greet` above are just functions you can always prep *after* the system has been created. That last one might require some explanation.

If assuming that all your components use keyword arguments (i.e. takes a single map as argument), you can do

```clojure
(require '[flc.component :as component])

(defn prep-system [system preps]
  (reduce (fn [system [k prep]]
            (update system k component/update-program (fn [program]
                                                        (fn [m]
                                                          (program (merge prep m))))))
          system
          preps))

(-> config
    system
    (prep-system {:adapter/jetty {:port 8080}})
    start!)
```

Here we create new functions (what in *flc* are called programs) by simply creating an outer lambda over them. You can modify the arguments, the return value, or both. This fact is what makes *flc* truly extensible.

## Suspending and resuming

The previous section served as an introduction to the "advanced" technique of wrapping programs. I will now show how it can be done to add suspend/resume behavior to *flc* ad hoc. (See [dev/guide/integrant_suspend.clj] for a full example).

```clojure
(defn suspendable
  ([program]
   (suspendable program process/stop! (constantly program)))
  ([program suspend resumer]
   (suspendable program suspend resumer (fn [_])))
  ([program suspend resumer stop!]
   (fn program' [& args]
     (let [process (apply program args)]
       (merge {::suspend (fn []
                           (let [suspended (suspend process)]
                             (fn [& new-args]
                               (let [new-program (if (= args new-args)
                                                   (suspendable (resumer suspended) suspend resumer)
                                                   (do (stop! suspended)
                                                       program'))]
                                 (apply new-program new-args)))))}
              process)))))
```

`suspendable` takes a program, like `jetty` above, and a function `suspend` that returns a value which is passed to `resumer` if the arguments to the program are the same. `resumer` then returns a new program that will restore the state when called. For this program to again support suspend/resume it is again passed to `suspendable` before it is called. If the arguments are different then the process is fully stopped and then restarted.

The one-argument form of `suspendable` can be applied to any program to make it stop when suspending, and then simply restart when resuming.

If you want to make it "unsafe", i.e. allow some arguments to change, then you can add yet another argument to select/remove the argument you do/don't care about. However, that might not be necessary if you restructure your system--see below.

### The jetty example

Here is the jetty example from *integrant*'s documentation.

```clojure
(defmethod ig/init-key :adapter/jetty [_ opts]
  (let [handler (atom (delay (:handler opts)))
        options (-> opts (dissoc :handler) (assoc :join? false))]
    {:handler handler
     :server (jetty/run-jetty (fn [req] (@@handler req)) options)}))

(defmethod ig/halt-key! :adapter/jetty [_ {:keys [server]}]
  (.stop server))

(defmethod ig/suspend-key! :adapter/jetty [_ {:keys [handler]}]
  (reset! handler (promise)))

(defmethod ig/resume-key :adapter/jetty [key opts old-opts old-impl]
  (if (= (dissoc opts :handler) (dissoc old-opts :handler))
    (do (deliver @(:handler old-impl) (:handler opts))
        old-impl)
    (do (ig/halt-key! key old-impl)
        (ig/init-key key opts))))
```

Here the key feature is that the handler is allowed to change, but nothing else. However, the same effect can be achieved by rearranging the system a bit.

The webserver need not depend on the handler. It can be started without a handler (effectively in "suspended" mode) and then have another component, let's call it web-app, "install" the handler. It is the job of the web-app to uninstall the handler when stopping.

In *flc* it could look like this:

```clojure
(def webserver
  (lifecycle (fn [opts]
               (let [handler (atom (promise))
                     options (assoc opts :join? false)]
                 {:handler handler
                  :server (jetty/run-jetty #(@@handler %) options)}))
             #(.stop (:server %))))

(def web-app
  (lifecycle (fn [webserver handler]
               (deliver @(:handler webserver) handler)
               webserver)
             #(update % :handler reset! (promise))))

(def system
  {:webserver/options (constant {:port 8080})
   :webserver (component webserver [:webserver/options])
   :handler (constant (fn [_] (resp/response "Hello, world!")))
   :web-app (component web-app [:webserver :handler])})
```

Here I have changed around the dependencies. The webserver does not depend on a handler; instead, the web-app installs the handler and if the handler is updated (meaning it is first stopped, then replaced with a new value, and then started) then when the web-app must also be stopped (which uninstalls the handler from the webserver) and then restarted. Partial stop/start can be done using [flc-x/partial].

Notice how this change means that we do not need to encode special logic for realizing if we need to restart the webserver or not.

A downside is that we need to encode this logic explicitly. In *integrant* we can add this to the webserver component without changing the structure of the system. That is a double edged sword though, if we happen to encode a bug in the suspend/resume logic. By inverting the dependencies no extra logic is added, and thus less room for bugs.

## Loading namespaces

This is a non-issue in *flc*.

[flc-x/simple]: ../src/flc_x/simple.clj
[flc-x/log]: ../src/flc_x/log.clj
[flc-x/try]: ../src/flc_x/try.clj
[flc-x/future]: ../src/flc_x/future.clj
[flc-x/lazy]: ../src/flc_x/lazy.clj
[flc-x/partial]: ../src/flc_x/partial.clj
[flc-x/recurrent]: ../src/flc_x/recurrent.clj
[flc-x/component]: ../src/flc_x/component.clj
[flc-x/let-start]: ../src/flc_x/let_start.clj
[flc-x/kw-args]: ../src/flc_x/kw_args.clj
[flc.map-like]: ../src/flc/map_like.clj

[dev/guide/integrant_suspend.clj]: ../dev/guide/integrant_suspend.clj
