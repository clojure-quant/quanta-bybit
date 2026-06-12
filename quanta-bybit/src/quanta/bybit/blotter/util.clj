(ns quanta.bybit.blotter.util
  (:require
   [quanta.bybit.impl.asset-converter :as ac])
  (:import [java.math BigDecimal]))

(def ^:private category->suffix
  {"spot" ".S.BB"
   "linear" ".LF.BB"
   "inverse" ".IF.BB"
   "option" ".O.BB"})

(defn category-name [category]
  (name (or category :spot)))

(defn asset-from-bybit [symbol category-kw]
  (when symbol
    (let [suffix (get category->suffix (category-name category-kw))]
      (when suffix
        (str symbol suffix)))))

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
