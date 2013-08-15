(defproject phytomap "0.1.0-SNAPSHOT"
  :description "freifunk node usage visualization"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  ;; clojure source code pathname
  :source-paths ["src/clj"]

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]]

  :plugins [;; cljsbuild plugin
            [lein-cljsbuild "0.3.2"]

            ;; ring plugin
            [lein-ring "0.8.5"]]

  ;; ring tasks configuration
  :ring {:handler phytomap.core/handler}

  ;; cljsbuild tasks configuration
  :cljsbuild {:builds
              [{;; clojurescript source code path
                :source-paths ["src/cljs"]

                ;; Google Closure Compiler options
                :compiler {;; the name of the emitted JS file
                           :output-to "resources/public/js/phytomap.js"

                           ;; use minimal optimization CLS directive
                           :optimizations :whitespace

                           ;; prettyfying emitted JS
                           :pretty-print true}}]})
