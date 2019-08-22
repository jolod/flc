# Derivation

Below is a derivation of the essence of the `flc` system, starting with the canonical webserver example.

BEWARE: All of this code is untested and written directly in this document.

## The core

```clojure
(defn start! []
  (let [database (db/connect "jdbc:sqlite:")
        handler (fn [request] (resp/response (query-status database)))
        jetty (jetty/run-jetty handler {:port 8080})]
    {:jetty jetty
      :database database}))

(def stop! [started]
  (fn [{:keys [jetty database]}]
    (.stop (:jetty started))
    (db/stop (:database started))))

; (stop! (start!))
; Presumably you store the return value of start! in an atom.
```

Move stop functions into `start!`.

```clojure
(defn start! []
  (let [database (db/connect "jdbc:sqlite:")
        handler (fn [request] (resp/response (query-status database)))
        jetty (jetty/run-jetty handler {:port 8080})]
    {:jetty [jetty #(.stop %)]
     :database [database db/stop]}))

(def stop! [started]
  (doseq [component [:jetty :database]]
    (let [[state stop] (get started component)]
      (stop state))))
```

Move component order into `start!` by using a vector of tuples instead of a map.

```clojure
(defn start! []
  (let [database (db/connect "jdbc:sqlite:")
        handler (fn [request] (resp/response (query-status database)))
        jetty (jetty/run-jetty handler {:port 8080})]
    [[:jetty [jetty #(.stop %)]]
     [:database [database db/stop]]]))

(def stop! [started]
  (doseq [[_ [state stop]] started]
    (stop state)))
```

Build up the return value piece by piece to make sure that the components are stopped in the reverse order.

```clojure
(defn start! []
  (let [started []

        database (db/connect "jdbc:sqlite:")
        started (cons [:database [database db/stop]] started)

        handler (fn [request] (resp/response (query-status database)))

        jetty (jetty/run-jetty handler {:port 8080})
        started (cons [:jetty [jetty #(.stop %)]] started)]
    started))
```

Refactor consing into `started`.

```clojure
(defn start! []
  (let [add (fn [started name state stop]
              [(cons [name [state stop]] started) state])

        started []
        [started database] (add started :database (db/connect "jdbc:sqlite:") db/stop)
        handler (fn [request] (resp/response (query-status database)))
        [started jetty] (add started :jetty [(jetty/run-jetty handler {:port 8080}) #(.stop %)])]
    started))
```

Homogenize the handler.

```clojure
(defn start! []
  (let [add (fn [started name state stop]
              [(cons [name [state stop]] started) state])

        started []
        [started database] (add started :database (db/connect "jdbc:sqlite:") db/stop)
        [started handler] (add started :handler (fn [request] (resp/response (query-status database))) identity)
        [started jetty] (add started :jetty [(jetty/run-jetty handler {:port 8080}) #(.stop %)])]
    started))
```

Make the `add` function take a function instead of `state` and `stop`, and pass the arguments to `add`:

```clojure
(defn start! []
  (let [add (fn [started name func & args]
              (let [[state stop] (apply func args)]
                [(cons [name [state stop]] started) state]))

        started []
        [started database] (add started :database (fn [] [(db/connect "jdbc:sqlite:") db/stop]))
        [started handler] (add started :handler (fn [database] [(fn [request] (resp/response (query-status database))) identity]) database)
        [started jetty] (add started :jetty (fn [handler] [(jetty/run-jetty handler {:port 8080}) #(.stop %)]) handler)]
    started))
```

Make `add` look up the state using the keyword instead of passing it directly, and there is now no need to return the state from `add`, only `started`.

```clojure
(defn start! []
  (let [add (fn [started name func & arg-names]
              (let [[state stop] (apply func (map (comp first (into {} started)) arg-names))]
                (cons [name [state stop]] started)))

        started []
        started (add started :database (fn [] [(db/connect "jdbc:sqlite:") db/stop]))
        started (add started :handler (fn [database] [(fn [request] (resp/response (query-status database))) identity]) :database)
        started (add started :jetty (fn [handler] [(jetty/run-jetty handler {:port 8080}) #(.stop %)]) :handler)]
    started))
```

Rewrite using `->`.

```clojure
(defn start! []
  (let [add (fn [started name func & arg-names]
              (let [[state stop] (apply func (map (comp first (into {} started)) arg-names))]
                (cons [name [state stop]] started)))

        started (-> []
                    (add :database (fn [] [(db/connect "jdbc:sqlite:") db/stop]))
                    (add :handler (fn [database] [(fn [request] (resp/response (query-status database))) identity]) :database)
                    (add :jetty (fn [handler] [(jetty/run-jetty handler {:port 8080}) #(.stop %)]) :handler))]
    started))
```

Move out the lambdas to make the structure clearer.

```clojure
(defn database []
  [(db/connect "jdbc:sqlite:")
   db/stop])

(defn handler [database]
  [(fn [request] (resp/response (query-status database)))
   identity])

(defn jetty [handler]
  [(jetty/run-jetty handler {:port 8080})
   #(.stop %)])

(defn start! []
  (let [add (fn [started name func & arg-names]
              (let [[state stop] (apply func (map (comp first (into {} started)) arg-names))]
                (cons [name [state stop]] started)))

        started (-> []
                    (add :database database)
                    (add :handler handler :database)
                    (add :jetty jetty :handler))]
    started))
```

Next, move the arguments to the tree `add`s into a map, and then use a function that uses the dependencies to construct a correct sequence of `add`s for you. For later convenience, also put the dependencies in a vector.

```clojure
(defn start! []
  (let [add (fn [started name func arg-names]
              (let [[state stop] (apply func (map (comp first (into {} started)) arg-names))]
                (cons [name [state stop]] started)))
        topo-sort (fn [components] ...) ; TODO :-)

        components {:database [database]
                    :handler [handler [:database]]
                    :jetty [jetty [:handler]]}
        started (reduce (fn [started [name [func arg-names]]]
                          (add started name func arg-names))
                        []
                        (topo-sort components))]
    started))
```

In practice you would use a function like

```clojure
(defn component [f & deps]
  [f deps])
```

so that you can write

```clojure
{:database (component database)
 :handler (component handler :database)
 :jetty (component jetty :handler)}
```

which I think reads better than nested vectors. It will also be useful if you want to support functions that keyword arguments, in which case you would replace `component` with a function called something like `kw-component`, but I won't cover that here.

Next, move the components map out as an argument.

```clojure
(defn start! [components]
  (let [add (fn [started name func arg-names]
              (let [[state stop] (apply func (map (comp first (into {} started)) arg-names))]
                (cons [name [state stop]] started)))
        topo-sort (fn [components] ...)
        started (reduce (fn [started [name [func arg-names]]]
                          (add started name func arg-names))
                        []
                        (topo-sort components))]
    started))
```

Done!

Here is the complete code we ended up with:

```clojure
;; Library:

(defn start! [components]
  (let [add (fn [started name func arg-names]
              (let [[state stop] (apply func (map (comp first (into {} started)) arg-names))]
                (cons [name [state stop]] started)))
        topo-sort (fn [components] ...)
        started (reduce (fn [started [name [func arg-names]]]
                          (add started name func arg-names))
                        []
                        (topo-sort components))]
    started))

(def stop! [started]
  (doseq [[_ [state stop]] started]
    (stop state)))

;; User defined:

(defn database []
  [(db/connect "jdbc:sqlite:")
   db/stop])

(defn handler [database]
  [(fn [request] (resp/response (query-status database)))
   identity])

(defn jetty [handler]
  [(jetty/run-jetty handler {:port 8080})
   #(.stop %)])

; (stop! (start! {:database [database]
;                 :handler [handler [:database]]
;                 :jetty [jetty [:handler]]}))
```

## A note on `topo-sort`

Note that once `start!` was rewritten to take an argument, we just instantly call `topo-sort` on the input before using it. `start!` is effectively a composition of `topo-sort` and the rest of `start!`. So, it would make sense to factor out "the rest" of `start!`, so that `topo-sort` is optional, and the input can be a sequence instead of a map.

This, taken even one step further, leads you to two aspects of *flc* that is not immediately apparent: `let-like` and `map-like`.

`let-like` only solves the "dynamic let" problem, and `map-like` deals with seqables (including seqs, vectors, and maps) while treating them as a map.

## Liberating yourself from dependencies

It is easy to ignore the library and revert back to the old way of doing it, using `let` and destructuring. In particular, if you do not care about stopping the system, you can just do:

```clojure
(defn start! []
  (let [[database'] (database)
        [handler'] (handler database')
        [jetty'] (jetty handler')]
    jetty'))
```

which reuses all your logic for creating the components but does not need any library.

In practice it is slightly more involved since you use the functions in `flc.program` to write the `database`, `handler`, and `jetty` functions (see below); this gives you a map instead of a vector. However, it is just *slightly* more involved, since you can just supply your own definitions (it would typically just be two or perhaps three functions, and might even be just one) and make them actually return vectors instead.

## A note on configuration

You presumably do not want to have the database URI or the webserver port to be hardcoded values. There are many ways of structuring this, but the basic idea is to introduce another function level.

```clojure
(defn jetty [port]
  (fn [handler]
    [(jetty/run-jetty handler {:port port})
     #(.stop %)]))

(start! {...
         :jetty [(jetty 8080) [:handler]]})
```

## Composability

For reasons that will become clear below, I will require the stop function to be nullary and thus already know the state (i.e. be a closure). But this will be useful later when we want to write higher-order life cycles.

```clojure
(defn jetty [port]
  (fn [handler]
    (let [server (jetty/run-jetty handler {:port port})]
      [server #(.stop server)])))

(def stop! [started]
  (doseq [[_ [_ stop]] started]
    (stop)))
```

Refactor the `let`. I call this function "process" because it contains the state and a way to stop it. So it's like a process.

```clojure
(defn process [state stop]
  [state #(stop state)])

(defn jetty [port]
  (fn [handler]
    (process (jetty/run-jetty handler {:port port})
             #(.stop %))))
```

This is now almost exactly what we had above for the core, except we use `process` instead of `vector`. So `jetty` is a *program* which creates a *process* when run.

We can also make the system start be async through futures.

```clojure
(defn ->async [program]
  (fn [& args]
    (let [p (delay (apply program (pmap deref args)))]
      [(future
         (let [[state _] @p]
           state))
       (fn []
         (let [[_ stop] @p]
           (stop)))])))

(defn async-system [f components]
  (into {}
        (for [[name [program dependencies]] components]
          [name [(->async program) dependencies]])))
```

For `->async` to work, all programs need to be rewritten to be async, because the dependent programs will now get references as arguments. By rewriting all programs all functions now produce and expect references and dereferences the dependencies appropriately.

Here we also see why it is useful to bind the state to the stop function early. Otherwise we would have to dereference the state since the state is now wrapped in a future:

```clojure
(fn []
  (let [[state stop] @p]
    (stop (deref state)))) ; # `deref` is the inverse of `future`.
```

However, it is not always possible to have an inverse function to transform the state back into what the original stop function expects.

## The other important utility function

Next to `process`, the other important utility function is

```clojure
(defn lifecycle [start stop]
  (fn [& args]
    (process (apply start args) stop)))

(defn jetty [port]
  (lifecycle (fn [handler]
               (jetty/run-jetty handler {:port port}))
             #(.stop %)))
```

`lifecycle` might actually cover all your needs for simple systems.
