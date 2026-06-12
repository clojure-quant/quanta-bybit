(ns quanta.bybit.demo.cli-trade-blotter
  (:require
   [missionary.core :as m]
   [tick.core :as t]
   [quanta.blotter.account-manager :refer [create-account-manager start-account-manager add-edn-accounts]]
   [quanta.blotter.consolidator :refer [create-consolidator start-consolidator!]]
   [quanta.blotter.logger :refer [create-logger log start-log-flow-to-logger]]
   [quanta.blotter.util :refer [push-flow-to-rdv]]
   [quanta.bybit.blotter.account]
   [quanta.bybit.demo.util.orderflow-simulated :refer [demo-order-action-flow]]
   [quanta.bybit.demo.util.update-printer :refer [create-orderupdate-printer]]))

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
   (let [l (create-logger (str "log/trade-blotter-" (name mode) ".txt") false)
         _ (log l {:type :blotter/started :date (t/instant)})
         log-fn (partial log l)
         order-rdv (m/rdv)
         orderupdate-rdv (m/rdv)
         dispose-orderupdate-printer (create-orderupdate-printer orderupdate-rdv)
         consolidator (create-consolidator {:order order-rdv
                                            :orderupdate orderupdate-rdv
                                            :log log-fn})
         _ (start-consolidator! consolidator)
         {:keys [combined-flow channel]} consolidator
         {:keys [order orderupdate]} channel
         l-channel (create-logger (str "log/trade-order-orderupdate-" (name mode) ".txt") false)
         dispose-flow-logger (start-log-flow-to-logger l-channel combined-flow)
         am (create-account-manager order orderupdate log-fn)
         _ (add-edn-accounts am (accounts-edn mode))
         dispose-account (start-account-manager am)
         dispose-orderflow (push-flow-to-rdv order-rdv demo-order-action-flow)]
     {:mode mode
      :dispose-orderflow dispose-orderflow
      :dispose-account dispose-account
      :dispose-orderupdate-printer dispose-orderupdate-printer
      :dispose-flow-logger dispose-flow-logger})))

(defn start-cli
  [{:keys [mode] :or {mode :test}}]
  (start! mode)
  @(promise))
