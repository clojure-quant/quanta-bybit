(ns quanta.bybit.impl.asset-converter
  (:require
   [clojure.string :as str]
   [quanta.asset.mapper :as p]))

(def ^:private suffix-meta
  [[".LF.BB" {:category :linear}]
   [".IF.BB" {:category :inverse}]
   [".O.BB" {:category :option}]
   [".S.BB" {:category :spot}]])

(def ^:private category->suffix
  {:spot ".S.BB"
   :linear ".LF.BB"
   :inverse ".IF.BB"
   :option ".O.BB"})

(defn parse-asset-id
  "Parse `BTCUSDT.LF.BB` -> `{:symbol ... :category ...}` or nil."
  [asset-id]
  (when (and (string? asset-id) (str/ends-with? asset-id ".BB"))
    (some (fn [[suffix meta]]
            (when (str/ends-with? asset-id suffix)
              (assoc meta :symbol (subs asset-id 0 (- (count asset-id) (count suffix))))))
          suffix-meta)))

(defn category-matches-asset? [account-category asset-id]
  (when-let [{:keys [category]} (parse-asset-id asset-id)]
    (= category account-category)))

(defrecord bybit-asset-mapper [account log]
  p/asset-mapper
  (to-api [_ asset-id]
    (if-let [{:keys [symbol]} (parse-asset-id asset-id)]
      symbol
      (do (log {:type :asset-parse-failure :asset asset-id})
          asset-id)))
  (from-api [_ symbol]
    (let [category (get-in account [:account/settings :connection :category])]
      (if-let [suffix (category->suffix category)]
        (str symbol suffix)
        (do (log {:type :asset-from-api-failure :symbol symbol :category category})
            symbol)))))

(defmethod p/create-asset-mapper :bybit
  [account log]
  (bybit-asset-mapper. account log))
