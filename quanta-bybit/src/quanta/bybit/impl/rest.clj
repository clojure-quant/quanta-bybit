(ns quanta.bybit.impl.rest
  (:require
   [clojure.string :as str]
   [jsonista.core :as j]
   [missionary.core :as m]
   [aleph.http :as http]
   [manifold.deferred :as d]
   [quanta.bybit.impl.auth :as auth])
  (:import [java.net URLEncoder]))

(def rest-base-urls
  {:main "https://api.bybit.com"
   :test "https://api-testnet.bybit.com"})

(def ^:private json-mapper j/keyword-keys-object-mapper)

(def ^:private derivative-categories
  [:linear :inverse :option])

(def ^:private order-categories
  [:spot :linear :inverse :option])

(defn rest-base-url [mode]
  (or (get rest-base-urls mode)
      (throw (ex-info "unknown bybit rest mode" {:mode mode}))))

(defn- encode-param [k v]
  (str (name k) "=" (URLEncoder/encode (str v) "UTF-8")))

(defn canonical-query-string
  "Sorted query string for Bybit v5 REST signing."
  [params]
  (->> params
       (sort-by (comp str key))
       (map (fn [[k v]] (encode-param k v)))
       (str/join "&")))

(defn- deferred->task [df]
  (let [v (m/dfv)]
    (d/on-realized df
                   (fn [r] (v (fn [] r)))
                   (fn [e] (v (fn [] (throw e)))))
    (m/absolve v)))

(defn- body->string [body]
  (cond
    (string? body) body
    (instance? java.io.InputStream body) (slurp body)
    (bytes? body) (String. ^bytes body)
    :else (str body)))

(defn- parse-json-body [response]
  (let [body (:body response)]
    (if (map? body)
      body
      (j/read-value (body->string body) json-mapper))))

(defn- check-ret-code [data path]
  (let [ret-code (or (:retCode data) (:ret_code data))]
    (when (nil? ret-code)
      (throw (ex-info "bybit rest invalid response"
                      {:path path :response data})))
    (when-not (or (= ret-code 0) (= ret-code "0"))
      (throw (ex-info "bybit rest error"
                      {:path path
                       :retCode ret-code
                       :retMsg (or (:retMsg data) (:ret_msg data))
                       :response data})))
    (:result data)))

(defn http-get-secure-response
  "Signed GET against Bybit v5 REST. Returns full parsed Bybit JSON body."
  [mode login path params]
  (m/sp
   (let [{:keys [api-key api-secret]} login
         query-string (canonical-query-string params)
         timestamp (str (System/currentTimeMillis))
         recv-window "8000"
         sign-payload (str timestamp api-key recv-window query-string)
         signature (auth/hmac-sha256-hex sign-payload api-secret)
         url (str (rest-base-url mode) path "?" query-string)
         response (m/? (deferred->task
                        (http/get url
                                  {:headers {"X-BAPI-API-KEY" api-key
                                             "X-BAPI-TIMESTAMP" timestamp
                                             "X-BAPI-RECV-WINDOW" recv-window
                                             "X-BAPI-SIGN" signature}
                                   :request-timeout 15000})))]
     (let [data (parse-json-body response)]
       (check-ret-code data path)
       data))))

(defn http-get-secure
  "Signed GET against Bybit v5 REST. Returns parsed `:result` map."
  [mode login path params]
  (m/sp
   (:result (m/? (http-get-secure-response mode login path params)))))

(defn- fetch-page [mode login path base-params extra-params]
  (m/? (http-get-secure mode login path (merge base-params extra-params))))

(defn get-open-orders-response
  "First-page raw Bybit response for `/v5/order/realtime`."
  [mode login category]
  (http-get-secure-response mode login "/v5/order/realtime"
                            {:category (name category)
                             :openOnly "0"
                             :limit "50"}))

(defn get-positions-response
  "First-page raw Bybit response for `/v5/position/list`."
  [mode login category extra-params]
  (http-get-secure-response mode login "/v5/position/list"
                            (merge {:category (name category)
                                    :limit "50"}
                                   extra-params)))

(defn get-wallet-balance-response
  "Raw Bybit response for `/v5/account/wallet-balance`."
  [mode login]
  (http-get-secure-response mode login "/v5/account/wallet-balance"
                            {:accountType "UNIFIED"}))

(defn get-all-pages
  "Fetch all pages for a Bybit list endpoint. `base-params` must include required
   query keys; `:cursor` is added automatically."
  [mode login path base-params]
  (m/sp
   (loop [cursor nil acc []]
     (let [extra (cond-> {:limit "50"}
                   cursor (assoc :cursor cursor))
           result (fetch-page mode login path base-params extra)
           list (or (:list result) [])
           acc (into acc list)
           next-cursor (:nextPageCursor result)]
       (if (str/blank? next-cursor)
         acc
         (recur next-cursor acc))))))

(defn get-open-orders
  "All open orders for `category` (`:spot`, `:linear`, `:inverse`, `:option`)."
  [mode login category]
  (get-all-pages mode login "/v5/order/realtime"
                 {:category (name category)
                  :openOnly "0"}))

(defn get-positions
  "Open derivative positions for `category` (`:linear`, `:inverse`, `:option`)."
  [mode login category extra-params]
  (get-all-pages mode login "/v5/position/list"
                 (merge {:category (name category)} extra-params)))

(defn get-linear-positions [mode login settle-coin]
  (get-positions mode login :linear {:settleCoin settle-coin}))

(defn get-wallet-balance
  "Unified wallet balance snapshot."
  [mode login]
  (http-get-secure mode login "/v5/account/wallet-balance"
                   {:accountType "UNIFIED"}))

(defn derivative-position-categories [] derivative-categories)
(defn order-query-categories [] order-categories)
