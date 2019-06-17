(ns flc-x.let-start
  "This namespace will be moved to flc-x/let-start."
  (:require [flc.process :as process :refer [process* process?]]))

(defmacro let-start!
  "`let-start!` allows you to write processes inline and use programs like normal functions. It looks like `let`, but if the expr is evaluated to a process (as decided by `flc.process/process?`) then the state is bound to the binding form instead of the process itself.

  In contrast to `let` the return value is that of `flc.core/start!`, where the names are the binding forms (usually symbols, but destructuring is supported). If a body is provided then a last process named nil is added with state being the value of the body.

  Only processes are returned, so

      (let [processes (let-start! [foo 3
                                   bar (process (inc foo))]
                        (+ foo bar))]
        (states processes))

  will evaluate to `[['bar 4] [nil 7]]`."
  ([bindings]
   `(rest (let-start! ~bindings nil)))
  ([[name process & more :as bindings] body]
   (if (seq bindings)
     `(let [process# ~process]
        (if (process? process#)
          (let [~name (process/state process#)]
            (concat
             (let-start! ~more ~body)
             [['~name process#]]))
          (let [~name process#]
            (let-start! ~more ~body))))
     [[nil `(process* ~body)]])))
