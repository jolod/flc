(ns flc.process
  "A process is a map with keys `:state` and `:stop` where `:state` `holds any value and `:stop` holds an nullary function performed for side effects. The return value is unspecified, and can be exploited if desired.")

(defn process*
  "Returns a process with the specified state and stop function. `stop` default to `(fn [])`."
  ([state]
   (process* state (fn [])))
  ([state stop]
   {::state state
    ::stop stop}))

(defn process
  "Like `process*`, but `stop` takes the state as an argument.

  The state is closed over, so updating the value in `::state` does not affect the stop method."
  ([state]
   (process* state))
  ([state stop]
   (process* state #(stop state))))

(defn process?
  "Returns true if the argument is associative and contains a `::state` key."
  [x]
  (and (associative? x)
       (contains? x ::state)))

(defn state
  "Returns the process' state."
  [process]
  (::state process))

(defn update-state
  "Applies `f` to the state. (The stop function remains unchanged.)"
  [process f]
  (update process ::state f))

(defn stop
  "Returns the process' stop function."
  [process]
  (::stop process))

(defn update-stop
  "Applies `f` to the stop function. Remember that the return value from `f` should be a nullary function."
  [process f]
  (update process ::stop f))

(defn stop!
  "Calls the process' stop function. Returns whatever the stop function returns."
  [process]
  ((stop process)))

(defn wrap-stop
  "Updates the stop function with an nullary function which calls `f` with the old stop function as the argument. `f` can perform any actions, including calling its argument, which is where the name comes from. `f` can also completely ignore the old stop function, but this is likely not a good idea."
  [process f]
  (update-stop process (fn [stop]
                         (fn []
                           (f stop)))))
