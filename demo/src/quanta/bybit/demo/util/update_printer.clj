(ns quanta.bybit.demo.util.update-printer
  (:require [missionary.core :as m]))

(defn print-orderupdate [r]
  (m/sp
   (loop []
     (println "ORDERUPDATE" (m/? r))
     (recur))))

(defn create-orderupdate-printer [orderupdate-rdv]
  (let [print-t (print-orderupdate orderupdate-rdv)]
    (print-t #(println "orderupdate-print done" %)
            #(println "orderupdate-print error" %))))
