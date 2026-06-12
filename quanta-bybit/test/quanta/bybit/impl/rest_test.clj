(ns quanta.bybit.impl.rest-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [quanta.bybit.impl.auth :as auth]
   [quanta.bybit.impl.rest :as rest]))

(deftest canonical-query-string-test
  (is (= "category=spot&openOnly=0"
         (rest/canonical-query-string {:category "spot" :openOnly "0"})))
  (is (= "a=1&b=2"
         (rest/canonical-query-string {:b "2" :a "1"}))))

(deftest rest-sign-payload-test
  (testing "Bybit v5 GET sign string"
    (let [timestamp "1234567890"
          api-key "test-key"
          recv-window "8000"
          query "category=spot&openOnly=0"
          payload (str timestamp api-key recv-window query)
          signature (auth/hmac-sha256-hex payload "test-secret")]
      (is (string? signature))
      (is (= 64 (count signature)))
      (is (= signature (auth/hmac-sha256-hex payload "test-secret"))))))

(deftest rest-base-url-test
  (is (= "https://api.bybit.com" (rest/rest-base-url :main)))
  (is (= "https://api-testnet.bybit.com" (rest/rest-base-url :test))))
