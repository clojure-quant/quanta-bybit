(ns quanta.bybit.blotter.query
  (:require
   [clojure.string :as str]
   [missionary.core :as m]
   [quanta.bybit.blotter.util :as u]
   [quanta.bybit.impl.asset-converter :as ac]
   [quanta.bybit.impl.rest :as rest])
  (:import [java.math BigDecimal]
           [java.time Instant]))

(def ^:private quote-coins #{"USDT" "USDC" "USD"})

(def ^:private active-order-statuses
  #{"New" "PartiallyFilled" "Untriggered" "Triggered"})

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
    (nil? ms) (java.time.Instant/now)
    (instance? Instant ms) ms
    (string? ms) (Instant/ofEpochMilli (Long/parseLong ms))
    (number? ms) (Instant/ofEpochMilli (long ms))
    :else (Instant/now)))

(defn- positive? [x]
  (and x (pos? (->decimal x))))

(defn- time-in-force<-bybit [tif]
  (case tif
    "GTC" :good-till-cancel
    "IOC" :immediate-or-cancel
    "FOK" :fill-or-kill
    "PostOnly" :good-till-cancel
    :good-till-cancel))

(defn- position-qty [side size]
  (let [qty (->decimal size)]
    (case side
      "Buy" [{:long-qty qty :short-qty 0M}]
      "Sell" [{:long-qty 0M :short-qty qty}]
      [{:long-qty 0M :short-qty 0M}])))

(defn- coin->spot-asset [account coin]
  (when (and coin (not (quote-coins coin)))
    (ac/from-api-with-category account (str coin "USDT") :spot)))

(defn- with-total [items total]
  (map #(assoc % :total total) items))

(defn derivative-position->item
  "Bybit position list row -> `:broker/positions-item` (without :total).
   `mode` is `:main` or `:test` (from account connection settings)."
  ([account-id req-id category row]
   (derivative-position->item account-id req-id category :main row))
  ([account-id req-id category mode {:keys [symbol side size avgPrice markPrice positionIdx]}]
   (when (and symbol (positive? size) (not= side "None"))
     (let [account {:account/settings {:connection {:mode mode}}}
           asset (ac/from-api-with-category account symbol category)]
       (when asset
         {:type :broker/positions-item
          :account/id account-id
          :req-id req-id
          :asset asset
          :position (position-qty side size)
          :position-id (str symbol "-" (or positionIdx 0))
          :settl-price (or (->decimal avgPrice) (->decimal markPrice))})))))

(defn wallet-coin->item
  "Wallet coin row -> `:broker/positions-item` (without :total)."
  ([account-id req-id coin-row]
   (wallet-coin->item account-id req-id :main coin-row))
  ([account-id req-id mode {:keys [coin walletBalance]}]
   (let [account {:account/settings {:connection {:mode mode}}}]
     (when-let [asset (coin->spot-asset account coin)]
       (when (positive? walletBalance)
         {:type :broker/positions-item
          :account/id account-id
          :req-id req-id
          :asset asset
          :position [{:long-qty (->decimal walletBalance) :short-qty 0M}]
          :position-id coin
          :settl-price nil})))))

(defn open-order->status
  "Bybit open order row -> `:broker/order-status`."
  ([account-id category row]
   (open-order->status account-id category :main row))
  ([account-id category mode
    {:keys [orderLinkId orderId symbol side qty price orderType timeInForce
            leavesQty cumExecQty updatedTime createdTime orderStatus]}]
   (when (and (contains? active-order-statuses orderStatus)
              (some? orderLinkId)
              symbol)
     (let [account {:account/settings {:connection {:mode mode}}}
           asset (ac/from-api-with-category account symbol category)
           order-type (u/order-type<-bybit orderType)]
       (when (and asset order-type)
         (cond-> {:type :broker/order-status
                  :account/id account-id
                  :order-id (str orderLinkId)
                  :asset asset
                  :side (u/side<-bybit side)
                  :qty (->decimal qty)
                  :order-type order-type
                  :date (->instant (or updatedTime createdTime))
                  :time-in-force (time-in-force<-bybit timeInForce)
                  :leaves-qty (->decimal leavesQty)
                  :cum-qty (->decimal cumExecQty)
                  :broker-order-id (str orderId)}
           (= :limit order-type) (assoc :limit (->decimal price))))))))

(defn- account-context [account]
  (let [{:keys [connection login]} (:account/settings account)]
    {:mode (ac/connection-mode account)
     :login login}))

(defn- safe-fetch [log label task]
  (m/sp
   (try
     (m/? task)
     (catch Exception ex
       (log {:type :rest/query-failure
             :label label
             :message (ex-message ex)
             :data (ex-data ex)})
       []))))

(defn- fetch-derivative-positions [mode login log category]
  (if (= category :linear)
    (m/sp
     (let [usdt (m/? (safe-fetch log :linear-usdt (rest/get-linear-positions mode login "USDT")))
           usdc (m/? (safe-fetch log :linear-usdc (rest/get-linear-positions mode login "USDC")))]
       (into usdt usdc)))
    (safe-fetch log category (rest/get-positions mode login category {}))))

(defn- normalize-positions [account-id req-id positions category mode]
  (->> positions
       (keep #(derivative-position->item account-id req-id category mode %))
       vec))

(defn- normalize-wallet [account-id req-id mode wallet-result]
  (->> (get-in wallet-result [:list 0 :coin])
       (keep #(wallet-coin->item account-id req-id mode %))
       vec))

(defn open-positions-report
  "Missionary task producing a vector of `:broker/positions-item` messages."
  [account req-id log]
  (m/sp
   (let [{:keys [mode login]} (account-context account)
         account-id (:account/id account)
         wallet-result (m/? (safe-fetch log :wallet (rest/get-wallet-balance mode login)))
         [linear inverse option]
         (m/? (m/join vector
                      (fetch-derivative-positions mode login log :linear)
                      (fetch-derivative-positions mode login log :inverse)
                      (fetch-derivative-positions mode login log :option)))
         wallet-items (if (map? wallet-result)
                        (normalize-wallet account-id req-id mode wallet-result)
                        [])
         derivative-items (vec (concat
                                (normalize-positions account-id req-id linear :linear mode)
                                (normalize-positions account-id req-id inverse :inverse mode)
                                (normalize-positions account-id req-id option :option mode)))
         items (into wallet-items derivative-items)
         total (count items)]
     (with-total items total))))

(defn- fetch-category-orders [mode login log category]
  (safe-fetch log category (rest/get-open-orders mode login category)))

(defn working-orders-report
  "Missionary task producing a vector of `:broker/order-status` messages."
  [account _req-id log]
  (m/sp
   (let [{:keys [mode login]} (account-context account)
         account-id (:account/id account)
         [spot linear inverse option]
         (m/? (m/join vector
                      (fetch-category-orders mode login log :spot)
                      (fetch-category-orders mode login log :linear)
                      (fetch-category-orders mode login log :inverse)
                      (fetch-category-orders mode login log :option)))]
     (vec (concat
           (keep #(open-order->status account-id :spot mode %) spot)
           (keep #(open-order->status account-id :linear mode %) linear)
           (keep #(open-order->status account-id :inverse mode %) inverse)
           (keep #(open-order->status account-id :option mode %) option))))))

(defn run-query
  "Execute a trader snapshot request and return a missionary task of blotter updates."
  [account req log]
  (case (:type req)
    :trader/open-positions (open-positions-report account (:req-id req) log)
    :trader/working-orders (working-orders-report account (:req-id req) log)
    (m/sp [])))
