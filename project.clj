(defproject com.mishadoff/littlepot "0.1.1"
  :description "littlepot: Autofilled Cache"
  :url "http://mishadoff.com"
  :license {:name "Eclipse Public License 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]]
  :main littlepot.core
  :profiles {:dev {:plugins [[jonase/eastwood "0.2.1"]
                             [lein-kibit "0.1.2"]
                             [lein-bikeshed "0.2.0"]
                             [lein-cloverage "1.0.6"]]}}
  
  :aliases {"build" ["do"
                     ["clean"]
                     ["test"]
                     ["kibit"]
                     ["bikeshed"]
                     ["eastwood"]]}
)
