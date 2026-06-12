(ns quanta.bybit.impl.auth-test
  (:require [clojure.test :refer [deftest is]]
            [quanta.bybit.impl.auth :as auth]))

(deftest auth-msg-test
  (let [msg (auth/auth-msg {:api-key "test-key"
                            :api-secret "test-secret"})]
    (is (= "auth" (:op msg)))
    (is (= 3 (count (:args msg))))
    (is (= "test-key" (first (:args msg))))
    (is (string? (nth (:args msg) 2)))))

(deftest auth-success-test
  (is (auth/auth-success? {:op "auth" :retCode 0}))
  (is (auth/auth-success? {:op "auth" :success true}))
  (is (not (auth/auth-success? {:op "auth" :retCode 10004})))
  (is (not (auth/auth-success? {:op "subscribe" :success true}))))
