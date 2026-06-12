(ns quanta.bybit.blotter.messaging
  "Trade websocket: fire-and-forget orders tagged with reqId.
   RPC replies on the trade socket produce immediate confirm/reject;
   fills and lifecycle updates still arrive on the private order stream."
  (:require
   [nano-id.core :refer [nano-id]]
   [tick.core :as t]
   [quanta.blotter.protocol :as p]
   [quanta.bybit.blotter.util :as u]))

(defn- reject? [msg]
  (ex-info msg {}))

(defn- validate-order-type [order-type limit]
  (case order-type
    :limit (when-not limit (throw (reject? "limit orders require :limit")))
    :market (when limit (throw (reject? "market orders must not include :limit")))
    (throw (reject? "order-type must be :limit or :market"))))

(defn- new-order-msg [{:keys [order-id asset side qty limit order-type]}]
  (let [_ (validate-order-type order-type limit)
        {:keys [symbol category]} (u/asset-category asset)
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

(defn- cancel-order-msg [{:keys [order-id asset]}]
  (let [{:keys [symbol category]} (u/asset-category asset)]
    {:op "order.cancel"
     :header (u/api-header)
     :args [{"category" category
             "symbol" symbol
             "orderLinkId" (str order-id)}]}))

(defn- blotter-order->api [order]
  (case (:type order)
    :trader/new-order (new-order-msg order)
    :trader/cancel-order (cancel-order-msg order)
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

(defn- rpc-response? [{:keys [op]}]
  (#{"order.create" "order.cancel"} op))

(defn- resolve-pending [pending-rpc msg-in]
  (if-let [id (req-id msg-in)]
    (when-let [pending (get @pending-rpc id)]
      (swap! pending-rpc dissoc id)
      pending)
    (when (= 1 (count @pending-rpc))
      (let [[id pending] (first @pending-rpc)]
        (swap! pending-rpc dissoc id)
        pending))))

(defrecord trade-messaging-bybit [account log pending-rpc]
  p/trade-messaging
  (api-order [_ blotter-order]
    (let [req-id (nano-id 8)
          msg (-> blotter-order
                  blotter-order->api
                  (assoc :reqId req-id :req_id req-id))]
      (swap! pending-rpc assoc req-id {:order blotter-order})
      msg))
  (blotter-order-update [{:keys [account pending-rpc]} msg-in]
    (when (rpc-response? msg-in)
      (when-let [pending (resolve-pending pending-rpc msg-in)]
        (case (:type (:order pending))
          :trader/new-order (create-response->blotter (:account/id account) pending msg-in)
          :trader/cancel-order (cancel-response->blotter (:account/id account) pending msg-in)
          nil)))))

(defmethod p/create-trade-messaging :bybit-trade
  [account _asset-converter log]
  (trade-messaging-bybit. account log (atom {})))
