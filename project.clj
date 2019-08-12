(defproject flc "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[expectations/clojure-test "1.1.1"]
                                  [com.stuartsierra/component "0.4.0"] ; Because flc-x.component.
                                  [prismatic/plumbing "0.5.5"]
                                  [integrant "0.7.0"]
                                  [org.clojure/java.jdbc "0.7.9"]
                                  [com.h2database/h2 "1.4.199"]]}
             :benchmark {:source-paths ["benchmark"]
                         :dependencies [[prismatic/plumbing "0.5.5"]
                                        [criterium "0.4.4"]]}})
