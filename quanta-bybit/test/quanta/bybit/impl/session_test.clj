(ns quanta.bybit.impl.session-test
  (:require [clojure.test :refer [deftest is]]
            [quanta.util.session :as session]
            [quanta.bybit.impl.session]
            [quanta.bybit.impl.connect]
            [quanta.bybit.quote.account]))

(deftest namespaces-load-test
  (is (fn? quanta.bybit.impl.session/create-bybit-session-task))
  (is (contains? (methods session/connect-and-run) :bybit)))
