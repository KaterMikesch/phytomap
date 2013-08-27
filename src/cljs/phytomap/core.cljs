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

(defn enriched-stats [stats nodes-map]
  "Returns vector with statistics entries which contain the data from statistics 
data as well as data from nodes info data."
  (reduce #(conj %1 (let [stats (second %2)
                          mac (stats "id_hex")
                          node (if (nil? (nodes-map mac)) {} (nodes-map mac))]
                      (assoc (assoc node "mac" mac) "stats" stats))) 
          [] stats))

(def *current-location* [50.9406645 6.9599115])

(defn make-nodes-with-distance [nodes]
  (map (fn [e] 
;         (log (node/latlon e)) 
         (if (node/latlon e) 
           (assoc e "distance" (node/distance-to e (first *current-location*) (second *current-location*))) 
           e))
       nodes))

(defn send-rot23-email [realname rot23-email]
  (open-uri (str "mailto:" (s/replace realname "," "") "<" (js/trans rot23-email -23) ">")))

(defn open-ssh [hostname]
  (open-uri (str "ssh://root@" hostname ".local")))
            
(def *enriched-stats* nil)
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
        (set! *map* osm-map)
        (.setView *map* (clj->js *current-location*) 14)))
    (update-osm-map nodes *map*))
  ([nodes osm-map] 
    (doseq [[k v] *markers*]
      (.removeLayer osm-map v))
    (set! *markers* {})
    (let [location-marker (L.marker (clj->js *current-location*))]
      (set! *markers* (assoc *markers* "location" location-marker))
      (.openPopup (.bindPopup (.addTo location-marker osm-map) "Standort")))
    (doseq [n nodes]
      (if-let [latlon (node/latlon n)]
        (let [marker (L.marker (clj->js latlon))]
          (.bindPopup marker (str "<b>" (node/node-name n) "</b><br/>" (node/address n)))
          (set! *markers* (assoc *markers* (node/mac n) marker))
          (.addTo marker osm-map))))))
  
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
  
  (def $scope.extended false)
  
  (defn set-stats! 
    ([js-array-stats] (set-stats! js-array-stats false))
    ([js-array-stats apply?]
      (if apply?
        (.$apply $scope (aset $scope "stats" js-array-stats))
        (aset $scope "stats" js-array-stats))))

  (defn is-extended-mode? []
    (aget $scope "extended"))
  
  (defn mode-changed 
    ([] (mode-changed false))
    ([apply?]
      (let [nodes (make-nodes-with-distance 
                    (if (is-extended-mode?) 
                      *enriched-stats* 
                      (filter #(and (node/working? %) (node/latlon %)) *enriched-stats*)))
            sorted-nodes (sort-by (fn [e] 
                                    (node/distance-to e (first *current-location*) (second *current-location*)))
                                  < 
                                  nodes)]
        (set-stats! (clj->js sorted-nodes) apply?)
        (update-osm-map sorted-nodes))))
  
  (def $scope.modeChanged mode-changed)
      
  ; get nodes info
  (.send goog.net.XhrIo node-list-url
    (fn [result]
      (if-let [nodes (js->clj (.getResponseJson (.-target result)))]
        ; get node stats
        (.send goog.net.XhrIo node-stats-url
          (fn [result] 
            (if-let [stats (js->clj (.getResponseJson (.-target result)))]
              (do
                (log "stats loaded ... trying to get location ...")
                ; get geo-location (todo: take care of error cases i.e. refusal, not present)
                (.getCurrentPosition js/navigator.geolocation
                  (fn [position]
                    (log "... got location")
                    (set! *current-location* [(.-latitude js/position.coords)
                                              (.-longitude js/position.coords)])
                    (set! *enriched-stats* (enriched-stats stats (make-nodes-map nodes)))
                    (mode-changed true))))
              (log "Error: Could not load node stats."))))
        (log "Error: Could not load nodes info.")))))

(def SimpleStatsCtrl
  (array
   "$scope"
    CSimpleStatsCtrl))

;; ----

