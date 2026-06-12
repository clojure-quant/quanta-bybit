(ns quanta.bybit.quote.account
  (:require
   [quanta.util.boot :refer [boot-with-retry]]
   [quanta.quote.protocol :as p]
   [quanta.quote.interactor :refer [create-quote-interactor]]
   [quanta.bybit.quote.messaging]
   [quanta.bybit.impl.asset-converter]
   [quanta.bybit.impl.connect]))

(defmethod p/create-quote-account :bybit-quote
  [account subscription-a send-quote log]
  (let [account (assoc account :account/session :bybit)
        interactor (create-quote-interactor subscription-a send-quote)]
    (boot-with-retry account log interactor)))
