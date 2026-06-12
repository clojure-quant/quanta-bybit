(ns quanta.bybit.blotter.interactor
  (:require
   [missionary.core :as m]
   [quanta.blotter.protocol :as p]
   [quanta.bybit.blotter.query :as query])
  (:import missionary.Cancelled))

(defn- query-request? [req]
  (#{:trader/open-positions :trader/working-orders} (:type req)))

(defn- request-loop
  [trade-message-processor account req-rdv res-rdv push log]
  (m/sp
   (loop []
     (let [req (m/? req-rdv)]
       (if (query-request? req)
         (let [updates (m/? (query/run-query account req log))]
           (doseq [update updates]
             (m/? (res-rdv update))))
         (when-let [payload (p/api-order trade-message-processor req)]
           (try
             (m/? (push payload))
             (catch Exception ex
               (log {:type :order-failure
                     :direction :out
                     :data {:request req :error (ex-message ex)}})))))
       (recur)))))

(defn- message-loop
  [trade-message-processor pull _log res-rdv]
  (m/sp
   (try
     (loop []
       (when-let [payload (m/? (pull))]
         (when-let [update (p/blotter-order-update trade-message-processor payload)]
           (m/? (res-rdv update))))
       (recur))
     (catch Cancelled _
       true))))

(defn create-bybit-trade-interactor
  [req-rdv res-rdv account log]
  (fn [_account _connection-id push pull _log _asset-converter]
    (let [trade-message-processor (p/create-trade-messaging account nil log)]
      (m/sp
       (m/? (m/join vector
                    (request-loop trade-message-processor account req-rdv res-rdv push log)
                    (message-loop trade-message-processor pull log res-rdv)))))))
