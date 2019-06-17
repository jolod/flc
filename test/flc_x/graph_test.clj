(ns flc-x.graph-test
  (:refer-clojure :exclude [compile])
  (:require [expectations.clojure.test :refer :all]
            [flc-x.graph :refer :all]
            [flc.core :as core]
            [flc.process :as process :refer [process*]]
            [flc.program :as program]
            [flc.let-like :as let-like]
            [flc.component :as component :refer [component]]
            [flc.map-like :as m]
            [clojure.set :as set]))

(defexpect -fnk
  (expect {:foo 3
           :bar 5}
          (let [graph (constants {:foo 3
                                  :bar 5})
                f (compile graph)]
            (f {})))
  (expect {:foo 3
           :bar 5
           :foobar 8}
          (let [graph (merge (constants {:foo 3
                                         :bar 5})
                             {:foobar (fnk' [foo bar]
                                            (+ foo bar))})
                f (compile graph)]
            (f {})))
  (expect {:foo 3
           :foobar 8}
          (let [graph (merge (constants {:foo 3})
                             {:foobar (fnk' [foo bar]
                                            (+ foo bar))})
                f (compile graph)]
            (f {:bar 5}))))

(defmacro computation [name args expr]
  `['~name (component (program/clean (fn ~args ~expr)) '~args)])

(defmacro constant [name expr]
  `['~name (component (program/constant ~expr))])

(defexpect lab
  (expect [['foo 3]]
          (->> [(computation foo [] 3)]
               core/start!
               core/states))
  (expect [['foo 3]]
          (->> [(constant foo 3)]
               core/start!
               core/states))
  (expect [['foo 3]
           ['bar 5]
           ['foobar 8]]
          (->> [(constant foo 3)
                (constant bar 5)
                (computation foobar [foo bar]
                             (+ foo bar))]
               core/start!
               core/states)))
