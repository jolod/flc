(ns flc-x.log-test
  (:require [expectations.clojure.test :refer :all]
            [flc-x.log :refer :all]
            [flc.core :refer [start! stop!]]
            [flc-x.simple :refer [components]]
            [flc.process :refer [process]]
            [flc.program :refer [lifecycle]]))

(defexpect flc.log-test
  (expect [[:starting! [:x nil]], [:started! [:x :X]]
           [:starting! [:y [:X]]], [:started! [:y :X]]
           [:stopping! [:y :X]], [:stopped! [:y :X]]
           [:stopping! [:x :X]], [:stopped! [:x nil]]]
          (let [the-log (atom [])
                _ (->> {:x [#(process :X)]
                        :y [(lifecycle identity identity) [:x]]}
                       components
                       (log-all (fns #(swap! the-log conj %)))
                       start!
                       stop!)]
            @the-log)))
