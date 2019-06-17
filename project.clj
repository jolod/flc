(defproject flc "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :profiles {:dev {:dependencies [[expectations/clojure-test "1.1.1"]
                                  [com.stuartsierra/component "0.4.0"] ; Because flc-x.component.
                                  [prismatic/plumbing "0.5.5"]
                                  [criterium "0.4.4"]]}
             :benchmark {:source-paths ["benchmark"]
                         :dependencies [[prismatic/plumbing "0.5.5"]
                                        [criterium "0.4.4"]]}})
