(ns phytomap.core  
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.util.response :as response]))

;; defroutes macro defines a function that chains individual route
;; functions together. The request map is passed to each function in
;; turn, until a non-nil response is returned.
(defroutes app-routes
  ; to serve document root address
  (GET "/" [] "<p>Hello from compojure</p>")
  
  (GET "/nodes.json" [] (response/header (response/response (slurp "http://register.kbu.freifunk.net/nodes.json")) "Expires" "Thu, 01 Dec 2010 16:00:00 GMT"))
  
  (GET "/stats.json" [] (response/header (response/response (slurp "http://stat.kbu.freifunk.net/nodes.json")) "Expires" "Thu, 01 Dec 2010 16:00:00 GMT"))
  
  ; to serve static pages saved in resources/public directory
  (route/resources "/")
  
  ; if page is not found
  (route/not-found "Page not found"))

;; site function creates a handler suitable for a standard website,
;; adding a bunch of standard ring middleware to app-route:
(def handler
  (handler/site app-routes))