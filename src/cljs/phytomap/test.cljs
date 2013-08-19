(ns phytomap.test
  (:require [goog.net.XhrIo :as gxhrio]))

(defn log [& more]
  (.log js/console (apply str more)))

(defn make-nodes-map [raw-nodes-list]
  )

(def *raw-nodes* nil)

(.send goog.net.XhrIo "http://localhost:3000/nodes.json" 
  (fn [result] 
    (let [nodes (js->clj (.getResponseJson (.-target result)))]
      (set! *raw-nodes* nodes)
      (log "\n HURZ: " *raw-nodes* "\n")
      (.send goog.net.XhrIo "http://localhost:3000/stats.json"
        #(let [stats (js->clj (.getResponseJson (.-target %)))]
           ;(log "nodes: " nodes "\n\n\nstats: " stats)
           ;(log "\n\nfirst node: " (first nodes))
           )))))


;(log "toll " 34 " Dirky" (format "%s" "bla"))