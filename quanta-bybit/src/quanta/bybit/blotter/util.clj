(ns quanta.bybit.blotter.util
  (:require
   [quanta.bybit.impl.asset-converter :as ac])
  (:import [java.math BigDecimal]))

(defn category-name [category]
  (name (or category :spot)))

(defn asset-from-bybit
  "Build blotter asset id from Bybit symbol + category.
   Optional `endpoint` is `:main` (default) or `:test` (.BBT).
   Prefer `ac/from-api-with-category` when an account (with connection `:mode`) is available."
  ([symbol category-kw]
   (asset-from-bybit symbol category-kw :main))
  ([symbol category-kw endpoint]
   (ac/from-api-with-category
    {:account/settings {:connection {:mode (or endpoint :main)}}}
    symbol
    category-kw)))

(defn asset-category [asset-id]
  (when-let [{:keys [symbol category]} (ac/parse-asset-id asset-id)]
    {:symbol symbol :category (category-name category)}))

(defn- ->decimal [x]
  (cond
    (nil? x) nil
    (instance? BigDecimal x) x
    (number? x) (bigdec x)
    (string? x) (bigdec x)
    :else (bigdec (str x))))

(defn format-qty [_asset qty]
  (str (->decimal qty)))

(defn format-price [_asset price]
  (str (->decimal price)))

(defn side->bybit [side]
  (case side
    :buy "Buy"
    :sell "Sell"
    (throw (ex-info "unsupported side" {:side side}))))

(defn side<-bybit [side]
  (case side
    "Buy" :buy
    "Sell" :sell
    nil))

(defn order-type->bybit [order-type]
  (case order-type
    :limit "Limit"
    :market "Market"
    "Market"))

(defn order-type<-bybit [order-type]
  (case order-type
    "Limit" :limit
    "Market" :market
    nil))

(defn api-header []
  {"X-BAPI-TIMESTAMP" (str (System/currentTimeMillis))
   "X-BAPI-RECV-WINDOW" "8000"})
