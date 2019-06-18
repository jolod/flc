(ns flc.let-like-test
  (:require [expectations.clojure.test :refer :all])
  (:require [flc.let-like :refer :all]))

#_(defexpect let-like
    (expect (reverse [[:foo :FOO]
                      [:bar :BAR]
                      [:baz :BAZ]])
            (:results (run [[:foo [(constantly :FOO)]]
                            [:bar [(constantly :BAR)]]
                            [:baz [(constantly :BAZ)]]])))

    (expect (reverse [[:foo "FOO"]
                      [:bar "BAR"]
                      [:foobar "FOOBAR"]])
            (:results (run [[:foo [(constantly "FOO")]]
                            [:bar [(constantly "BAR")]]
                            [:foobar [#(str %1 %2) [:foo :bar]]]])))

    (expect (reverse [[:x 1]
                      [:x 2]
                      [:x 3]])
            (:results (run [[:x [(constantly 1)]]
                            [:x [inc [:x]]]
                            [:x [inc [:x]]]])))

    (expect (reverse [[:x 2]
                      [:x 3]])
            (:results (run {:x 1}
                           [[:x [inc [:x]]]
                            [:x [inc [:x]]]])))

    (expect (reverse [[:foo "FOO"]
                      [:bar "BAR"]
                      [:foobar "FOOBAR"]])
            (:results (run [[:foo [#(do "FOO")]]
                            [:bar [#(do "BAR")]]
                            [:foobar [#(str %1 %2) [:foo :bar]]]])))

    (expect (reverse [[:foo ["FOO"]]
                      [:bar ["BAR"]]
                      [:foobar ["FOOBAR"]]])
            (let [upgrade (fn [f]
                            (fn [& args]
                              (apply f (map first args))))
                  system [[:foo [#(vector "FOO")]]
                          [:bar [#(vector "BAR")]]
                          [:foobar [#(vector (str %1 %2)) [:foo :bar]]]]]
              (:results (run (for [[k [f deps]] system]
                               [k [(upgrade f) deps]])))))

    (expect [[:x nil], [:y [:x]]]
            (dependencies [[:x [1]], [:y [2 [:x]]]])))

(defmacro def' [name body]
  `['~name [(fn [] ~body)]])

(defmacro defn' [name args body]
  `['~name [(fn ~args ~body) '~args]])

(defexpect foo2
  (expect (reverse [['foo "FOO"]
                    ['bar "BAR"]
                    ['foobar "FOOBAR"]])
          (:results (run [(def' foo "FOO")
                          (def' bar "BAR")
                          (defn' foobar [foo bar] (str foo bar))])))

  (expect [:foo :bar :foobar :baz]
          (map first
               (arrange [[:foo [vector]]
                         [:foobar [vector [:foo :bar]]]
                         [:bar [vector]]
                         [:baz [vector]]])))

  (expect [[:foo :bar :foobar :baz] nil]
          (stable-dependency-sort [[:foo]
                                   [:foobar [:foo :bar]]
                                   [:bar]
                                   [:baz]]))

  ; Circular
  (expect [[:x] {:foo #{:bar}
                 :bar #{:foo}}]
          (stable-dependency-sort [[:x]
                                   [:foo [:bar]]
                                   [:bar [:foo]]]))

  ; Missing dependency
  (expect [[:x] {:foo #{:bar}}]
          (stable-dependency-sort [[:x]
                                   [:foo [:bar]]]))

  ; Give presedence to last occurance.
  (expect [[:foo :bar] nil]
          (stable-dependency-sort [[:foo [:bar]]
                                   [:bar [:foo]]
                                   [:foo]]))

  ; Give presedence to last occurance.
  (expect [[:foo :bar] nil]
          (stable-dependency-sort [[:bar]
                                   [:foo]
                                   [:bar]])))

(defexpect -missing-and-complete
  (let [bindings [[:x [nil []]]
                  [:y [nil [:x]]]]]
    (expect [[:x #{}]
             [:y #{}]]
            (missing (dependencies bindings)))
    (expect (complete? bindings)))

  (let [bindings [[:y [nil [:x]]]]]
    (expect [[:y #{:x}]]
            (missing (dependencies bindings)))
    (expect (not (complete? bindings))))

  ; Shadowing
  (let [bindings [[:x [nil []]]
                  [:x [nil [:x]]]]]
    (expect [[:x #{}]
             [:x #{}]]
            (missing (dependencies bindings)))
    (expect (complete? bindings)))
  )



  ; (->> [[:x [nil [:x]]]]
  ;      dependencies
  ;      missing)

  ; (->> [[:x [nil [:x]]]]
  ;      complete?))
