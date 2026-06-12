(ns quanta.bybit.impl.connect
  (:require
   [missionary.core :as m]
   [quanta.util.session :as p]
   [quanta.bybit.impl.websocket :as websocket]
   [quanta.bybit.impl.session :as session]))

(defn- dbg [& args]
  (apply println "[bybit-connect]" args)
  (flush))

(defmethod p/connect-and-run :bybit
  [account log interactor]
  (m/sp
   (try
     (let [connection (:connection (:account/settings account))]
       (log (assoc connection
                   :account/id (:account/id account)
                   :type :ws/connect)))
     (let [ws-socket (m/? (websocket/connect account))
           _ (log {:type :ws/connected
                   :account/id (:account/id account)
                   :url (:url ws-socket)})
           {:keys [run]} (session/create-bybit-session-task account ws-socket log interactor)]
       (log {:type :bybit-session/starting :account/id (:account/id account)})
       (let [result (m/? run)]
         (log {:type :bybit-session/stopped
               :account/id (:account/id account)
               :result result})
         (case result
           :auth-failed :auth-failed
           :run-finally)))
     (catch Exception e
       (dbg "connect-and-run: Exception" (ex-message e))
       (.printStackTrace e)
       (log {:type :bybit-session-run-ex
             :account/id (:account/id account)
             :message (ex-message e)})
       :connect-ex))))
