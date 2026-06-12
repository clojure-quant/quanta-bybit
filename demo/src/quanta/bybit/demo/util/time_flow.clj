(ns quanta.bybit.demo.util.time-flow
  (:require [missionary.core :as m]))

(defn create-time-flow
  "Returns a missionary flow that fires input orders over time.
   input is a partition-2 seq: [sleep-sec order sleep-sec order ...]"
  [time-data-partitions]
  (let [input (m/seed (partition 2 time-data-partitions))]
    (m/ap (let [[sleep-sec msg] (m/?> input)]
            (when (> sleep-sec 0)
              (m/? (m/sleep (* 1000 sleep-sec))))
            msg))))
