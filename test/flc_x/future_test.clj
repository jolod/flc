(ns flc-x.future-test
  (:refer-clojure :exclude [await])
  (:require [expectations.clojure.test :refer :all]
            [flc-x.future :refer :all]
            [flc.map-like :as m]
            [flc-x.simple :as simple]
            [flc.process :as process :refer [process*]]
            [flc.core :refer [start! states]]))

(defn slow-constant' [name x dt]
  [name [(fn []
           (process*
            (do (Thread/sleep dt)
                x)))]])

(defexpect flc.future-test
  (let [dt 0]
    (expect [[:foo :FOO]
             [:bar :BAR]
             [:foobar [:FOO :BAR]]]
            (->> [(slow-constant' :foo :FOO dt)
                  (slow-constant' :bar :BAR dt)
                  [:foobar [(comp process/process vector) [:foo :bar]]]]
                 simple/components
                 future-all
                 start!
                 states
                 (map #(update % 1 deref))))))

(defexpect flc.future.await-test
  (let [dt 10000]
    (expect [[:foo true]
             [:bar false]
             [:foobar false]]
            (let [started (->> [(slow-constant' :foo :FOO 0)
                                (slow-constant' :bar :BAR dt)
                                [:foobar [(comp process/process vector) [:foo :bar]]]]
                               simple/components
                               future-all
                               start!)]
              (Thread/sleep 100)
              (->> started
                   states
                   (m/fmap realized?)))))
  (let [dt 0]
    (expect [[:foo true]
             [:bar true]
             [:foobar true]]
            (->> [(slow-constant' :foo :FOO dt)
                  (slow-constant' :bar :BAR dt)
                  [:foobar [(comp process/process vector) [:foo :bar]]]]
                 simple/components
                 future-all
                 start!
                 await
                 states
                 (m/fmap realized?)))))
