(ns phytomap.test
  (:require [goog.net.XhrIo :as gxhrio]))

(defn log [& more]
  (.log js/console (apply str more)))

(def *raw-nodes* nil)
(def *stats* nil)
(def *nodes-by-mac* nil)

(defn make-nodes-map [raw-nodes-list]
  (reduce #(assoc %1 (get-in %2 ["node" "mac"]) %2) {} raw-nodes-list))

(defn node-ping-stats [node]
  (if-let [rtt-5-min (get-in node ["stats" "rtt_5_min"])]
    rtt-5-min
    100000.0))

(defn enriched-stats [stats, nodes-map]
  ""
  (reduce #(conj %1 (let [stats (second %2)
                          mac (stats "id_hex")
                          node (if (nil? (nodes-map mac)) {} (nodes-map mac))]
                      (assoc (assoc node "mac" mac) "stats" stats))) 
          [] stats))

(.send goog.net.XhrIo "http://localhost:3000/nodes.json" 
  (fn [result] 
    (let [nodes (js->clj (.getResponseJson (.-target result)))]
      (set! *raw-nodes* nodes)
      (.send goog.net.XhrIo "http://localhost:3000/stats.json"
        #(let [stats (js->clj (.getResponseJson (.-target %)))]
           ;(log "nodes: " nodes "\n\n\nstats: " stats)
           ;(log "\n\nfirst node: " (first nodes))
           (set! *nodes-by-mac* (make-nodes-map *raw-nodes*))
           (set! *stats* (enriched-stats stats *nodes-by-mac*))
           (let [stats-sorted-by-ping (sort-by node-ping-stats < *stats*)]
             (log "stats sorted by ping: " (map node-ping-stats stats-sorted-by-ping))))))))


;(log "toll " 34 " Dirky" (format "%s" "bla"))