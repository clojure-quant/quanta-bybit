(ns quanta.bybit.quote.messaging
  (:require
   [clojure.string :as str]
   [quanta.quote.protocol :as p]
   [quanta.asset.mapper :as am]
   [quanta.bybit.impl.asset-converter :as ac]))

(def ^:private orderbook-depth 1)

(defn- orderbook-topic [symbol]
  (str "orderbook." orderbook-depth "." symbol))

(defn- subscribe-topics [asset-converter assets]
  (->> assets
       (map #(am/to-api asset-converter %))
       (map orderbook-topic)
       vec))

(defn- eventually-add-last-volume [{:keys [bid ask] :as quote}]
  (if (and bid ask)
    (assoc quote :price (/ (+ bid ask) 2.0M)
           :volume 1.0M
           :spread (- ask bid))
    (assoc quote :volume 0.0M)))

(defn- parse-level [[price-str size-str]]
  (when (and price-str size-str)
    [(bigdec price-str) (bigdec size-str)]))

(defn- snapshot->quote [asset-converter {:keys [topic data]}]
  (when (and topic data (str/starts-with? topic "orderbook."))
    (let [{:keys [s b a]} data
          [[bid _bv] [ask _av]] (map parse-level [(first b) (first a)])
          symbol (or s (last (str/split topic #"\.")))]
      (when (and symbol bid ask)
        (-> {:bid bid
             :ask ask
             :asset (am/from-api asset-converter symbol)}
            eventually-add-last-volume)))))

(defrecord quote-feed-bybit [account-config asset-converter log]
  p/quote-messaging
  (subscribe-msg [_ sub]
    (let [category (get-in account-config [:account/settings :connection :category])
          valid (filter #(ac/category-matches-asset? category %) sub)
          invalid (remove #(ac/category-matches-asset? category %) sub)
          _ (when (seq invalid)
              (log {:type :subscribe-category-mismatch
                    :category category
                    :assets invalid}))
          topics (subscribe-topics asset-converter valid)
          msg {:op "subscribe" :args topics}]
      (log {:type :subscribe :assets valid :broker-topics topics})
      msg))
  (unsubscribe-msg [_ unsub]
    (let [topics (subscribe-topics asset-converter unsub)
          msg {:op "unsubscribe" :args topics}]
      (log {:type :unsubscribe :assets unsub :broker-topics topics})
      msg))
  (read-quote [_ msg-in]
    (cond
      (and (= (:type msg-in) "snapshot")
           (:topic msg-in))
      (snapshot->quote asset-converter msg-in)

      (and (= (:op msg-in) "subscribe")
           (false? (:success msg-in)))
      (do (log {:type :subscription-failure :direction :in :data msg-in})
          nil)

      :else nil)))

(defmethod p/create-quote-messaging :bybit-quote
  [account-config asset-converter log]
  (quote-feed-bybit. account-config asset-converter log))
