(ns flc-x.let-start-test
  (:require [expectations.clojure.test :refer :all]
            [flc-x.let-start :refer :all]
            [flc.core :refer [states stop!]]
            [flc.process :refer [process* process]]))

(defexpect flc.let-start-test
  (expect [['foo 3]]
          (states (let-start! [foo (process* 3)])))
  (expect {:thing 104
           :states [['foo 3]
                    ['bar 4]
                    [nil 7]]}
          (let [thing (atom 100)
                processes (let-start! [foo (process* 3)
                                       bar (process (inc foo) #(swap! thing + %))]
                                      (+ foo bar))]
            (stop! processes)
            {:thing @thing
             :states (states processes)}))
  (expect [['[foo] [3]]
           ['bar 4]
           [nil 7]]
          (let [processes (let-start! [[foo] (process* [3])
                                       bar (process* (inc foo))]
                                      (+ foo bar))]
            (stop! processes)
            (states processes)))
  (expect {:thing 104
           :states [['bar 4]
                    [nil 7]]}
          (let [thing (atom 100)
                processes (let-start! [foo 3
                                       bar (process (inc foo) #(swap! thing + %))
                                       foobar (+ foo bar)]
                                      foobar)]
            (stop! processes)
            {:thing @thing
             :states (states processes)}))
  (expect [['[bar] [4]]
           [nil 7]]
          (states (let-start! [[foo] [3]
                               [bar] (process (vector (inc foo)))]
                              (+ foo bar))))
  (expect [['[bar] [4]]
           [nil nil]]
          (states (let-start! [[foo] [3]
                               [bar] (process (vector (inc foo)))]
                              nil))))
