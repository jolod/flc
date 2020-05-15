(ns flc.let-like-test
  (:require [clojure.test :refer :all]
            [expectations.clojure.test :refer :all]
            [flc.let-like :refer :all]))

(deftest -run
  (is (vector? (run nil))))

; Note to self: check the git history for more unit tests that was removed because what they tested was already tested indirectly through e.g. flc.core.

(defexpect -ordering
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

  ; Give precedence to last occurance.
  (expect [[:foo :bar] nil]
          (stable-dependency-sort [[:foo [:bar]]
                                   [:bar [:foo]]
                                   [:foo]]))

  ; Give precedence to last occurance.
  (expect [[:foo :bar] nil]
          (stable-dependency-sort [[:bar]
                                   [:foo]
                                   [:bar]])))

(defexpect -missing-and-complete
  ; Base case, no missing dependencies.
  (let [bindings [[:x [nil []]]
                  [:y [nil [:x]]]]]
    (expect [[:x #{}]
             [:y #{}]]
            (missing (dependencies bindings)))
    (expect (complete? bindings)))

  ; Simplest missing dependency case.
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
    (expect (complete? bindings))))

(defexpect -example-from-documentation
  (expect [[:x :z :y :xy], {:rec #{:rec} :foo #{:bar}}]
          (stable-dependency-sort [[:x], [:z], [:xy [:y :x]], [:y], [:rec [:rec]], [:foo [:bar]]])))
