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
            
(defn setup-osm-map [nodes]
  (let [osm-map (js/L.map "map" (clj->js {"scrollWheelZoom" false}))
        cm-url "http://{s}.tile.cloudmade.com/BC9A493B41014CAABB98F0471D759707/997/256/{z}/{x}/{y}.png"
        tile-layer (js/L.tileLayer cm-url (clj->js {"maxZoom" 18 "detectRetina" true}))]
    (.setView osm-map (clj->js *current-location*) 16)
    (.addTo tile-layer osm-map)
    (.addTo (L.marker (clj->js *current-location*)) osm-map)))    

;; Angular.js stuff inspired partly by:
;; https://github.com/konrad-garus/hello-cljs-angular/blob/master/src-cljs/hello_clojurescript.cljs

(defn CSimpleStatsCtrl [$scope]
  (def $scope.stats (array))
  
  (def $scope.sendEmail send-rot23-email)

  (def $scope.openSSH open-ssh)
  
  (defn set-stats! [js-array-stats]
    (.$apply $scope #(aset $scope "stats" js-array-stats)))
  
  ; get nodes info
  (.send goog.net.XhrIo "http://localhost:3000/nodes.json" 
    (fn [result]
      (if-let [nodes (js->clj (.getResponseJson (.-target result)))]
        ; get node stats
        (.send goog.net.XhrIo "http://localhost:3000/stats.json"
          (fn [result] 
            (if-let [stats (js->clj (.getResponseJson (.-target result)))]
              ; get geo-location (todo: take care of error cases i.e. refusal, not present)
              (.getCurrentPosition js/navigator.geolocation
                (fn [position]
                  (set! *current-location* [(.-latitude js/position.coords)
                                            (.-longitude js/position.coords)])
                  (let [enriched-stats (enriched-stats stats (make-nodes-map nodes))]
                    (let [sorted-stats 
                          (sort-by (fn [e] 
                                     (node/distance-to e (first *current-location*) (second *current-location*)))
                                   < 
                                   (simple-stats enriched-stats))]
                      (set-stats! (clj->js sorted-stats))
                      (setup-osm-map sorted-stats)))))
              (log "Error: Could not load node stats."))))
        (log "Error: Could not load nodes info.")))))

(def SimpleStatsCtrl
  (array
   "$scope"
    CSimpleStatsCtrl))

;; ----

