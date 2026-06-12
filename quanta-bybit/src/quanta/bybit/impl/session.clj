(ns quanta.bybit.impl.session
  (:require
   [missionary.core :as m]
   [nano-id.core :refer [nano-id]]
   [quanta.asset.mapper :refer [create-asset-mapper]]
   [quanta.bybit.impl.auth :as auth]
   [quanta.bybit.impl.websocket :as ws]
   [manifold.stream :as s])
  (:import missionary.Cancelled))

(defn- dbg [& args]
  (apply println "[bybit-session]" args)
  (flush))

(def ^:private ping-payload {:op "ping"})

(defn- timeout-watchdog [mailbox ms]
  (m/sp
   (loop []
     (when (= :timeout (m/? (m/timeout mailbox ms :timeout)))
       (throw (ex-info "No message received after specified time"
                       {::type ::timeout ::time-seconds (int (/ ms 1000))})))
     (recur))))

(defn- ping-sender [push]
  (m/sp
   (loop []
     (m/? (m/sleep 20000))
     (m/? (push ping-payload))
     (recur))))

(defn- ws-reader
  "Reads decoded maps from websocket pull, delivers on mailbox."
  [ws-pull log in-mbx keepalive]
  (m/sp
   (try
     (loop []
       (when-let [msg (m/? (ws-pull))]
         (log {:type :json-msg :direction :in :data msg})
         (keepalive nil)
         (in-mbx msg)
         (recur)))
     (catch Cancelled _
       (in-mbx nil)))))

(defn- login? [account]
  (some? (:login (:account/settings account))))

(defn- authenticate! [account push pull log]
  (m/sp
   (dbg "session: sending auth")
   (log {:type :connection-status :data :authenticating})
   (m/? (push (auth/auth-msg (:login (:account/settings account)))))
   (loop [n 0]
     (let [msg (m/? (pull))]
       (dbg "session: waiting for auth, got" (:op msg) "n=" n)
       (cond
         (auth/auth-success? msg)
         (do (log {:type :connection-status :data :authenticated})
             (dbg "session: authenticated"))

         (auth/auth-response? msg)
         (throw (ex-info "bybit auth failed" {:response msg}))

         (< n 100)
         (recur (inc n))

         :else
         (throw (ex-info "bybit auth timeout" {})))))))

(defn create-bybit-session-task
  [account ws-socket log interactor]
  (let [connection-id (nano-id 16)
        log* (fn [event]
               (log (assoc event :connection-id connection-id)))
        ws-push (:push ws-socket)
        ws-pull (:pull ws-socket)
        stream (:stream ws-socket)
        in-mbx (m/mbx)
        push (fn [msg]
               (m/sp
                (log* {:type :json-msg :direction :out :data msg})
                (m/? (ws-push msg))))
        pull (fn []
               (m/sp
                (m/? in-mbx)))
        session-body (fn [asset-converter keepalive]
                     (m/sp
                      (dbg "session: connecting" (:url ws-socket))
                      (log* {:type :connection-status :data :connecting})
                      (when (login? account)
                        (m/? (authenticate! account push pull log*)))
                      (dbg "session: ready, starting interactor")
                      (log* {:type :connection-status :data :ready})
                      (m/? (m/join vector
                                   (interactor account connection-id push pull log* asset-converter)
                                   (ping-sender push)
                                   (timeout-watchdog keepalive 90000)))))
        run (m/sp
             (let [keepalive (m/mbx)
                   reader (ws-reader ws-pull log* in-mbx keepalive)
                   asset-converter (create-asset-mapper account log)]
               (try
                 (m/? (m/join vector reader (session-body asset-converter keepalive)))
                 (catch Cancelled _
                   :cancelled)
                 (catch Exception ex
                   (dbg "session: exception" (ex-message ex) (ex-data ex))
                   (log* {:type :connection-status
                          :data {:error (ex-message ex) :data (ex-data ex)}})
                   (throw ex))
                 (finally
                   (log* {:type :connection-status :data :disconnected})
                   (when stream
                     (s/close! stream))))))]
    {:connection-id connection-id
     :push push
     :pull pull
     :run run}))
