(ns phytomap.test
  (:require [goog.net.XhrIo :as gxhrio]))

(defn log [& more]
  (.log js/console (apply str more)))

(defn nodes-callback [reply]
  (let [nodes (js->clj (.getResponseJson (.-target reply)))]
    (log nodes)))

(.send goog.net.XhrIo "http://localhost:3000/nodes.json" 
  (fn [result] 
    (let [nodes (js->clj (.getResponseJson (.-target result)))]
    (.send goog.net.XhrIo "http://localhost:3000/stats.json"
      #(let [stats (js->clj (.getResponseJson (.-target %)))]
         (log "nodes: " nodes "\n\n\nstats: " stats)
         (log "\n\nfirst node: " (first nodes)))))))


;(log "toll " 34 " Dirky" (format "%s" "bla"))