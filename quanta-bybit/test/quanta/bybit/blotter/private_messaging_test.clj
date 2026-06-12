(ns quanta.bybit.blotter.private-messaging-test
  (:require [clojure.test :refer [deftest is]]
            [quanta.bybit.blotter.private-messaging :as pm]))

(def account-id 2000)

(def new-order-msg
  {:topic "order"
   :data [{:orderLinkId "test-order-1"
           :orderId "1743285788525029376"
           :orderStatus "New"
           :symbol "BTCUSDT"
           :category "spot"
           :side "Buy"
           :orderType "Limit"
           :qty "0.001"
           :price "50000"
           :cumExecQty "0"
           :updatedTime "1722551860334"}]})

(def filled-order-msg
  {:topic "order"
   :data [{:orderLinkId "test-order-1"
           :orderId "1743285788525029376"
           :orderStatus "Filled"
           :symbol "BTCUSDT"
           :category "spot"
           :side "Buy"
           :orderType "Market"
           :qty "0.001"
           :price "0"
           :cumExecQty "0.001"
           :avgPrice "62765.32"
           :updatedTime "1722551860337"}]})

(deftest subscribe-msg-test
  (is (= {:op "subscribe" :args ["order"]}
         (pm/subscribe-msg nil #{pm/order-subscription})))
  (is (nil? (pm/subscribe-msg nil #{"BTCUSDT.S.BB"}))))

(deftest read-order-update-confirmed-test
  (let [update (pm/read-order-update account-id new-order-msg)]
    (is (= :broker/order-confirmed (:type update)))
    (is (= "test-order-1" (:order-id update)))
    (is (= account-id (:account/id update)))
    (is (= "BTCUSDT.S.BB" (:asset update)))))

(deftest read-order-update-filled-test
  (let [update (pm/read-order-update account-id filled-order-msg)]
    (is (= :broker/order-filled (:type update)))
    (is (= "test-order-1" (:order-id update))
        "matched by client order id (orderLinkId)")))

(deftest read-order-update-rejected-test
  (let [msg {:topic "order"
             :data [{:orderLinkId "bad-order"
                     :orderStatus "Rejected"
                     :rejectReason "EC_NoError"
                     :symbol "BTCUSDT"
                     :category "spot"
                     :side "Buy"
                     :orderType "Limit"
                     :qty "0.001"
                     :price "50000"
                     :updatedTime "1722551860334"}]}
        update (pm/read-order-update account-id msg)]
    (is (= :broker/order-rejected (:type update)))
    (is (= "bad-order" (:order-id update)))))

(deftest read-order-update-cancelled-test
  (let [msg {:topic "order"
             :data [{:orderLinkId "test-order-1"
                     :orderStatus "Cancelled"
                     :symbol "BTCUSDT"
                     :category "spot"
                     :side "Buy"
                     :orderType "Limit"
                     :qty "0.001"
                     :price "50000"
                     :updatedTime "1722551860334"}]}
        update (pm/read-order-update account-id msg)]
    (is (= :broker/order-canceled (:type update)))))
