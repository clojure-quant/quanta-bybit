(ns quanta.bybit.blotter.account
  (:require
   [missionary.core :as m]
   [quanta.util.boot :refer [boot-with-retry]]
   [quanta.blotter.protocol :as p]
   [quanta.blotter.interactor :refer [create-trade-interactor]]
   [quanta.bybit.blotter.orderupdate-interactor :refer [create-orderupdate-interactor]]
   [quanta.bybit.blotter.private-messaging :refer [order-subscription]]
   [quanta.bybit.blotter.messaging]
   [quanta.bybit.impl.asset-converter]
   [quanta.bybit.impl.connect]))

(defn- order-bridge [order-rdv req-rdv log]
  (m/sp
   (loop []
     (let [order (m/? order-rdv)]
       (try
         (m/? (req-rdv order))
         (catch Exception ex
           (log {:type :order-failure
                 :direction :in
                 :data {:order order :error (ex-message ex)}}))))
     (recur))))

(defn- update-bridge [res-rdv update-rdv]
  (m/sp
   (loop []
     (let [update (m/? res-rdv)]
       (when update
         (m/? (update-rdv update))))
     (recur))))

(defn- account-log-fn [account-id log]
  (fn [event]
    (log (assoc event :account/id account-id))))

(defn- with-connection-category [account category]
  (assoc-in account [:account/settings :connection :category] category))

(defmethod p/create-trade-account :bybit-trade
  [account order-rdv update-rdv log]
  (let [account (assoc account :account/session :bybit)
        {:keys [account/id]} account
        req-rdv (m/rdv)
        res-rdv (m/rdv)
        subscription-a (atom #{order-subscription})
        give-update (fn [update] (m/? (res-rdv update)))
        trade-interactor (create-trade-interactor req-rdv res-rdv)
        orderupdate-interactor (create-orderupdate-interactor subscription-a give-update)
        trade-account (with-connection-category account :trade)
        private-account (with-connection-category account :private)
        account-log (account-log-fn id log)]
    (m/sp
     (m/? (m/join vector
                  (boot-with-retry trade-account account-log trade-interactor)
                  (boot-with-retry private-account account-log orderupdate-interactor)
                  (order-bridge order-rdv req-rdv log)
                  (update-bridge res-rdv update-rdv))))))
