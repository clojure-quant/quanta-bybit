(ns quanta.bybit.blotter.private-messaging
  "Private websocket: subscribe to order topic, parse async order updates.
   Updates are keyed by client order id (orderLinkId)."
  (:require
   [clojure.string :as str]
   [tick.core :as t]
   [quanta.bybit.blotter.util :as u])
  (:import [java.math BigDecimal]
           [java.time Instant]))

(def order-subscription :bybit/order)

(def ^:private order-topic "order")

(defn- ->decimal [x]
  (cond
    (nil? x) nil
    (and (string? x) (str/blank? x)) nil
    (instance? BigDecimal x) x
    (number? x) (bigdec x)
    (string? x) (bigdec x)
    :else (bigdec (str x))))

(defn- ->instant [ms]
  (cond
    (nil? ms) (t/instant)
    (instance? Instant ms) ms
    (string? ms) (Instant/ofEpochMilli (Long/parseLong ms))
    (number? ms) (Instant/ofEpochMilli (long ms))
    :else (t/instant)))

(defn subscribe-msg [_ subs]
  (when (contains? subs order-subscription)
    {:op "subscribe" :args [order-topic]}))

(defn unsubscribe-msg [_ subs]
  (when (contains? subs order-subscription)
    {:op "unsubscribe" :args [order-topic]}))

(defn- reject-message [order-id account-id reason]
  {:type :broker/order-rejected
   :account/id account-id
   :order-id order-id
   :date (t/instant)
   :message (or reason "order rejected")})

(defn- order-confirmed [account-id order-id asset side qty order-type limit date]
  (cond-> {:type :broker/order-confirmed
           :account/id account-id
           :order-id order-id
           :asset asset
           :side side
           :qty qty
           :order-type order-type
           :date date
           :message ""}
    (and (= :limit order-type) limit) (assoc :limit limit)))

(defn- order-filled [account-id order-id asset side qty price date]
  {:type :broker/order-filled
   :account/id account-id
   :order-id order-id
   :fill-id (str "bybit-" order-id "-" (.toEpochMilli ^Instant date))
   :date date
   :asset asset
   :qty qty
   :side side
   :price price})

(defn- order-canceled [account-id order-id date]
  {:type :broker/order-canceled
   :account/id account-id
   :order-id order-id
   :date date})

(defn- bybit-order->blotter [account-id {:keys [orderLinkId orderStatus rejectReason
                                               symbol category side qty price
                                               orderType cumExecQty avgPrice updatedTime]}]
  (let [order-id (some-> orderLinkId str)
        asset (u/asset-from-bybit symbol (keyword category))
        side-kw (u/side<-bybit side)
        order-type (u/order-type<-bybit orderType)
        qty-dec (->decimal qty)
        limit-dec (->decimal price)
        fill-qty (->decimal cumExecQty)
        fill-price (or (->decimal avgPrice) limit-dec)
        date (->instant updatedTime)]
    (when (and order-id asset side-kw order-type)
      (case orderStatus
        "New"
        (order-confirmed account-id order-id asset side-kw qty-dec order-type limit-dec date)

        "PartiallyFilled"
        (when (and (pos? (double fill-qty)) (pos? (double fill-price)))
          (order-filled account-id order-id asset side-kw fill-qty fill-price date))

        "Filled"
        (order-filled account-id order-id asset side-kw
                      (or fill-qty qty-dec)
                      (or fill-price limit-dec)
                      date)

        "Cancelled"
        (order-canceled account-id order-id date)

        "Rejected"
        (reject-message order-id account-id
                        (if (str/blank? rejectReason) "Rejected" rejectReason))

        "PartiallyFilledCanceled"
        (order-canceled account-id order-id date)

        nil))))

(defn read-order-update [account-id msg-in]
  (let [msg (if (and (nil? (:topic msg-in))
                      (= order-topic (get-in msg-in [:data :topic])))
              (:data msg-in)
              msg-in)]
    (cond
      (and (= (:topic msg) order-topic) (seq (:data msg)))
      (some #(bybit-order->blotter account-id %) (:data msg))

      (and (= (:op msg) "subscribe") (false? (:success msg)))
      nil

      :else nil)))

(defn create-private-messaging [account log]
  {:account account
   :log log
   :account-id (:account/id account)})

(defn subscribe-msg* [messaging subs]
  (subscribe-msg messaging subs))

(defn unsubscribe-msg* [messaging subs]
  (unsubscribe-msg messaging subs))

(defn read-order-update* [messaging msg-in]
  (read-order-update (:account-id messaging) msg-in))
