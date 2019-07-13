(ns flc.program-test
  (:require [expectations.clojure.test :refer :all])
  (:require [flc.program :refer :all]
            [flc.process :as process]))

#_(defexpect program
  (expect 4
          (let [a (atom 0)
                program (lifecycle #(inc %) #(reset! a %))]
            (process/stop! (program 3))
            @a))

  (expect {:a 4
           :state -4}
          (let [a (atom 0)
                program (update-state (lifecycle #(inc %) #(reset! a %)) -)
                process (program 3)]
            (process/stop! process)
            {:a @a
             :state (process/state process)})))

(defexpect -compose
  (expect -10
          (-> 4
              ((compose (clean -)
                        (clean #(* 2 %))
                        (clean inc)))
              process/state)))
