(ns phytomap.node)

; geo stuff

(defn radians [deg]
  (* deg (/ js/Math.PI 180)))

(defn haversine-distance [lat1 lon1 lat2 lon2]
  (let [R 6372.8 ; kilometers
        dlat (radians (- lat2 lat1))
        dlon (radians (- lon2 lon1))
        lat1 (radians lat1)
        lat2 (radians lat2)
        a (+ (* (js/Math.sin (/ dlat 2)) (js/Math.sin (/ dlat 2))) 
             (* (js/Math.sin (/ dlon 2)) (js/Math.sin (/ dlon 2)) (js/Math.cos lat1) (js/Math.cos lat2)))]
    (* R 2 (js/Math.asin (js/Math.sqrt a)))))

; ---

(def dead-ping 
  "A high number indicating a non-answering/dead ping."
  100000)

(defn ping-stats [node]
  "Returns the ping stats of a node. If not given or nil/invalid, returns dead-ping (100000)."
  (if-let [rtt-5-min (get-in node ["stats" "rtt_5_min"])]
    rtt-5-min
    dead-ping))

(defn working? [node]
  "true if node has a ping that is not a dead-ping (100000). false otherwise."
  (< (ping-stats node) dead-ping))

(defn latlon [node]
  "Returns the node's latitude and longitude (as a vector) if present. nil otherwise."
  (if-let [lat (get-in node ["node_registration" "latitude"])]
    (if-let [lon (get-in node ["node_registration" "longitude"])]
      [lat lon])))

(defn distance-to [node lat lon]
  (if-let [latlon (latlon node)]
    (haversine-distance lat lon (first latlon) (second latlon))))