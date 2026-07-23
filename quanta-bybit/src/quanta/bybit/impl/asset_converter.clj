(ns quanta.bybit.impl.asset-converter
  (:require
   [clojure.string :as str]
   [quanta.asset.mapper :as p]))

(def ^:private suffix-meta
  ;; Longer suffixes first so `.BBT` matches before `.BB`.
  [[".LF.BBT" {:category :linear :endpoint :test}]
   [".IF.BBT" {:category :inverse :endpoint :test}]
   [".O.BBT" {:category :option :endpoint :test}]
   [".S.BBT" {:category :spot :endpoint :test}]
   [".LF.BB" {:category :linear :endpoint :main}]
   [".IF.BB" {:category :inverse :endpoint :main}]
   [".O.BB" {:category :option :endpoint :main}]
   [".S.BB" {:category :spot :endpoint :main}]])

(def ^:private category->suffix-main
  {:spot ".S.BB"
   :linear ".LF.BB"
   :inverse ".IF.BB"
   :option ".O.BB"})

(def ^:private category->suffix-test
  {:spot ".S.BBT"
   :linear ".LF.BBT"
   :inverse ".IF.BBT"
   :option ".O.BBT"})

(defn- category->suffix [endpoint category]
  (get (if (= endpoint :test)
         category->suffix-test
         category->suffix-main)
       category))

(defn parse-asset-id
  "Parse `BTCUSDT.LF.BB` / `BTCUSDT.LF.BBT` -> `{:symbol ... :category ... :endpoint ...}` or nil."
  [asset-id]
  (when (string? asset-id)
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
    (let [category (get-in account [:account/settings :connection :category])
          endpoint (or (get-in account [:account/settings :connection :mode]) :main)]
      (if-let [suffix (category->suffix endpoint category)]
        (str symbol suffix)
        (do (log {:type :asset-from-api-failure :symbol symbol :category category :endpoint endpoint})
            symbol)))))

(defmethod p/create-asset-mapper :bybit
  [account log]
  (bybit-asset-mapper. account log))
