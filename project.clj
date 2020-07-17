;; I really wanted it to be a Leiningen-less project and use something
;; like uberdeps for the uberjar, but apparently there’s no escape,
;; because of this:
;; https://www.arctype.co/blog/resolve-log4j2-conflicts-in-uberjars

(defproject soupscraper "0.1.0-SNAPSHOT"
  :main soupscraper.core
  :plugins [[lein-tools-deps "0.4.5"] ; at least let's not repeat deps
            [arctype/log4j2-plugins-cache "1.0.0"]] ; apply the workaround
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :manifest {"Multi-Release" "true"} ; otherwise it'll complain, see https://stackoverflow.com/questions/53049346/is-log4j2-compatible-with-java-11
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn
               leiningen.log4j2-plugins-cache/middleware])
