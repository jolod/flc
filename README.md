# flc - functional life cycles

*flc* is a functional formulation of life cycles, sporting ad hoc logging (*[flc-x/log]*), exception handling (*[flc-x/try]*), asynchronous start (*[flc-x/future]*), etc, and with minimal effort the dependency on `flc.core` can be removed. Through adapters you can mix and match components from *[stuartsierra/component][component]* and *[weavejester/integrant][integrant]*, this too, ad hoc.

## Synopsis

(Adapted from [James Reeve's presentation on Integrant](https://skillsmatter.com/skillscasts/9820-enter-integrant-a-micro-framework-for-data-driven-architecture-with-james-reeves).)

```clojure
(ns synopsis
  (:require [flc.program :refer [lifecycle]]
            [flc.process :refer [process]]
            [flc.core :refer [start! stop!]]
            ...))

(defn database [{:keys [uri]}]
  (lifecycle #(db/connect uri)
             db/stop))

(defn handler' [database]
  (process (fn [request] (resp/response (query-status database)))))

(defn jetty [{:keys [port]}]
  (lifecycle (fn [handler]
               (jetty/run-jetty handler {:port port}))
             #(.stop %)))

(defn system [config]
  {:database [(database (:database config))]
   :handler [handler' [:database]]
   :webserver [(jetty (:webserver config)) [:handler]]})

(def processes
  (atom nil))

(defn my-start! [config]
  (swap! processes (fn [processes]
                     (stop! processes)
                     (start! (system config)))))

; (my-start! {:database {:uri "jdbc:sqlite:"}
;             :webserver {:port 8080}})
```

## Rationale

Do we need another component library? Aren't *[component]* and *[integrant]* (or *[mount]*) enough?

Well, *flc* does a little more and a little less than *component* and *integrant*. *flc* can be used instead of *[plumatic/graph]* even.

The big selling point of *flc* is that the system is extensible by its open design. *flc* is not a framework like *component* and *integrant*; *flc* is just a collection of functions that you compose. Importantly, you can ad hoc wrap the components to enrich the life cycles.

For instance, with *flc* you can with a simple function add exception handling during start for all your components or logging of the start and stop functions, without changing the source code of the components. You can "import" components from *component* or *integrant*, and then add logging to them.

If you have a slow-running *graph*, where the computations dominate the function invocation overhead, you can use the drop-in replacement library *[flc-x/graph]* and add logging with *flc*. You can also make the computations run in futures to speed up the computation, while also logging.

Extensions like these can be stacked, and since wrapping e.g. a *component* component is just another extension, you can take your existing *component* system, turn it into an *flc* system (see `flc-x.component/system`), and then start it as if it was an *flc* system and make use of all the extensions (see below for a partial list), without any change to your existing component code.

## Description

*flc* is built on a couple of concepts:

* Process: a map holding a state value and a nullary stop function (that does something with the state).
* Program: a function that returns a process.
* Life cycle: a way of defining a program using a function to start the process and a unary function to stop the process.
* Component: a program that depends indirectly (through names) on other programs.

A sequence of named components can be arranged such that any program that is needed by another program is started before it is needed, and when started its state is passed to any dependents.

The primary user-facing functions in *flc* are re-exported through `flc-x.simple` (*[flc-x/simple]*) which satisfies the needs for simple systems. The functions in *flc* make for a flexible core to build more advanced systems with, see *Extensions* below.

See `docs/derivation.md` for a detailed background of the design of this library.

## Extensions

*flc* holds the core functionality, and extra behavior is added ad hoc.

The following extensions modify how programs work.

* [flc-x/log]: Add generic logging to all components.
* [flc-x/try]: One way of handling exceptions.
* [flc-x/future]: Add asynchronous start to components.
* [flc-x/lazy]: Make all or some components only start if used as dependencies.
* [flc-x/component]: Adapter for *[component]*.
* [flc-x/kw-args]: Support for components that use keyword arguments instead of positional arguments.

They can all be combined. For instance, you can log an asynchronously started component defined through *[component]*'s `Lifecycle` protocol with graceful handling of exceptions.

Next, the following extensions provide alternatives to `start!`/`stop!` and thus replace most of `flc.core`. All of the above extensions still work.

* [flc-x/partial]: Start/stop only some components (and their dependencies/dependents).
* [flc-x/recurrent]: Allow information to carry over between restarts.

Other:

* [flc-x/let-start]: Provides an alternative syntax for defining programs where an existing `let` statement which side effects that needs to be cleaned up can be enriched with clean-up behavior.

## Usage

### Defining programs

The basic usage is that programs are functions that use `process`.

```clojure
(defn program [... arguments ...]
  (process (...state...)
           (fn [state] ... clean up state ...)))
```

The state passed to `process` is the state that will be passed to the stop function.

If the program has configuration that can be partially applied as arguments, or you could add another function that takes the configuration and returns the program.

```clojure
(defn make-program [config]
  (fn [... arguments ...]
    (process (...state...)
             (fn [state] ... clean up state ...))))
```

In this scenario you might want to use `lifecycle`, which turns the above function into

```clojure
(defn make-program [config]
  (lifecycle (fn [... arguments ...] ...state...)
             (fn [state] ... clean up state ...)))
```

*flc* does not have an opinion on the return value of the stop function. In fact, it is up to you to optionally exploit the return value. The typical usage is to return nil, but there is no real reason to enforce that. See more under "Advanced usage" below.

### Defining components

No matter how you define programs, a named component consists of a name, a program, and a list of names of other components whose state (once started) will be passed as arguments to the program. For instance, if you have a program `bar` that needs the state of a component `:foo`, then you construct `[:bar (component bar [:foo])]`. I say construct, because `(seq {:foo (component foo), :bar (component bar [:foo])})` is `[[:foo (component foo)], [:bar (component bar [:foo])]]`. So it is convenient to use maps to define a set of components, but it is not necessary to provide a map and it some cases it can be better to not use a map; see `flc.core/start!` for more details.

There is a fair bit of nesting going on, but you could easily write a function that does this nesting for you, e.g.

```clojure
(defn flat->nested [& components]
  (for [[name f & deps] components]
    [name (component f deps)]))
```

and then you could write `(flat->nested [:foo foo] [:bar bar :foo])` instead.

These kinds of cosmetics is mostly left to the user. There are many ways to do it, for instance maybe you only care about maps and want to work with formats like `{:foo [foo] :bar [bar :foo]}` instead. That particular format is supported by `flc-x.simple/components`, which also optionally can have the dependencies as a collection.

### Starting a sequence of components (a.k.a. a system)

The `flc.core/start!` function takes a sequence of components. The sequence is first sorted so that any dependencies are started before they are needed. The dependent components then look up the state of the dependencies. For instance,

```clojure
(start! [[:foo (component foo)]
         [:bar (component bar [:foo])]])
```

is like

```clojure
(let [foo' (foo)
      bar' (bar :foo)])
```

However, in contrast to `let`, you can have components out of order,

```clojure
(start! [[:bar (component bar [:foo])]
         [:foo (component foo)]])
```

but you cannot have shadowing (but see `flc.let-like`).

Another important aspect is that if the components *need not* be reordered, then they *will not* be reordered, so you will have predictability in the start sequence. Below, `f` will always be executed before `g`.

```clojure
(start! [[:f (component f)]
         [:g (component g)]])
```

If you instead do

```clojure
(start! {:f (component f)
         :g (component g)})
```

the order depends on the map implementation which isn't defined.

### Inspecting the running state

`start!` returns the sequence of started components in reverse start order, structured as `[name process]`. The `states` function transforms that sequence into `[name state]` and reverses the order, so that it is in start order. If you want to look up states you can use `flc.map-like/->map` to turn it into a map, or use `state-map` from `flc-x.simple`.

### Stopping a system

The return value of `start!` can be passed directly to `stop!`. It runs through all the stop functions in given order, i.e. in reverse start order.

## Advanced usage

There are many "entry points" to *flc*. You decide how to construct programs and components. You can even write your on `start!` and/or `stop!` function. However, since programs are just functions you can go a long way by wrapping programs, and this is the main mechanism for enriching systems with more capabilities, e.g. asynchronous start or an adapter for *stuartsierra/component* components.

It is illustrative to look at how `flc.core/start!` works. The interesting work is actually performed in `flc.let-like/run`, but `flc.let-like` has no understanding of processes or how to extract state, so how can that work?

A simplified version of `start!`, assuming components are encoded using tuples and implemented without `flc.let-like` and which does not do sorting (but does support shadowing!), could be

```clojure
(defn start! [components]
  (reverse (reduce (fn [processes [name [program deps]]]
                     (let [args (map (comp :state (into {} processes))
                                     deps)]
                       (conj processes [name (apply program args)])))
                   []
                   components)))
```

Here `:state` is explicitly extracted from dependencies. If we want to be more general, we could move `:state` out as an argument:

```clojure
(defn start! [f-dep components]
  (reverse (reduce (fn [processes [name [program deps]]]
                     (let [args (map (comp f-dep (into {} processes))
                                     deps)]
                       (conj processes [name (apply program args)])))
                   []
                   components)))
```

and you can now write e.g. `(start! identity components)` to get a behavior that is more like a regular `let`. However, there is another way to get `:state` out of the `reduce` and that is in the opposite direction! `args` is only used by `program`, so we can change `program` to pull out `:state` from all its arguments. We would do that using a function like

```clojure
(fn [& args]
  (apply program (map :state args)))
```

and thus we get

```clojure
(defn start! [components]
  (let [components (for [[name [program deps]] components]
                     [name [(fn [& args]
                              (apply program (map :state args)))
                            deps]])]
    (reverse (reduce (fn [processes [name [program deps]]]
                       (let [args (map (into {} processes)
                                       deps)]
                         (conj processes [name (apply program args)])))
                     []
                     components))))
```

The `let`'s body can be refactored into a function, let's call it `run`, and we end up with

```clojure
(defn start! [components]
  (let [components (for [[name [program deps]] components]
                     [name [(fn [& args]
                              (apply program (map :state args)))
                            deps]])]
    (run components)))
```

Exactly the same principle is used for asynchronous start, exception handling, etc, except those extension also manipulate the return value.

### Asynchronous start

In *[flc-x/future]* there are functions that deal with asynchronous start through futures. Each program in the system is wrapped to do the start in a future, and all dependencies are consequently dereferenced.

Error handling in futures is unpleasant, so this can be composed with the functios in the next section.

### Error handling

In `flc-x.try` there are functions to deal with exceptions. The whole system should be transformed and each program will then return either `{:success state}` or `{:failure exception}`. The exception data contains information about the arguments and dependencies. If a dependency failed the (original) program will not run and a failure will be returned instead, containing information about which dependency failed.

It will thus seem like all components started successfully, but some states might be failures. `states->graph` can be used to inspect if and how any errors propagated. This can be used to e.g. implement retry logic.

Interestingly, the same approach can be used to do exception-free system stops. The return value from `start!` can be transformed so that the stop function is turned into a program, with the dependencies inverted. This way, if a stop fails then any dependencies won't be stopped--they will also "fail" to stop. The return value from `start!` applied to this stop plan will again contain information on which components that were not stopped and which components originally failed to stop.

If you instead just want to log any failed stops and continue to stop even the dependencies, then you can just write your custom `stop!` which loops over the started components and performs the stop call in a try/catch; no dependencies on *flc* needed. Or you can wrap all stop functions (or all programs before starting them) to perform the process' stop function in a `try`, e.g.

```clojure
(defn perform-in-try [stop]
  (fn []
    (try
      {:success (stop)}
      (catch Exception e
        {:failure e}))))

(defn safe-stop! [started]
  (->> started
       (map-like/fmap perform-in-try)
       stop!))
```

and then you can inspect the return value to see if anything failed.

### Lazy dependencies

If a process does not perform a task by itself (in contrast to e.g. a file watcher), but is merely started to be used by another process, then it can be lazily loaded, see *[flc-x/lazy]*. If it is not needed, then it is not started. This can be useful if systems are composed dynamically.

### Keyword arguments

If you have a function that takes a map of both configuration and dependencies, like records in *stuartsierra/component*, then you can use a function like

```clojure
(defn kw-component [[program init] deps-map]
  (let [arg-names (keys deps-map)
        deps (vals deps-map)
        init-map (into {} init)
        p (fn [& args]
            (program (into init-map (map vector arg-names args))))]
    [p deps]))
```

to be able to write components like

```clojure
(defn jetty [{:keys [port handler]}]
  (flc/process (jetty/run-jetty handler {:port port})
               #(.stop %)))

(def components
  {:handler ...
   :webserver (kw-component [jetty {:port 8080}] {:handler :handler})})
```

You can freely move arguments between configuration and dependency, e.g.

```clojure
(def components
  {:handler ...
   :webserver/port [#(process 8080)]
   :webserver (kw-component [jetty] {:port :webserver/port
                                     :handler :handler})})
```

I encourage you to come up with your own DSL. For instance, I would replace `{:webserver/port [#(process 8080)]}` with either `{:webserver/port (constant 8080)}` if I predominantely use maps, or `[(constant :webserver/port 8080)]` if I use sequences.

### Relationship to *component* and *integrant*

You can easily write an adapter for components defined using the `Lifecycle` protocol in *[component]*. We already have a function to handle keyword arguments above, so we can write

```clojure
; Assume the component library is required as sierra.
; kw-component as above.

(defn sierra-component [component]
  (kw-component [(fn [m]
                   (process (sierra/start (merge component m))
                            sierra/stop))]
                (sierra/dependencies component)))

(def components
  {:handler ...
   :webserver (sierra-component (sierra/using (map->Jetty {:port 8080}) [:handler]))})
```

assuming you have a `Jetty` record that implements `Lifecycle`.

Similarly an adapter for [integrant] can be written. *integrant* also supports `suspend!` and `resume!`. You can in fact encode this too ad hoc, but it does not necessarily compose with other extensions. One way to do it is to have processes return three values there the third value next to the state and the stop function is the suspend function. (The third value will just be ignored by all other functions.) You can write a wrapper that provides a default suspend function and apply that to the whole system to not have to rewrite existing components. Just like the start function returns a stop function, the suspend function should return a resume function that looks just like the start function. It would look something like this:

```clojure
(defn with-default-suspend [start]
  (fn p [& args]
    (let [{:as process :keys [stop suspend]} (apply start args)]
      (assoc process :suspend (or suspend (fn [] (stop) p))))))

(defn suspend! [started]
  (reduce (fn [suspended [name {:keys [suspend]}]]
            (cons [name (suspend)] suspended))
          nil
          started))

(defn resume! [components suspended]
  (let [resumer (into {} suspended)]
    (start! (for [[name [_ deps]] components]
              [name [(resumer name) deps]]))))
```

Note that the resume function must produce something that can be suspended; this is why the resume function returns `p` in `with-default-suspend`. It is probably a good idea to create a lifecycle function that takes four arguments, `start`, `stop`, `resume`, and `suspend` to encode this recursion. It's not pretty:

```clojure
(defn lifecycle' [start stop suspend resume]
  (fn [& args]
    (let [p (fn p [state]
              {:state state
               :stop #(stop state)
               :suspend (fn []
                          (let [suspended-state (suspend state)]
                            (fn [& new-args]
                              (p (if (= new-args args)
                                   (resume suspended-state)
                                   (apply start new-args))))))})]
      (p (apply start args)))))
```

I'm not so sure this is a good approach though, which is why it is only written here in the documentation as an example.

A better approach is probably to partially start/stop the system, and invert the dependencies.

### Partial start/stop

Partial start/stop requires you to know which components that have been started, and this is handled in *[flc-x/partial]*.

Here is how I would handle the *integrant* example in *flc*.

```clojure
; https://github.com/weavejester/integrant#suspending-and-resuming

(defn constant [x]
  (-> {:dummy x} constants :dummy)) ; Silly.

(defn webserver [opts]
  (lifecycle (fn []
               (let [handler (atom (promise))
                     options (-> opts (assoc :join? false))]
                 {:handler handler
                  :server (jetty/run-jetty #(@@handler %) options)}))
             #(.stop (:server %))))

(defn web-app [webserver handler]
  (deliver @(:handler webserver) handler)
  (process webserver
           #(update % :handler reset! (promise))))

(def system
  {:webserver/options (constant {:port 8080})
   :webserver (component webserver [:webserver/options])
   :handler (constant (fn [_] (resp/response "Hello, world!")))
   :web-app (component web-app [:webserver :handler])})
```

Here I have changed around the dependencies. The webserver does not depend on a handler; instead, the web app installs the handler and if the handler is updated (meaning it is first stopped, then replaced with a new value, and then started) then when the web-app is restarted the new handler is installed.

Notice how this change means that we do not need to encode special logic for realizing if we need to restart the webserver or not. The options are passed in as a dependency, so if we want to change the port we will necessarily stop the webserver and then restart it.

A downside is that we need to encode this logic explicitly. In *integrant* we can add this without changing the structure of the system. That is a double edged sword though, if we happen to encode a bug in the suspend/resume logic. By inverting the dependencies no extra logic is added, and thus less room for bugs.

### Custom DSLs

There are obvious convenience functions that one would like to have when using *flc*. You quickly reach for a way to specify constants and side-effect free processes (a.k.a. functions). However, how those should be designed in detail is less obvious. Therefore I have been conservative in including them, and it is easy to write your own. For instance, it would perhaps be tempting to write a macro like `(component webserver webserver/options)` which produces `[:webserver [webserver [:webserver/options]]]`, with an optional first keyword argument: `(component :webserver jetty webserver/options)`.

There is another good reason for not providing too much out of the box. It encourages the creation of a project specific namespaces that provides the functionality that fits that project. If you ever want to remove *flc* as a dependency you now have an easy way out.

### Migrating away from *flc*

If you don't use any extensions then *flc* is mostly useful during development. For a production build you might want to get rid of the dependency on *flc'.

In that case, if you are only effectively using `process`, `lifecycle`, and `start!`, then you can rewrite the following,

```clojure
(ns no-flc
  (:require [flc.simple :as flc]))

(def foo [x]
  (process ...))

(def bar
  (lifecycle (fn [foo] ...)
             ...))

(start! (merge (constants {:x ...})
               {:foo (component foo [:x])
                :bar (component bar [:foo])}))
```

into

```clojure
(ns no-flc)

(defn process [state & _]
  state)

(def lifecycle [start & _]
  start)

(def foo [x] ; Unchanged.
  (process ...))

(def bar ; Unchanged.
  (lifecycle (fn [foo] ...)
             ...))

(let [x ...
      foo' (foo x)
      bar' (bar foo')])
```

and then remove *flc* from the list of dependencies.

## See also

*[yoyo]* is similar at the lowest level as it includes the abstraction that I call *process*. From the documentation:

```clojure
(:require [yoyo.core :as yc])

(defn open-db-pool! [db-config]
  (let [db-pool (start-db-pool! db-config)]
    (yc/->component db-pool
                    (fn []
                      (stop-db-pool! db-pool)))))
```

`yoyo.core/->component` looks precisely like `flc.process/process*`. It then takes a different turn however, using monadic composition and the *[cats]* monad library. This leads to a very different experience.

## License

Copyright © 2018-2019 Johan Lodin

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[integrant]: https://github.com/weavejester/integrant
[component]: https://github.com/stuartsierra/component
[mount]: https://github.com/tolitius/mount
[yoyo]: https://github.com/jarohen/yoyo
[cats]: https://github.com/funcool/cats
[plumatic/graph]: https://github.com/plumatic/plumbing

[flc-x/simple]: src/flc_x/simple.clj
[flc-x/log]: src/flc_x/log.clj
[flc-x/try]: src/flc_x/try.clj
[flc-x/future]: src/flc_x/future.clj
[flc-x/lazy]: src/flc_x/lazy.clj
[flc-x/partial]: src/flc_x/partial.clj
[flc-x/recurrent]: src/flc_x/recurrent.clj
[flc-x/component]: src/flc_x/component.clj
[flc-x/let-start]: src/flc_x/let_start.clj
[flc-x/kw-args]: src/flc_x/kw_args.clj
