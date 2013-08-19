(ns phytomap.test
  (:require [goog.net.XhrIo :as gxhrio]))

(def *raw-nodes* nil)

(def *nodes-by-mac* nil)


(defn log [& more]
  (.log js/console (apply str more)))

(defn make-node-map-entry [map key value]
  (log "appending value for key " key " to map of size " (count map))  
  (assoc map key value)
  )

(defn make-nodes-map [raw-nodes-list]
  (log (count raw-nodes-list))
  (reduce #(make-node-map-entry %1 ((%2 "node") "mac") %2) {} *raw-nodes*)
  )


(.send goog.net.XhrIo "http://localhost:3000/nodes.json" 
  (fn [result] 
    (let [nodes (js->clj (.getResponseJson (.-target result)))]
      (set! *raw-nodes* nodes)
      ;(log "\n HURZ: " *raw-nodes* "\n")
      (.send goog.net.XhrIo "http://localhost:3000/stats.json"
        #(let [stats (js->clj (.getResponseJson (.-target %)))]
           ;(log "nodes: " nodes "\n\n\nstats: " stats)
           ;(log "\n\nfirst node: " (first nodes))
           (set! *nodes-by-mac* (make-nodes-map *raw-nodes*))
           )))))


;(log "toll " 34 " Dirky" (format "%s" "bla"))