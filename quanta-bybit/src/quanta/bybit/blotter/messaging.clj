(ns quanta.bybit.blotter.messaging
  "Trade websocket: fire-and-forget orders tagged with reqId.
   RPC replies on the trade socket produce immediate confirm/reject;
   fills and lifecycle updates still arrive on the private order stream."
  (:require
   [nano-id.core :refer [nano-id]]
   [tick.core :as t]
   [quanta.asset.mapper :as am]
   [quanta.blotter.protocol :as p]
   [quanta.bybit.blotter.util :as u]))

(defn- reject? [msg]
  (ex-info msg {}))

(defn- validate-order-type [order-type limit]
  (case order-type
    :limit (when-not limit (throw (reject? "limit orders require :limit")))
    :market (when limit (throw (reject? "market orders must not include :limit")))
    (throw (reject? "order-type must be :limit or :market"))))

(defn- asset->api
  "Resolve Bybit symbol/category. Uses asset-mapper `to-api` so connection `:mode`
   is checked against `.BB` / `.BBT`."
  [asset-converter asset]
  (let [{:keys [symbol category] :as parsed} (u/asset-category asset)]
    (when-not parsed
      (throw (reject? (str "unsupported bybit asset: " asset))))
    {:symbol (if asset-converter
               (am/to-api asset-converter asset)
               symbol)
     :category category}))

(defn- new-order-msg [asset-converter {:keys [order-id asset side qty limit order-type]}]
  (let [_ (validate-order-type order-type limit)
        {:keys [symbol category]} (asset->api asset-converter asset)
        bybit-order (cond-> {"symbol" symbol
                             "category" category
                             "side" (u/side->bybit side)
                             "orderType" (u/order-type->bybit order-type)
                             "qty" (u/format-qty asset qty)
                             "orderLinkId" (str order-id)}
                      (= :limit order-type)
                      (assoc "price" (u/format-price asset limit)))]
    {:op "order.create"
     :header (u/api-header)
     :args [bybit-order]}))

(defn- cancel-order-msg [asset-converter {:keys [order-id asset]}]
  (let [{:keys [symbol category]} (asset->api asset-converter asset)]
    {:op "order.cancel"
     :header (u/api-header)
     :args [{"category" category
             "symbol" symbol
             "orderLinkId" (str order-id)}]}))

(defn- modify-order-msg [asset-converter {:keys [order-id asset qty limit]}]
  (let [{:keys [symbol category]} (asset->api asset-converter asset)
        bybit-order (cond-> {"category" category
                             "symbol" symbol
                             "orderLinkId" (str order-id)}
                      qty (assoc "qty" (u/format-qty asset qty))
                      limit (assoc "price" (u/format-price asset limit)))]
    {:op "order.amend"
     :header (u/api-header)
     :args [bybit-order]}))

(defn- blotter-order->api [asset-converter order]
  (case (:type order)
    :trader/new-order (new-order-msg asset-converter order)
    :trader/cancel-order (cancel-order-msg asset-converter order)
    :trader/modify-order (modify-order-msg asset-converter order)
    (throw (ex-info "unsupported blotter order type"
                    {:type (:type order) :account/id (:account/id order)}))))

(defn- req-id [msg]
  (or (:reqId msg) (:req_id msg)))

(defn- rpc-success? [{:keys [retCode success]}]
  (or (true? success) (= retCode 0)))

(defn- rpc-error-msg [msg]
  (or (:retMsg msg) (:ret_msg msg) "request failed"))

(defn- create-response->blotter [account-id {:keys [order]} msg]
  (let [{:keys [order-id asset side qty limit order-type]} order
        date (t/instant)]
    (if (rpc-success? msg)
      (cond-> {:type :broker/order-confirmed
               :account/id account-id
               :order-id (str order-id)
               :asset asset
               :side side
               :qty qty
               :order-type order-type
               :date date
               :message ""}
        (and (= :limit order-type) limit) (assoc :limit limit))
      {:type :broker/order-rejected
       :account/id account-id
       :order-id (str order-id)
       :date date
       :message (rpc-error-msg msg)})))

(defn- cancel-response->blotter [account-id {:keys [order]} msg]
  (let [order-id (str (:order-id order))]
    (if (rpc-success? msg)
      {:type :broker/cancel-confirmed
       :account/id account-id
       :order-id order-id
       :message "cancel accepted"}
      {:type :broker/cancel-rejected
       :account/id account-id
       :order-id order-id
       :message (rpc-error-msg msg)})))

(defn- modify-response->blotter [account-id {:keys [order]} msg]
  (let [order-modification (select-keys order [:order-id :asset :limit :qty])
        order-base (assoc order-modification  :account/id account-id)]
    (println "modify original order: " order)
    (println "modify-response->blotter" msg)
    ; original order: 
    ;{:type :trader/modify-order, :account/id 2000, :order-id zT-6joLt, :asset BTCUSDT.S.BB, :limit 58901.0M}
    
    ; success
    #_{:retCode 0, :retExtInfo {}, :retMsg "OK", :connId d8jr12h6ri7qsd2vfnl0-5pn7, :op 
       "order.amend", :header {:Timenow 1782508563656, :X-Bapi-Limit-Status 9, :X-Bapi-Limit-Reset-Timestamp 1782508563656, 
                               :Traceid "edf7a9735d1a46ecb45d8a1a6b2dbcf2", :X-Bapi-Limit 10}, :reqId "N9d1p9PX", 
       :data {:orderLinkId "6bGcq_2z", :orderId "2246239057174947840"}}

    ; reject
    #_{:retCode 170213 :retExtInfo {} :retMsg "Order does not exist." :connId "d8jr12h6ri7qsd2vfnl0-5piv" 
       :op "order.amend" :header {:Timenow 1782507679490 :X-Bapi-Limit-Status 9 :X-Bapi-Limit-Reset-Timestamp 1782507679488
                                  :Traceid "bc6809c494414155a7a20e6df337acb7" :X-Bapi-Limit 10},
       :reqId "CpQjrzHq" :data {}}

    ;{"category" "spot", "symbol" "BTCUSDT", "orderLinkId" "bybit-demo-4", "price" "59646"}
    (if (rpc-success? msg)
      (assoc order-base :type :broker/order-modified :message "modify accepted")
      (assoc order-base :type :broker/modify-rejected :message (rpc-error-msg msg)))))

(defn- rpc-response? [{:keys [op]}]
  (#{"order.create" "order.cancel" "order.amend"} op))

(defn- resolve-pending [pending-rpc msg-in]
  (if-let [id (req-id msg-in)]
    (when-let [pending (get @pending-rpc id)]
      (swap! pending-rpc dissoc id)
      pending)
    (when (= 1 (count @pending-rpc))
      (let [[id pending] (first @pending-rpc)]
        (swap! pending-rpc dissoc id)
        pending))))

(defrecord trade-messaging-bybit [account asset-converter log pending-rpc]
  p/trade-messaging
  (api-order [{:keys [asset-converter]} blotter-order]
    (let [req-id (nano-id 8)
          msg (-> (blotter-order->api asset-converter blotter-order)
                  (assoc :reqId req-id :req_id req-id))]
      (swap! pending-rpc assoc req-id {:order blotter-order})
      msg))
  (blotter-order-update [{:keys [account pending-rpc]} msg-in]
    (when (rpc-response? msg-in)
      (when-let [pending (resolve-pending pending-rpc msg-in)]
        (case (:type (:order pending))
          :trader/new-order (create-response->blotter (:account/id account) pending msg-in)
          :trader/cancel-order (cancel-response->blotter (:account/id account) pending msg-in)
          :trader/modify-order (modify-response->blotter (:account/id account) pending msg-in)
          nil)))))

(defmethod p/create-trade-messaging :bybit-trade
  [account asset-converter log]
  (trade-messaging-bybit. account asset-converter log (atom {})))
