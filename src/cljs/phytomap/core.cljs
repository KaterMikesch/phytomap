(ns phytomap.core
  (:require [phytomap.node :as node]
            [clojure.string :as s]
            [goog.net.XhrIo :as gxhrio]
            [goog.dom :as dom]
            [cljs.core.async :as async :refer [chan close!]])
  (:require-macros
    [cljs.core.async.macros :refer [go alt!]]))

(defn log [& more]
  (.log js/console (apply str more)))
  
(defn open-uri [uri]
  (let [window (dom/getWindow)
        location (.-location window)]
    (aset location "href" uri)))

(defn GET-json [url]
  "Channel based HTTP-GET. Returns the channel that eventually has the response's json as result."
  (let [ch (chan 1)]
    (gxhrio/send url
                 (fn [event]
                   (let [res (-> event .-target .getResponseJson)]
                     (go (>! ch res)
                         (close! ch)))))
    ch))

(defn get-current-location []
  (let [ch (chan 1)]
    (.getCurrentPosition js/navigator.geolocation
      (fn [position]
        (let [res [(.-latitude js/position.coords)
                   (.-longitude js/position.coords)]]
          (go (>! ch res)
              (close! ch)))))
    ch))

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

(defn make-nodes-with-distance [nodes loc]
  (map (fn [e] 
         (if (node/latlon e) 
           (assoc e "distance" (node/distance-to e (first loc) (second loc))) 
           e))
       nodes))

(defn send-rot23-email [realname rot23-email]
  (open-uri (str "mailto:" (s/replace realname "," "") "<" (js/trans rot23-email -23) ">")))

(defn open-ssh [hostname]
  (open-uri (str "ssh://root@" hostname ".local")))
            
(def *map* nil)
(def *markers* {})
(def *location-circle-layer* nil)

(defn show-node [node-mac]
  (let [marker (get *markers* node-mac)
        latlng (.getLatLng marker)]
    (log latlng)
    (.panTo *map* latlng)
    ;(log "show-node " node-mac marker)
    (.openPopup marker)))

(defn zoom-for-node [n current-loc osm-map]
  "Returns the zoom level that's necessary for displaying the given node, when the given current location is in the center of the map."
  (let [node-latlon (node/latlon n)
        lat-diff (- (first current-loc) (first node-latlon))
        lon-diff (- (second current-loc) (second node-latlon))
        north-west (js/L.LatLng. (- (first current-loc) lat-diff) (- (second current-loc) lon-diff))
        south-east (js/L.LatLng. (+ (first current-loc) lat-diff) (+ (second current-loc) lon-diff))
        bounds (js/L.LatLngBounds. north-west south-east)]
    (.getBoundsZoom osm-map bounds)))

(defn update-osm-map 
  ([nodes loc]
    (if (nil? *map*)
      (let [osm-map (js/L.map "map" (clj->js {"scrollWheelZoom" false}))
            cm-url "http://{s}.tile.cloudmade.com/BC9A493B41014CAABB98F0471D759707/997/256/{z}/{x}/{y}.png"
            tile-layer (js/L.tileLayer cm-url (clj->js {"maxZoom" 18 "detectRetina" false}))]
        (.addTo tile-layer osm-map)
        (set! *map* osm-map)
        (.setView *map* (clj->js loc) (zoom-for-node (first nodes) loc osm-map))))
    (update-osm-map nodes loc *map*))
  ([nodes loc osm-map] 
    (doseq [[k v] *markers*]
      (.removeLayer osm-map v))
    (if *location-circle-layer* 
      (.removeLayer osm-map *location-circle-layer*))
    (set! *markers* {})
    (doseq [n nodes]
      (if-let [latlon (node/latlon n)]
        (let [marker (L.marker (clj->js latlon))]
          (.bindPopup marker (str "<b>" (node/node-name n) "</b><br/>" (node/address n)))
          (set! *markers* (assoc *markers* (node/mac n) marker))
          (.addTo marker osm-map))))
    (let [location-marker (L.marker (clj->js loc))
          location-latlng (L.LatLng. (first loc) (second loc))
          location-circle-layer (L.CircleMarker. location-latlng (clj->js {"color" "#ff0000"}))]
      (.addLayer osm-map location-circle-layer)
      (set! *location-circle-layer* location-circle-layer))))

(def node-list-url "/nodes.json")
(def node-stats-url "/stats.json")

(defn CSimpleStatsCtrl [$scope]
  (def $scope.stats nil)
  
  (def $scope.sendEmail send-rot23-email)

  (def $scope.openSSH open-ssh)
  
  (def $scope.showNode show-node)
  
  (def $scope.extended false)
  
  (def $scope.current-location [50.9406645 6.9599115])

  (defn set-stats! 
    ([js-array-stats] (set-stats! js-array-stats false))
    ([js-array-stats apply?]
      (if apply?
        (.$apply $scope (aset $scope "stats" js-array-stats))
        (aset $scope "stats" js-array-stats))))

  (defn is-all-nodes-mode? []
    (aget $scope "allNodes"))
  
  (defn mode-changed 
    ([] (mode-changed false))
    ([apply?]
      (let [loc (aget $scope "current-location")
            nodes (make-nodes-with-distance 
                    (if (is-all-nodes-mode?) 
                      (aget $scope "enrichedStats") 
                      (filter #(and (node/working? %) (node/latlon %)) (aget $scope "enrichedStats"))) loc)
            sorted-nodes (sort-by (fn [e] 
                                    (node/distance-to e (first loc) (second loc)))
                                  < 
                                  nodes)]
        (set-stats! (clj->js sorted-nodes) apply?)
        (update-osm-map sorted-nodes loc))))
  
  (def $scope.modeChanged mode-changed)
      
  (go
    (let [ch-nodes (GET-json node-list-url)
          ch-stats (GET-json node-stats-url)
          ch-pos (get-current-location)
          nodes (js->clj (<! ch-nodes))
          stats (js->clj (<! ch-stats))
          pos (<! ch-pos)]
      (aset $scope "current-location" pos)
      (aset $scope "enrichedStats" (enriched-stats stats (make-nodes-map nodes)))
      (aset $scope "enrichedStatsCount" (count (aget $scope "enrichedStats")))
      (mode-changed true))))

(def SimpleStatsCtrl
  (array
   "$scope"
    CSimpleStatsCtrl))

;; ----

