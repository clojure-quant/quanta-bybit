(ns quanta.bybit.demo.cli-trade-blotter
  (:require
   [missionary.core :as m]
   [tick.core :as t]
   [quanta.blotter.oms.server :as oms-server]
   [quanta.blotter.oms.core :refer [send-flow-messages]]
   [quanta.bybit.demo.util.orderflow-simulated :refer [demo-order-action-flow]]
   ;; side effects
   [quanta.bybit.blotter.account]
   
   ))

(defn- accounts-edn [mode]
  (case mode
    :test "bybit-accounts-trade-test.edn"
    :main "bybit-accounts-trade-main.edn"))

(defn start!
  ([]
   (start! :test))
  ([mode]
   (.mkdirs (java.io.File. "log"))
   (println "trade blotter demo mode:" mode)
   (let [oms-server  (oms-server/start-oms-server {:log-file "log/oms-server-trace.txt"
                                                   :transaction-log-file "log/oms-server-transaction.txt"
                                                   :validate? true
                                                   :tag? true
                                                   :accounts-file (accounts-edn mode)
                                                   :trading-state-print-interval-ms 5000
                                                   })
         {:keys [oms trade-db]} oms-server
         dispose-orderflow (send-flow-messages oms demo-order-action-flow)
         ]
     {:oms-server oms-server
      dispose-orderflow dispose-orderflow}
       )))

(defn start-cli
  [{:keys [mode] :or {mode :test}}]
  (start! mode)
  @(promise))
