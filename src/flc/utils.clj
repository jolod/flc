(ns flc.utils)

(defn =>
  "Like partial, but puts the arguments at the end. `#(f % y z)` can be replaced with `(=> f y z)`, and the analogy with `->` is that the `%` is inserted as the first argument. Multiple arguments are also supported, so `#(f %1 %2 y z)` can also be replaced with `(=> f y z)`.

  A couple of core functions have special support for this pattern, e.g. `update`, `update-in`, `swap!`. You can write `(update {:foo [3]} conj 4)` which is equivalent to `(update {:foo [3]} :foo (=> conj 4)))`. Thus, if you have an update function that does not have this support hardcoded then you can use `=>` instead.

  If you instead want to `cons` instead of `conj` (which have the arguments in opposite order) in the example above, you cannot use the special syntax, but you can instead use `partial`, or `=>>` which is an alias, which puts the arguments at the end: `(update {:foo [3]} :foo (=>> cons 4)))`.

  `=>` and `=>>` are also useful when you have nested anonymous functions."
  [f & args]
  (fn
    ([x]
     (apply f x args))
    ([x & more]
     (apply f x (concat more args)))))

(defn =>>
  "Equivalent to `partial`, but provided in analogy with `->>` (see `=>`)."
  [f & args]
  (apply partial f args))
