(ns quanta.bybit.demo.cli-quote-spot-future
  (:require
   [clojure.string :as str]
   [missionary.core :as m]
   [quanta.quote.account-manager :refer [create-account-manager add-edn-accounts get-account]]
   [quanta.blotter.logger :refer [create-logger log]]
   [quanta.bybit.quote.account]))

(def ^:private pairs
  [{:symbol "BTCUSDT"
    :spot "BTCUSDT.S.BB"
    :future "BTCUSDT.LF.BB"}
   {:symbol "ETHUSDT"
    :spot "ETHUSDT.S.BB"
    :future "ETHUSDT.LF.BB"}])

(defn- base-symbol [asset]
  (first (str/split asset #"\.")))

(defn- spread-map [{:keys [spot future]}]
  (when (and spot future)
    (let [s (double spot)
          f (double future)
          abs (- f s)]
      {:spot s
       :future f
       :abs abs
       :prct (* 100.0 (/ abs s))})))

(defn- print-spread! [symbol state]
  (when-let [{:keys [spot future abs prct]} (spread-map (get @state symbol))]
    (println (format "%s  spot %.2f  future %.2f  spread %+.4f (%+.3f%%)"
                     symbol spot future abs prct))))

(defn spot-future-printer [spot-flow future-flow]
  (let [state (atom {})
        on-quote (fn [side q]
                   (when-let [asset (:asset q)]
                     (let [sym (base-symbol asset)]
                       (swap! state update sym assoc side (:price q))
                       (print-spread! sym state)))
                   nil)
        dispose-spot ((m/reduce (fn [_ q] (on-quote :spot q) nil) nil spot-flow)
                      (constantly nil) #(println "spot-flow error" %))
        dispose-future ((m/reduce (fn [_ q] (on-quote :future q) nil) nil future-flow)
                        (constantly nil) #(println "future-flow error" %))]
    (fn []
      (dispose-spot)
      (dispose-future))))

(def ^:private valid-modes #{:test :main})

(defn- accounts-edn [mode]
  (case mode
    :test "bybit-accounts-quote-test.edn"
    :main "bybit-accounts-quote-main.edn"))

(defn start!
  ([]
   (start! :test))
  ([mode]
   (when-not (valid-modes mode)
     (throw (ex-info "mode must be :test or :main" {:mode mode})))
   (.mkdirs (java.io.File. "log"))
   (println "spot-future demo mode:" mode)
   (let [log-file (str "log/spot-future-" (name mode) ".txt")
         l (create-logger log-file false)
         log-fn (partial log l)
         am (create-account-manager log-fn)
         _ (add-edn-accounts am (accounts-edn mode))
         ;; account 2002: spot WS feed
         spot-acct (get-account am 2002)
         _ (reset! (:subscription-a spot-acct) (set (map :spot pairs)))
         ;; account 2003: linear WS feed
         linear-acct (get-account am 2003)
         _ (reset! (:subscription-a linear-acct) (set (map :future pairs)))
         dispose-printer (spot-future-printer (:flow spot-acct) (:flow linear-acct))]
     {:mode mode
      :dispose-printer dispose-printer})))

(defn start-cli
  "Run with `clojure -X:quote-spot-future` or `clojure -X:quote-spot-future :mode :main`."
  [{:keys [mode] :or {mode :test}}]
  (start! mode)
  @(promise))
