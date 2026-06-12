(ns quanta.bybit.demo.cli-rest-print
  "Direct REST demo: fetch open orders, positions, wallet and print raw responses."
  (:require
   [clojure.edn :as edn]
   [clojure.pprint :as pprint]
   [missionary.core :as m]
   [quanta.bybit.impl.rest :as rest]))

(defn- accounts-edn [mode]
  (case mode
    :test "bybit-accounts-trade-test.edn"
    :main "bybit-accounts-trade-main.edn"))

(defn- load-account [mode]
  (first (edn/read-string (slurp (accounts-edn mode)))))

(defn- login-from-account [account]
  (get-in account [:account/settings :login]))

(defn- mode-from-account [account mode]
  (or (get-in account [:account/settings :connection :mode]) mode))

(defn- print-raw [title data]
  (println)
  (println (str "=== " title " ==="))
  (if data
    (pprint/pprint data)
    (println "(nil)")))

(defn- safe-response [label task]
  (try
    (m/? task)
    (catch Exception ex
      {:error (ex-message ex)
       :data (ex-data ex)
       :label label})))

(defn print-rest!
  ([]
   (print-rest! :test))
  ([mode]
   (let [account (load-account mode)
         login (login-from-account account)
         api-mode (mode-from-account account mode)]
     (println "bybit REST raw responses mode:" api-mode)

     (print-raw "get-open-orders :spot"
                (safe-response "open-orders spot"
                               (rest/get-open-orders-response api-mode login :spot)))
     (print-raw "get-open-orders :linear"
                (safe-response "open-orders linear"
                               (rest/get-open-orders-response api-mode login :linear)))
     (print-raw "get-open-orders :inverse"
                (safe-response "open-orders inverse"
                               (rest/get-open-orders-response api-mode login :inverse)))
     (print-raw "get-open-orders :option"
                (safe-response "open-orders option"
                               (rest/get-open-orders-response api-mode login :option)))

     (print-raw "get-positions :linear settleCoin=USDT"
                (safe-response "positions linear USDT"
                               (rest/get-positions-response api-mode login :linear
                                                            {:settleCoin "USDT"})))
     (print-raw "get-positions :linear settleCoin=USDC"
                (safe-response "positions linear USDC"
                               (rest/get-positions-response api-mode login :linear
                                                            {:settleCoin "USDC"})))
     (print-raw "get-positions :inverse"
                (safe-response "positions inverse"
                               (rest/get-positions-response api-mode login :inverse {})))
     (print-raw "get-positions :option"
                (safe-response "positions option"
                               (rest/get-positions-response api-mode login :option {})))

     (print-raw "get-wallet-balance"
                (safe-response "wallet"
                               (rest/get-wallet-balance-response api-mode login))))))

(defn start-cli
  [{:keys [mode] :or {mode :test}}]
  (print-rest! mode))

(defn -main [& _]
  (start-cli {:mode :test}))
