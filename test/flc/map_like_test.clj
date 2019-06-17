(ns flc.map-like-test
  (:require [expectations.clojure.test :refer :all])
  (:require [flc.map-like :as m]))

(defexpect test.update-nth
  (expect [1 1] (m/update-nth (range 2) -1 inc))
  (expect [1 1] (m/update-nth (range 2) 0 inc))
  (expect [0 2] (m/update-nth (range 2) 1 inc))
  (expect [0 1 11] (m/update-nth (range 2) 2 (fnil inc 10)))
  (expect [0 1 nil 11] (m/update-nth (range 2) 3 (fnil inc 10)))
  (expect [nil nil 11] (m/update-nth nil 2 (fnil inc 10)))
  (expect [nil nil 11] (m/update-nth [] 2 (fnil inc 10))))

#_(defexpect map-like
    (expect [[nil :foo :bar :foo]
             [nil :FOO :BAR :FOO]]
            (->> [nil
                  [:foo :FOO]
                  [:bar :BAR]
                  [:foo :FOO]]
                 ((juxt m/keys m/vals))))

    (expect [1 2 3]
            (->> [nil
                  [:foo 1]
                  (seq [:bar 2])]
                 (m/fmap (fnil inc 0))
                 m/vals))

    (expect [[:more] [:even :more]]
            (->> [[:key 1 :more]
                  [:key 2 :even :more]]
                 (m/fmap inc)
                 (map (comp rest rest))))

;   (expect [[:foo 1] [:bar 4] [:foo 5]]
;           (-> [[:foo 3] [:bar 4] [:foo 7]]
;               (m/update  :foo - 2)))
;   (expect {:foo 1 :bar 5}
;           (-> {:foo 3 :bar 4}
;               (m/update :foo - 2)
;               (m/update :bar inc)
;               m/->map))

;   (expect [3 7]
;           (-> [[:foo 3] [:bar 4] [:foo 7]]
;               (m/get :foo)))
;   (expect nil
;           (m/get [] :foo))
;   (expect ::default
;           (m/get [] :foo ::default))

  ; Item should go at the end if does not exist.
    (expect [[:a :a]
             [:b :b]
             [:foo :FOO]]
            (m/assoc [[:a :a]
                      [:b :b]]
                     :foo
                     :FOO))
  ; Item should replace the last item, and remove all other.
    (expect [[:a :a]
             [:b :b]
             [:c :c] [:foo :FOO]
             [:d :d]]
            (m/assoc [[:a :a] [:foo 1]
                      [:b :b] [:foo 2]
                      [:c :c] [:foo 3]
                      [:d :d]]
                     :foo
                     :FOO))
  ; Maps should be preserved ...
    (expect {:a :a
             :b :b
             :foo :FOO}
            (m/assoc {:a :a
                      :b :b}
                     :foo
                     :FOO))
  ; ... unless more than one value is assoced.
    (expect {:map? false
             :butlast {:a :a
                       :b :b}
             :last [:foo :FOO :extra]}
            (let [m (m/assoc {:a :a
                              :b :b}
                             :foo
                             :FOO
                             :extra)]
              {:map? (map? m)
               :butlast (m/->map (butlast m))
               :last (last m)}))

    (expect {:a [:a 2]
             :b [:b 3]}
            (m/fmap-keyed (fn [k v]
                            [k (inc v)])
                          {:a 1
                           :b 2}))
    (expect [[:a [:a 2]]
             [:b [:b 3] :extra]]
            (m/fmap-keyed (fn [k v]
                            [k (inc v)])
                          [[:a 1]
                           [:b 2 :extra]])))
