(ns phytomap.test)

(defn log [& more]
    (.log js/console (apply str more)))

(log "toll " 34 " Dirky" (format "%s" "bla"))