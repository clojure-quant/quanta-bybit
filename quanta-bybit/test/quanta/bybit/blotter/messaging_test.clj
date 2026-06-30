(ns quanta.bybit.blotter.messaging-test
  (:require [clojure.test :refer [deftest is testing]]
            [quanta.blotter.protocol :as p]
            [quanta.bybit.blotter.messaging]))

(def account {:account/id 2000
              :account/api :bybit-trade})

(defn- messaging []
  (p/create-trade-messaging account nil (fn [_])))

(def new-order
  {:type :trader/new-order
   :account/id 2000
   :order-id "test-order-1"
   :asset "BTCUSDT.S.BB"
   :side :buy
   :order-type :limit
   :limit 50000M
   :qty 0.001M})

(deftest api-order-new-test
  (let [m (messaging)
        msg (p/api-order m new-order)]
    (is (= "order.create" (:op msg)))
    (is (= "test-order-1" (get-in msg [:args 0 "orderLinkId"])))
    (is (string? (:reqId msg)))
    (is (= (:reqId msg) (:req_id msg)))))

(deftest api-order-cancel-test
  (let [m (messaging)
        order {:type :trader/cancel-order
               :account/id 2000
               :order-id "test-order-1"
               :asset "BTCUSDT.S.BB"}
        msg (p/api-order m order)]
    (is (= "order.cancel" (:op msg)))
    (is (= "spot" (get-in msg [:args 0 "category"])))
    (is (= "BTCUSDT" (get-in msg [:args 0 "symbol"])))
    (is (string? (:reqId msg)))))

(deftest api-order-modify-test
  (let [m (messaging)
        order {:type :trader/modify-order
               :account/id 2000
               :order-id "test-order-1"
               :asset "BTCUSDT.S.BB"
               :qty 0.002M
               :limit 51000M}
        msg (p/api-order m order)]
    (is (= "order.amend" (:op msg)))
    (is (= "spot" (get-in msg [:args 0 "category"])))
    (is (= "BTCUSDT" (get-in msg [:args 0 "symbol"])))
    (is (= "test-order-1" (get-in msg [:args 0 "orderLinkId"])))
    (is (= "0.002" (get-in msg [:args 0 "qty"])))
    (is (= "51000" (get-in msg [:args 0 "price"])))))

(deftest api-order-modify-qty-only-test
  (let [m (messaging)
        order {:type :trader/modify-order
               :account/id 2000
               :order-id "test-order-1"
               :asset "BTCUSDT.LF.BB"
               :qty 0.5M}
        msg (p/api-order m order)]
    (is (= "linear" (get-in msg [:args 0 "category"])))
    (is (= "0.5" (get-in msg [:args 0 "qty"])))
    (is (nil? (get-in msg [:args 0 "price"])))))

(deftest rpc-reject-test
  (testing "immediate reject from trade websocket RPC reply"
    (let [m (messaging)
          msg (p/api-order m new-order)
          req-id (:reqId msg)
          reply {:op "order.create"
                 :reqId req-id
                 :retCode 170131
                 :retMsg "Insufficient balance."
                 :data {}}
          update (p/blotter-order-update m reply)]
      (is (= :broker/order-rejected (:type update)))
      (is (= "test-order-1" (:order-id update)))
      (is (= "Insufficient balance." (:message update)))
      (is (nil? (p/blotter-order-update m reply))
          "pending entry removed after first match"))))

(deftest rpc-confirm-test
  (let [m (messaging)
        msg (p/api-order m new-order)
        reply {:op "order.create"
               :reqId (:reqId msg)
               :retCode 0
               :retMsg "OK"
               :data {:orderLinkId "test-order-1"}}
        update (p/blotter-order-update m reply)]
    (is (= :broker/order-confirmed (:type update)))
    (is (= "BTCUSDT.S.BB" (:asset update)))))

(deftest rpc-cancel-reject-test
  (let [m (messaging)
        cancel {:type :trader/cancel-order
                :account/id 2000
                :order-id "test-order-1"
                :asset "BTCUSDT.S.BB"}
        msg (p/api-order m cancel)
        reply {:op "order.cancel"
               :reqId (:reqId msg)
               :retCode 110001
               :retMsg "order not exists or too late to cancel"}
        update (p/blotter-order-update m reply)]
    (is (= :broker/cancel-rejected (:type update))
        "cancel RPC failure")))

(deftest rpc-modify-reject-test
  (let [m (messaging)
        modify {:type :trader/modify-order
                :account/id 2000
                :order-id "test-order-1"
                :asset "BTCUSDT.S.BB"
                :limit 51000M}
        msg (p/api-order m modify)
        reply {:op "order.amend"
               :reqId (:reqId msg)
               :retCode 110001
               :retMsg "order not exists or too late to replace"}
        update (p/blotter-order-update m reply)]
    (is (= :broker/modify-rejected (:type update))
        "modify RPC failure")))

(deftest rpc-modify-accept-test
  (testing "amend RPC success emits broker/order-modified"
    (let [m (messaging)
          modify {:type :trader/modify-order
                  :account/id 2000
                  :order-id "test-order-1"
                  :asset "BTCUSDT.S.BB"
                  :qty 0.002M}
          msg (p/api-order m modify)
          reply {:op "order.amend"
                 :reqId (:reqId msg)
                 :retCode 0
                 :retMsg "OK"
                 :data {:orderLinkId "test-order-1"}}
          update (p/blotter-order-update m reply)]
      (is (= :broker/order-modified (:type update)))
      (is (= "test-order-1" (:order-id update)))
      (is (= "BTCUSDT.S.BB" (:asset update)))
      (is (= 0.002M (:qty update)))
      (is (= "modify accepted" (:message update)))
      (is (nil? (p/blotter-order-update m reply))
          "pending entry removed after first match"))))

(deftest rpc-reject-without-reqid-test
  (testing "fallback when reply omits reqId but only one order is pending"
    (let [m (messaging)
          _ (p/api-order m new-order)
          reply {:op "order.create"
                 :retCode 170131
                 :retMsg "Insufficient balance."
                 :data {}}
          update (p/blotter-order-update m reply)]
      (is (= :broker/order-rejected (:type update)))
      (is (= "Insufficient balance." (:message update))))))

(deftest ignore-unmatched-test
  (is (nil? (p/blotter-order-update (messaging) {:op "pong"})))
  (is (nil? (p/blotter-order-update (messaging)
                                    {:op "order.create"
                                     :reqId "unknown"
                                     :retCode 0}))))
