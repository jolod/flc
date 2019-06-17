(ns flc-x.partial-test
  (:require [expectations.clojure.test :refer :all]
            [flc-x.partial :as partial :refer :all]
            [flc.core :as core :refer [states]]
            [flc.map-like :as m]
            [flc.process :as process :refer [process]]))

(defexpect flc.partial-graph-test
  (expect {:x [:y]
           :y []}
          (subgraph {} contains? assoc {:x [:y]
                                        :y []
                                        :foo []} [:x]))

  (expect #{:foo :foobar}
          (reach {:foo []
                  :bar []
                  :foobar [:foo]
                  :x [:foobar]}
                 [:foobar]))

  (expect {:y #{:x}
           :x #{}
           :z #{:x}}
          (transpose {:x [:y :z]
                      :y []})))

(defexpect flc.partial.start
  (expect [[:foo :bar :foobar]
           [[:foo :FOO]
            [:bar :BAR]
            [:foobar [:FOO :BAR]]]]
          (let [started (atom [])
                add-started #(swap! started conj %)
                states (-> {:x [#(do (add-started :x) (process :X))]
                            :foo [#(do (add-started :foo) (process :FOO))]
                            :bar [#(do (add-started :bar) (process :BAR))]
                            :foobar [(fn [& args] (add-started :foobar) (process args)) [:foo :bar]]
                            :foobar-x [(fn [& args] (add-started :foobar-x) (process args)) [:foobar :x]]}
                           system
                           (start! [:foo])
                           (start! [:foobar])
                           partial/started
                           states)]
            [@started states])))

(defexpect flc.partial.stop
  (expect {:log [[:started :x]
                 [:started :foo]
                 [:started :bar]
                 [:started :foobar]
                 [:stopped :foobar]
                 [:stopped :bar]]
           :states [[:x :X]
                    [:foo :FOO]]}
          (let [log (atom [])
                started! #(swap! log conj [:started %])
                stopped! #(swap! log conj [:stopped %])
                logged (fn [[name [start deps]]]
                         [name [(fn [& args]
                                  (let [process (apply start args)]
                                    (started! name)
                                    (process/wrap-stop process (fn [stop]
                                                                   (let [ret (stop)]
                                                                     (stopped! name)
                                                                     ret)))))
                                deps]])
                states (-> {:x [#(process :X)]
                            :foo [#(process :FOO)]
                            :bar [#(process :BAR)]
                            :foobar [(comp process vector) [:foo :bar]]
                            :foobar-x [(comp process vector) [:foobar :x]]}
                           (->> (map logged))
                           system
                           (start! [:x :foobar])
                           (stop! [:bar])
                           partial/started
                           states)]
            {:log @log
             :states states}))

  (expect {:log [[:started :x]
                 [:started :foo]
                 [:started :bar]
                 [:started :foobar]
                 [:started :foobar-x]
                 [:stopped :foobar-x]
                 [:stopped :foobar]
                 [:stopped :bar]
                 [:stopped :foo]
                 [:stopped :x]
                 [:started :x]
                 [:started :foo]
                 [:started :bar]
                 [:started :foobar]
                 [:started :foobar-x]
                 [:stopped :foobar-x]
                 [:stopped :foobar]
                 [:stopped :bar]
                 [:stopped :foo]
                 [:stopped :x]]
           :states nil}
          (let [log (atom [])
                started! #(swap! log conj [:started %])
                stopped! #(swap! log conj [:stopped %])
                logged (fn [[name [start deps]]]
                         [name [(fn [& args]
                                  (let [process (apply start args)]
                                    (started! name)
                                    (process/wrap-stop process (fn [stop]
                                                                   (let [ret (stop)]
                                                                     (stopped! name)
                                                                     ret)))))
                                deps]])
                states (-> {:x [#(process :X)]
                            :foo [#(process :FOO)]
                            :bar [#(process :BAR)]
                            :foobar [(comp process vector) [:foo :bar]]
                            :foobar-x [(comp process vector) [:foobar :x]]}
                           (->> (map logged))
                           system
                           start!
                           stop!
                           start!
                           stop!
                           :started
                           states)]
            {:log @log
             :states states}))

  (expect [[:foobar [:success]]
           [:bar [:failure]]]
          (let [->safe-stop (fn [[name [start deps]]]
                              [name [(fn [& args]
                                       (let [process (apply start args)]
                                         (process/wrap-stop process (fn [stop]
                                                                        (try
                                                                          {:success (stop)}
                                                                          (catch Exception e
                                                                            {:failure e}))))))
                                     deps]])]
            (-> {:x [#(process :X)]
                 :foo [#(process :FOO)]
                 :bar [#(process :BAR (fn [_] (throw (Exception. ":bar nope"))))]
                 :foobar [(comp process vector) [:foo :bar]]}
                (->> (map ->safe-stop))
                system
                start!
                (stop+! [:bar])
                :stopped
                (->> (map #(m/update-nth % 1 keys))))))

  (expect {:log [[:started :foo]
                 [:started :bar]
                 [:started :foobar]

                 [:stopped :foobar]
                 [:stopped :bar]
                 [:started :bar]
                 [:started :foobar]

                 [:stopped :foobar]
                 [:stopped :bar]
                 [:stopped :foo]]
           :states nil}
          (let [log (atom [])
                started! #(swap! log conj [:started %])
                stopped! #(swap! log conj [:stopped %])
                logged (fn [[name [start deps]]]
                         [name [(fn [& args]
                                  (let [process (apply start args)]
                                    (started! name)
                                    (process/wrap-stop process (fn [stop]
                                                                   (let [ret (stop)]
                                                                     (stopped! name)
                                                                     ret)))))
                                deps]])
                states (-> {:foo [#(process :FOO)]
                            :bar [#(process :BAR)]
                            :foobar [(comp process vector) [:foo :bar]]}
                           (->> (map logged))
                           system
                           start!
                           (restart! [:bar])
                           stop!
                           :started
                           states)]
            {:log @log
             :states states}))

  (expect [:foo :bar :foobar]
          (-> {:foo [#(process :FOO)]
               :bar [#(process :BAR)]
               :foobar [(comp process vector) [:foo :bar]]}
              system
              start!
              (inject! {:extra [#(process :EXTRA)]})
              :started
              m/keys
              reverse))

  (let [log (atom [])
        started! #(swap! log conj [:started %])
        stopped! #(swap! log conj [:stopped %])
        logged (fn [[name [start deps]]]
                 [name [(fn [& args]
                          (let [process (apply start args)]
                            (started! name)
                            (process/wrap-stop process (fn [stop]
                                                           (let [ret (stop)]
                                                             (stopped! name)
                                                             ret)))))
                        deps]])
        states (-> {:foo [#(process :FOO)]
                    :bar [#(process :BAR)]
                    :foobar [(comp process vector) [:foo :bar]]}
                   (->> (map logged))
                   system
                   start!
                   (inject! (map logged {:bar [#(process [% :BAR]) [:foo]]}))
                   :started
                   states)]
    (expect [[:started :foo]
             [:started :bar]
             [:started :foobar]

             [:stopped :foobar]
             [:stopped :bar]
             [:started :bar]
             [:started :foobar]]
            @log)
    (expect [[:foo :FOO]
             [:bar [:FOO :BAR]]
             [:foobar [:FOO [:FOO :BAR]]]]
            states)))
