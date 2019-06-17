(ns flc-x.try-test
  (:require [expectations.clojure.test :refer :all]
            [flc-x.try :refer :all]
            [flc.core :as core]
            [flc.process :as process :refer [process*]]
            [flc.program :as program]
            [flc.let-like :as let-like]
            [flc.map-like :as m]
            [flc-x.simple :as simple]
            [clojure.set :as set]))

(defn constant [x]
  [(program/constant x)])

(defn constant' [name x]
  [name (constant x)])

(defn clean [f & deps]
  [(program/clean f) deps])

(defn format-exception [exception]
  (let [data (ex-data exception)]
    (case (:reason data)
      :start data
      :dependency (dissoc data :args))))

(defn format-failure [x]
  (when (failure? x)
    (format-exception (:failure x))))

(defexpect test-try
  (expect {:foo {:success "FOO"}
           :bar {:success "BAR"}
           :foobar {:success "FOOBAR"}}
          (->> {:foo (constant "FOO")
                :bar (constant "BAR")
                :foobar (clean str :foo :bar)}
               simple/components
               try-all
               core/start!
               core/states
               m/->map))

  (expect {:foo {:failure {:reason :start
                           :component :foo
                           :dependencies nil
                           :args nil}}
           :bar {:success "BAR"}
           :foobar {:failure {:reason :dependency
                              :component :foobar
                              :dependencies [:foo :bar]
                              :failures [:foo]}}}
          (->> {:foo (clean (fn [] (/ 1 0)))
                :bar (constant "BAR")
                :foobar (clean str :foo :bar)}
               simple/components
               try-all
               core/start!
               core/states
               (m/fmap #(or (some-> % format-failure failure) %))
               m/->map))

  (expect [[:foo nil]
           [:foobar [:foo]]]
          (->> {:foo (clean (fn [] (/ 1 0)))
                :bar (constant "BAR")
                :foobar (clean str :foo :bar)}
               simple/components
               try-all
               core/start!
               core/states
               states->graph))

; This illustrates how to use start! and try-all to stop safely.
  (expect [[:foobar nil]
           [:bar [:foobar]]
           [:foo [:foobar]]]
          (let [components [(constant' :x :X)
                            [:foo [(fn []
                                     (process* :FOO #(throw (Exception. "Failed to stop foo"))))]]
                            (constant' :bar :BAR)
                            [:foobar [(program/lifecycle vector
                                                         (fn [_]
                                                           (throw (Exception. "Failed to stop foobar"))))
                                      [:foo :bar]]]]
                dependents (apply merge-with
                                  set/union
                                  (for [[name [_ dependencies]] components
                                        dependency dependencies]
                                    {dependency (set [name])}))
                started (core/start! (simple/components components))
                foo (for [[name process] started
                          :let [stop (process/stop process)]]
                      [name [(fn [& args]
                               (process* (stop)))
                             (dependents name)]])]
            (assert (= (let-like/arrange foo) foo))
            (->> foo
                 simple/components
                 try-all
                 core/start!
                 core/states
                 states->graph)))

  (expect Exception ; This just shows that "type" errors are not caught, as it should be.
          (-> {:foo [(constantly 3)]}
              simple/components
              try-all
              core/start!)))
