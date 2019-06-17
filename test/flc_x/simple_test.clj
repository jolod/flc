(ns flc-x.simple-test
  (:require [expectations.clojure.test :refer :all]
            [flc-x.simple :refer :all]))

(defexpect flc.simple-test
  (let [m {:x 1
           :y 2}]
    (expect m
            (-> m
                constants
                start!
                state-map))))