(ns quanta.bybit.impl.websocket
  (:require
   [missionary.core :as m]
   [jsonista.core :as j]
   [aleph.http :as http]
   [manifold.deferred :as d]
   [manifold.stream :as s])
  (:import missionary.Cancelled))

(def websocket-urls
  {:main {:spot "wss://stream.bybit.com/v5/public/spot"
          :linear "wss://stream.bybit.com/v5/public/linear"
          :inverse "wss://stream.bybit.com/v5/public/inverse"
          :option "wss://stream.bybit.com/v5/public/option"
          :trade "wss://stream.bybit.com/v5/trade"
          :private "wss://stream.bybit.com/v5/private"}
   :test {:spot "wss://stream-testnet.bybit.com/v5/public/spot"
          :linear "wss://stream-testnet.bybit.com/v5/public/linear"
          :inverse "wss://stream-testnet.bybit.com/v5/public/inverse"
          :option "wss://stream-testnet.bybit.com/v5/public/option"
          :trade "wss://stream-testnet.bybit.com/v5/trade"
          :private "wss://stream-testnet.bybit.com/v5/private"}})

(defn ws-url [mode category]
  (get-in websocket-urls [mode category]))

(defn- deferred->task [df]
  (let [v (m/dfv)]
    (d/on-realized df
                   (fn [r] (v (fn [] r)))
                   (fn [e] (v (fn [] (throw e)))))
    (m/absolve v)))

(defn- connected? [stream]
  (when stream
    (let [desc (s/description stream)]
      (and (not (-> desc :sink :closed?))
           (not (-> desc :source :closed?))))))

(defn- encode-msg [msg]
  (j/write-value-as-string msg))

(defn- decode-msg [json]
  (j/read-value json j/keyword-keys-object-mapper))

(defn connect
  "Connect a Bybit websocket for `:connection` from account settings.
   Returns a missionary task producing `{:stream :push :pull}`.
   `push` accepts Clojure maps (encoded to JSON); `pull` yields decoded maps."
  [account]
  (m/sp
   (let [{:keys [mode category]} (:connection (:account/settings account))
         url (or (ws-url mode category)
                 (throw (ex-info "unknown bybit websocket destination"
                                 {:mode mode :category category})))
         stream (m/? (deferred->task (http/websocket-client url)))
         push (fn [msg]
                (m/sp
                 (when-not (connected? stream)
                   (throw (ex-info "websocket push failed (not connected)"
                                   {:url url})))
                 (let [json (encode-msg msg)
                       ok? (m/? (deferred->task (s/put! stream json)))]
                   (when-not ok?
                     (throw (ex-info "websocket push failed" {:msg msg}))))))
         pull (fn []
                (m/sp
                 (try
                   (when-let [json (m/? (deferred->task (s/take! stream)))]
                     (decode-msg json))
                   (catch Cancelled _
                     nil))))]
     {:stream stream :url url :push push :pull pull})))
