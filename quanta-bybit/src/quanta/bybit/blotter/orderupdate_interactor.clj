(ns quanta.bybit.blotter.orderupdate-interactor
  "Subscription + inbound loop for private order updates (same shape as quote interactor)."
  (:require
   [clojure.set :refer [difference]]
   [missionary.core :as m]
   [quanta.bybit.blotter.private-messaging :as pm])
  (:import missionary.Cancelled))

(defn- sub-unsub-sets [old new]
  (let [unsub (difference old new)
        sub (difference new old)]
    {:sub sub :unsub unsub}))

(defn- process-subscription-changes [messaging subscription-f push session-log]
  (m/ap
   (let [assets-old (atom #{})
         assets-new (m/?> 1 subscription-f)
         _ (session-log {:type :subscriptions :assets assets-new})
         {:keys [sub unsub]} (sub-unsub-sets @assets-old assets-new)]
     (reset! assets-old assets-new)
     (when (seq sub)
       (session-log {:type :subscribe :assets sub})
       (when-let [msg (pm/subscribe-msg* messaging sub)]
         (m/? (push msg))))
     (when (seq unsub)
       (session-log {:type :unsubscribe :assets unsub})
       (when-let [msg (pm/unsubscribe-msg* messaging unsub)]
         (m/? (push msg)))))))

(defn- subscription-watcher [messaging subscription-a push session-log]
  (let [sub-f (m/relieve (m/watch subscription-a))
        sub-process-f (process-subscription-changes messaging sub-f push session-log)]
    (m/reduce (fn [_ _] nil) nil sub-process-f)))

(defn- message-loop [messaging pull send-orderupdate]
  (m/sp
   (try
     (loop []
       (when-let [msg (m/? (pull))]
         (when-let [update (pm/read-order-update* messaging msg)]
           (send-orderupdate update)))
       (recur))
     (catch Cancelled _
       true))))

(defn create-orderupdate-interactor
  [subscription-a send-orderupdate]
  (fn [account _connection-id push pull log _asset-converter]
    (let [messaging (pm/create-private-messaging account log)]
      (m/sp
       (log {:type :orderupdate-interactor-start})
       (m/? (m/join vector
                    (subscription-watcher messaging subscription-a push log)
                    (message-loop messaging pull send-orderupdate)))))))
