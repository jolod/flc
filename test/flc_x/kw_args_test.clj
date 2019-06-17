(ns flc-x.kw-args-test
  (:require [expectations.clojure.test :refer :all]
            [flc.component :as component]
            [flc-x.kw-args :refer :all]))

(defexpect flc.kw-args-test
  (let [component (component identity {:init :INIT :arg :INIT} {:arg :DEP})
        program (component/program component)
        deps (component/dependencies component)]
    (expect [:DEP] deps)
    (expect {:init :INIT
             :arg :ARG}
            (program :ARG))))
