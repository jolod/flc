(ns flc-x.lazy-test
  (:require [expectations.clojure.test :refer :all]
            [flc-x.lazy :refer :all]
            [flc.core :as core :refer [start! stop!]]
            [flc.process :refer [process*]]
            [flc.component :as component :refer [component]]
            [flc.map-like :as m]))

(defexpect flc.lazy-test
  (expect [[:starting :x]
           [:stopping :x]]
          (let [the-log (atom [])
                _ (-> [[:x (strict (component (fn []
                                                (swap! the-log conj [:starting :x])
                                                (process* :X #(swap! the-log conj [:stopping :x])))))]
                       [:y (lazy (component (fn []
                                              (swap! the-log conj [:starting :y])
                                              (process* :Y #(swap! the-log conj [:stopping :y])))))]]
                      start!
                      stop!)]
            @the-log))
  (expect [[:starting :x]
           [:stopping :x]]
          (let [the-log (atom [])
                _ (-> [[:x (strict (component (fn []
                                                (swap! the-log conj [:starting :x])
                                                (process* :X #(swap! the-log conj [:stopping :x])))))]
                       [:y (lazy (component (fn []
                                              (swap! the-log conj [:starting :y])
                                              (process* :Y #(swap! the-log conj [:stopping :y])))))]
                       [:xy (lazy (component (fn [& args]
                                               (swap! the-log conj [:starting :xy])
                                               (process* args #(swap! the-log conj [:stopping :xy])))))]]
                      start!
                      stop!)]
            @the-log))
  (expect [[:starting :x]
           [:starting :y]
           [:starting :xy]
           [:stopping :xy]
           [:stopping :y]
           [:stopping :x]]
          (let [the-log (atom [])
                _ (-> [[:x (strict (component (fn []
                                                (swap! the-log conj [:starting :x])
                                                (process* :X #(swap! the-log conj [:stopping :x])))))]
                       [:y (lazy (component (fn []
                                              (swap! the-log conj [:starting :y])
                                              (process* :Y #(swap! the-log conj [:stopping :y])))))]
                       [:xy (strict (component (fn [& args]
                                                 (swap! the-log conj [:starting :xy])
                                                 (process* args #(swap! the-log conj [:stopping :xy])))
                                               [:x :y]))]]
                      start!
                      stop!)]
            @the-log))
  (expect [[:x :X]]
          (let [the-log (atom [])
                started (-> [[:x (strict (component (fn []
                                                      (swap! the-log conj [:starting :x])
                                                      (process* :X #(swap! the-log conj [:stopping :x])))))]
                             [:y (lazy (component (fn []
                                                    (swap! the-log conj [:starting :y])
                                                    (process* :Y #(swap! the-log conj [:stopping :y])))))]]
                            start!)]
            (->> started
                 remove-unrealized
                 core/states
                 (m/fmap deref)))))
