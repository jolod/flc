(ns flc-x.recurrent-test
  (:require [expectations.clojure.test :refer :all]
            [flc-x.recurrent :refer :all]
            [flc.core :as core]
            [flc-x.simple :as simple]
            [flc.map-like :as m]
            [flc.let-like :as let-like]
            [flc.component :as component]
            [flc.process :as process :refer [process* process]]))

(defn counter [n]
  (fn []
    (process (inc n) (comp recurrent counter))))

(defn fibonacci [x y]
  (fn []
    (process (+ x y) (comp recurrent (partial fibonacci y)))))

(defn obstinate [x]
  (process* x
            (fn []
              (recurrent
               (fn [_]
                 (process* x))))))

(defn start-stop [components]
  (map (comp m/->map second)
       (rest (iterate (fn [[components]]
                        (let [processes (core/start! components)]
                          [(step components (core/stop! processes))
                           (core/states processes)]))
                      [components]))))

(defn run [components]
  (->> components
       (m/fmap component/->let-like)
       let-like/run
       :results
       reverse))

(defexpect flc.recurrent-test
  (let [components (simple/components {:foo [(counter 0)]})
        processes (core/start! components)
        components' (step components (core/stop! processes))]
    (expect [[:foo 1]]
            (core/states processes))
    (expect (not= (seq components) components'))
    (expect [[:foo 2]]
            (-> components'
                core/start!
                core/states)))

  (expect [1 2 3 5 8 13 21 34 55]
          (->> {:fib [(fibonacci 0 1)]}
               simple/components
               start-stop
               (take 9)
               (map :fib)))

  (expect [1 2 3 4 5]
          (->> {:x [(counter 0)]}
               simple/components
               start-stop
               (take 5)
               (map :x)))

  (expect [{:x 8
            :obstinate 8}
           {:x 9
            :obstinate 8}
           {:x 10
            :obstinate 8}]
          (->> {:x [(counter 7)]
                :obstinate [obstinate [:x]]}
               simple/components
               start-stop
               (take 3)))

  (expect [[:x 1]
           [:x 2]
           [:x 3]]
          (-> (simple/components [[:x [(constantly 1)]]
                                  [:x [(constantly 2)]]
                                  [:x [(constantly 3)]]])
              (step (simple/components [[:x (constantly :three)]
                                        [:x (constantly :two)]]))
              run))

  (expect [[:x 1]
           [:x :two]
           [:x :three]]
          (-> (simple/components [[:x [(constantly 1)]]
                                  [:x [(constantly 2)]]
                                  [:x [(constantly 3)]]])
              (step (simple/components [[:x (recurrent (constantly :three))]
                                        [:x (recurrent (constantly :two))]]))
              run))

  (expect [[:x 1]
           [:x :two]
           [:x 3]]
          (-> (simple/components [[:x [(constantly 1)]]
                                  [:x [(constantly 2)]]
                                  [:x [(constantly 3)]]])
              (step (simple/components [[:x (constantly :three)]
                                        [:x (recurrent (constantly :two))]]))
              run)))
