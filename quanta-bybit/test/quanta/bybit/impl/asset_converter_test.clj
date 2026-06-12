(ns quanta.bybit.impl.asset-converter-test
  (:require [clojure.test :refer [deftest is]]
            [quanta.asset.mapper :as p]
            [quanta.bybit.impl.asset-converter :as ac]))

(def spot-account
  {:account/settings {:connection {:category :spot}}})

(def linear-account
  {:account/settings {:connection {:category :linear}}})

(deftest parse-asset-id-test
  (is (= {:symbol "BTCUSDT" :category :spot}
         (ac/parse-asset-id "BTCUSDT.S.BB")))
  (is (= {:symbol "BTCUSDT" :category :linear}
         (ac/parse-asset-id "BTCUSDT.LF.BB")))
  (is (= {:symbol "BTC-26DEC25" :category :linear}
         (ac/parse-asset-id "BTC-26DEC25.LF.BB")))
  (is (= {:symbol "BTCUSD" :category :inverse}
         (ac/parse-asset-id "BTCUSD.IF.BB")))
  (is (nil? (ac/parse-asset-id "BTCUSDT"))))

(deftest asset-mapper-roundtrip-test
  (let [spot-mapper (p/create-asset-mapper (assoc spot-account :account/session :bybit) (fn [_]))
        linear-mapper (p/create-asset-mapper (assoc linear-account :account/session :bybit) (fn [_]))]
    (is (= "BTCUSDT" (p/to-api spot-mapper "BTCUSDT.S.BB")))
    (is (= "BTCUSDT.S.BB" (p/from-api spot-mapper "BTCUSDT")))
    (is (= "BTCUSDT.LF.BB" (p/from-api linear-mapper "BTCUSDT")))))

(deftest category-matches-asset-test
  (is (ac/category-matches-asset? :spot "BTCUSDT.S.BB"))
  (is (not (ac/category-matches-asset? :spot "BTCUSDT.LF.BB"))))
