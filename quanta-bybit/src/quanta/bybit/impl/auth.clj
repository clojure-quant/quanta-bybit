(ns quanta.bybit.impl.auth
  (:require
   [buddy.core.codecs :as crypto.codecs]
   [buddy.core.mac :as crypto.mac]))

(defn- sign [to-sign key-secret]
  (-> (crypto.mac/hash to-sign {:key key-secret :alg :hmac+sha256})
      (crypto.codecs/bytes->hex)))

(defn auth-msg
  "Build Bybit websocket auth message for `{:api-key ... :api-secret ...}`."
  [{:keys [api-key api-secret]}]
  (let [expires (+ (System/currentTimeMillis) (* 1000 60 5))
        to-sign (str "GET/realtime" expires)
        signature (sign to-sign api-secret)]
    {:op "auth"
     :args [api-key expires signature]}))

(defn auth-response? [{:keys [op]}]
  (= op "auth"))

(defn auth-success?
  "True when Bybit acknowledges authentication."
  [{:keys [op retCode success]}]
  (and (auth-response? {:op op})
       (or (= retCode 0) (true? success))))

(defn auth-failed!
  "Throw an exception that signals non-retryable auth failure."
  [response]
  (throw (ex-info "bybit auth failed"
                  {:type ::auth-failed
                   :response response})))

(defn auth-failure?
  "True for exceptions from [[auth-failed!]] or auth timeout."
  [ex]
  (= ::auth-failed (:type (ex-data ex))))
