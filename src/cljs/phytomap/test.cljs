(ns phytomap.test
  (:require [phytomap.node :as node]
            [clojure.string :as s]
            [goog.net.XhrIo :as gxhrio]
            [goog.dom :as dom]))

(defn log [& more]
  (.log js/console (apply str more)))
  
(defn open-uri [uri]
  (let [window (dom/getWindow)
        location (.-location window)]
    (aset location "href" uri)))

(def *raw-nodes* nil)
(def *stats* nil)
(def *nodes-by-mac* nil)

(defn make-nodes-map [raw-nodes-list]
  "Takes raw-nodes-list and makes entries accessible by the MAC address for 
constant time access."
  (reduce #(assoc %1 (get-in %2 ["node" "mac"]) %2) {} raw-nodes-list))

(defn enriched-stats [stats, nodes-map]
  "Returns vector with statistics entries which contain the data from statistics 
data as well as data from nodes info data."
  (reduce #(conj %1 (let [stats (second %2)
                          mac (stats "id_hex")
                          node (if (nil? (nodes-map mac)) {} (nodes-map mac))]
                      (assoc (assoc node "mac" mac) "stats" stats))) 
          [] stats))

(def *current-location* [50.9406645 6.9599115])

(defn simple-stats [enriched-stats]
  (let [working-nodes-stats (filter #(and (node/working? %) (node/latlon %)) enriched-stats)]
    ; todo: sort by distance to current location
    (map #(assoc % "distance" (node/distance-to % (first *current-location*) (second *current-location*)))
         working-nodes-stats)))

(defn send-rot23-email [realname rot23-email]
  (open-uri (str "mailto:" (s/replace realname "," "") "<" (js/trans rot23-email -23) ">")))

(defn open-ssh [hostname]
  (open-uri (str "ssh://root@" hostname ".local")))
            
;; Angular.js stuff inspired partly by:
;; https://github.com/konrad-garus/hello-cljs-angular/blob/master/src-cljs/hello_clojurescript.cljs

(defn CSimpleStatsCtrl [$scope]
  (def $scope.stats (array))
  
  (def $scope.sendEmail send-rot23-email)

  (def $scope.openSSH open-ssh)
  
  (defn set-stats! [js-array-stats]
    (.$apply $scope #(aset $scope "stats" js-array-stats)))
  
  (.send goog.net.XhrIo "http://localhost:3000/nodes.json" 
  (fn [result]
    (let [nodes (js->clj (.getResponseJson (.-target result)))]
      (set! *raw-nodes* nodes)
      (.send goog.net.XhrIo "http://localhost:3000/stats.json"
        #(let [stats (js->clj (.getResponseJson (.-target %)))]
           ; Geolocation
           (.getCurrentPosition js/navigator.geolocation
             (fn [position]
               (set! *current-location* [(.-latitude js/position.coords)
                                         (.-longitude js/position.coords)])
               ;(log "nodes: " nodes "\n\n\nstats: " stats)
               ;(log "\n\nfirst node: " (first nodes))
               (set! *nodes-by-mac* (make-nodes-map *raw-nodes*))
               (set! *stats* (enriched-stats stats *nodes-by-mac*))
               (let [sorted-stats (sort-by (fn [e] (node/distance-to e (first *current-location*) (second *current-location*))) < (simple-stats *stats*))]
                 ;(log sorted-stats)
                 (set-stats! (clj->js sorted-stats)))))))))))


(def SimpleStatsCtrl
  (array
   "$scope"
    CSimpleStatsCtrl))

;; ----

