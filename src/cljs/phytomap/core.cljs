(ns phytomap.core
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
  (reduce #(assoc %1 (node/mac %2) %2) {} raw-nodes-list))

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
            
(def *map* nil)
(def *markers* {})

(defn show-node [node-mac]
  (let [marker (get *markers* node-mac)]
    (log "show-node " node-mac marker)
    (.openPopup marker)))

(defn update-osm-map 
  ([nodes]
    (if (nil? *map*)
      (let [osm-map (js/L.map "map" (clj->js {"scrollWheelZoom" false}))
            cm-url "http://{s}.tile.cloudmade.com/BC9A493B41014CAABB98F0471D759707/997/256/{z}/{x}/{y}.png"
            tile-layer (js/L.tileLayer cm-url (clj->js {"maxZoom" 18 "detectRetina" true}))]
        (.addTo tile-layer osm-map)
        (set! *map* osm-map)))
    (update-osm-map nodes *map*))
  ([nodes osm-map] 
    (doseq [[k v] *markers*]
      (.removeLayer osm-map v))
    (set! *markers* {})
    (.setView osm-map (clj->js *current-location*) 14)
    (let [location-marker (L.marker (clj->js *current-location*))]
      (set! *markers* (assoc *markers* "location" location-marker))
      (.openPopup (.bindPopup (.addTo location-marker osm-map) "Standort")))
    (doseq [n nodes]
      (let [marker-icon (L.AwesomeMarkers.icon (clj->js {"icon" "coffee" "color" "red"}))
            marker (L.marker (clj->js (node/latlon n)) (comment (clj->js {"icon" marker-icon})))]
        (.bindPopup marker (str "<b>" (node/node-name n) "</b><br/>" (node/address n)))
        (set! *markers* (assoc *markers* (node/mac n) marker))
        (.addTo marker osm-map)))))

;var redMarker = L.AwesomeMarkers.icon({
;icon: 'coffee', 
;color: 'red'
;})

;L.marker([51.941196,4.512291], {icon: redMarker}).addTo(map);

;; Angular.js stuff inspired partly by:
;; https://github.com/konrad-garus/hello-cljs-angular/blob/master/src-cljs/hello_clojurescript.cljs

(def node-list-url "/nodes.json")
(def node-stats-url "/stats.json")

(defn CSimpleStatsCtrl [$scope]
  (def $scope.stats (array))
  
  (def $scope.sendEmail send-rot23-email)

  (def $scope.openSSH open-ssh)
  
  (def $scope.showNode show-node)
  
  (defn set-extended! [b]
    (aset $scope "extended" b)
    (aset $scope "simple" (not b)))
  
  (defn is-extended? []
    (aget $scope "extended"))
  
  (set-extended! false)
  
  (def $scope.setExtended set-extended!)
  
  (defn set-stats! [js-array-stats]
    (.$apply $scope #(aset $scope "stats" js-array-stats)))
    
  ; get nodes info
  (.send goog.net.XhrIo node-list-url
    (fn [result]
      (if-let [nodes (js->clj (.getResponseJson (.-target result)))]
        ; get node stats
        (.send goog.net.XhrIo node-stats-url
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
                      (update-osm-map sorted-stats)))))
              (log "Error: Could not load node stats."))))
        (log "Error: Could not load nodes info.")))))

(def SimpleStatsCtrl
  (array
   "$scope"
    CSimpleStatsCtrl))

;; ----

