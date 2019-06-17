(ns flc.utils-test
  (:require [expectations.clojure.test :refer :all])
  (:require [flc.utils :refer :all]))

(defexpect utils
  (expect [1 2 3 4 5]
          ((=> vector 2 3 4 5) 1))

  (expect [1 2 3 4 5]
          ((=> vector 3 4 5) 1 2))

  (expect [1 2 3 4 5 6]
          ((-> vector (=> 5 6) (=>> 1 2)) 3 4))

  (expect {:foo [3 4]}
          (update {:foo [3]} :foo conj 4))

  (expect {:foo [3 4]}
          (update {:foo [3]} :foo #(conj % 4)))

  (expect {:foo [3 4]}
          (update {:foo [3]} :foo (=> conj 4))))
