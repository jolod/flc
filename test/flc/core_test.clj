(ns flc.core-test
  (:require [expectations.clojure.test :refer :all]
            [flc.core :refer :all]
            [flc-x.simple :refer [components]]
            [flc.process :as process :refer [process process*]]
            [flc.program :as program :refer [lifecycle]]
            [flc.component :as component]
            [flc.map-like :as m]))

(defexpect error-if-not-dag-or-complete
  (expect {:sorted [:x]
           :ambiguous {:y #{:missing}}
           :dependencies {:x nil
                          :y [:x :missing]}}
          (ex-data (try (-> {:x [(program/constant 3)]
                             :y [(program/clean inc) [:x :missing]]}
                            components
                            start!)
                        (catch Exception e e)))))

#_(defexpect core
    (expect 1
            (let [result (atom nil)]
              (-> {:x [(fn [] (process (atom 0) #(reset! result (deref %))))]
                   :foo [(lifecycle #(swap! % inc) #(swap! % -)) [:x]]}
                  components
                  start!
                  states
                  m/->map
                  :x
                  deref)))

    (expect -1
            (let [result (atom nil)]
              (-> {:x [(fn [] (process (atom 0) #(reset! result (deref %))))]
                   :foo [(lifecycle #(do (swap! % inc) %) #(swap! % -)) [:x]]}
                  components
                  start!
                  stop!)
              @result))

    (expect [[:x 3]
             [:y 4]]
            (-> {:y [(program/clean inc) [:x]]}
                (concat (constants {:x 3}))
                components
                start!
                states))

    (expect {:sorted [:x]
             :ambiguous {:y #{:missing}}
             :dependencies {:x nil
                            :y [:x :missing]}}
            (ex-data (try (-> {:x [(program/constant 3)]
                               :y [(program/clean inc) [:x :missing]]}
                              components
                              start!)
                          (catch Exception e e)))))

(defexpect -arrange
  (let [components (concat (into {}
                                 (for [n (range 10)]
                                   [(inc n) (component/component inc [n])]))
                           [[0 (component/component (constantly 0))]])]
    (expect (not (apply < (m/keys components))))
    (expect (apply < (m/keys (arrange components))))))
