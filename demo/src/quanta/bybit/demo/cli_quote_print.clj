(ns quanta.bybit.demo.cli-quote-print
  (:require
   [missionary.core :as m]
   [quanta.quote.account-manager :refer [create-account-manager add-edn-accounts get-account]]
   [quanta.blotter.logger :refer [create-logger log]]
   [quanta.bybit.quote.account]))

(defn quote-printer [f]
  (m/reduce
   (fn [_ v]
     (println "QUOTE" v)
     nil)
   nil
   f))

(defn subscription-changer [subscription-a]
  (m/sp
   (m/? (m/sleep 7000))
   (println "removing ETHUSDT subscription")
   (swap! subscription-a disj "ETHUSDT.S.BB")

   (m/? (m/sleep 7000))
   (println "subscription-changer done!")
   nil))

(defn start! []
  (.mkdirs (java.io.File. "log"))
  (let [l (create-logger "log/quotes.txt" false)
        log-fn (partial log l)
        am (create-account-manager log-fn)
        _ (add-edn-accounts am "bybit-accounts-quote-test.edn")
        {:keys [flow subscription-a]} (get-account am 2002)
        _ (reset! subscription-a #{"BTCUSDT.S.BB" "ETHUSDT.S.BB"})
        printer (quote-printer flow)
        dispose-printer (printer #(println "quote-printer done" %)
                                 #(println "quote-printer CRASH" %))
        sub-changer (subscription-changer subscription-a)
        dispose-sub-changer (sub-changer #(println "sub-changer done" %)
                                         #(println "sub-changer CRASH" %))]
    {:dispose-printer dispose-printer
     :dispose-sub-changer dispose-sub-changer}))

(defn start-cli [_]
  (start!)
  @(promise))
