(ns flc-x.try-test
  (:require [expectations.clojure.test :refer :all]
            [flc-x.try :refer :all]
            [flc.core :as core]
            [flc.process :as process :refer [process*]]
            [flc.program :as program]
            [flc.component :as component :refer [component]]
            [flc.let-like :as let-like]
            [flc.map-like :as m]
            [clojure.set :as set]))

(defn constant [x]
  (component (program/constant x)))

(defn constant' [name x]
  [name (constant x)])

(defn clean [f & deps]
  (component (program/clean f) deps))

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
               try-all
               core/start!
               core/states
               failing-dependencies))

; This illustrates how to use start! and try-all to stop safely.
  (expect [[:foobar nil]
           [:bar [:foobar]]
           [:foo [:foobar]]]
          (let [components [(constant' :x :X)
                            [:foo (component (fn []
                                               (process* :FOO #(throw (Exception. "Failed to stop foo")))))]
                            (constant' :bar :BAR)
                            [:foobar (component (program/lifecycle vector
                                                                   (fn [_]
                                                                     (throw (Exception. "Failed to stop foobar"))))
                                                [:foo :bar])]]
                dependents (apply merge-with
                                  set/union
                                  (for [[name component] components
                                        dependency (component/dependencies component)]
                                    {dependency (set [name])}))
                started (core/start! components)
                foo (m/fmap-keyed (fn [name process]
                                    (let [stop (process/stop process)]
                                      (component (fn [& args]
                                                   (process* (stop)))
                                                 (dependents name))))
                                  started)]
            (assert (= (core/arrange foo) foo))
            (->> foo
                 try-all
                 core/start!
                 core/states
                 failing-dependencies)))

  (expect Exception ; This just shows that "type" errors are not caught, as it should be.
          (-> {:foo [(constantly 3)]}
              try-all
              core/start!))

  (expect nil
          (-> {:foo (constant 3)}
              try-all
              core/start!
              core/states
              failing-dependencies))

  (let [processes (-> {:foo (clean (fn [] (/ 1 0)))}
                      try-all
                      core/start!)]
    (expect [[:foo nil]]
            (failing-dependencies (core/states processes)))

    ; Values that are neither successes nor failures are ignored.
    (expect nil
            (failing-dependencies processes))))
