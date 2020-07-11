(ns soupscraper.core
  (:require [skyscraper.core :as core :refer [defprocessor]]
            [skyscraper.context :as context]
            [taoensso.timbre :refer [warnf]]))

(defn parse-post [div]
  (let [content (reaver/select div ".content")]
    (if content
      (let [imagebox (reaver/select content "a.lightbox")
            imagedirect (reaver/select content ".imagecontainer > img")
            body (reaver/select content ".body")
            h3 (reaver/select content "content > h3")
            video (reaver/select content ".embed video")
            id (subs (reaver/attr div :id) 4)]
        (merge {:id id}
               (cond
                 video {:type :video, :xurl (reaver/attr video :src)}
                 imagebox {:type :image, :xurl (reaver/attr imagebox :href)}
                 imagedirect {:type :image, :xurl (reaver/attr imagedirect :src)}
                 body {:type :text}
                 :otherwise nil)
               (when h3 {:title (reaver/text h3)})
               (when body {:content (.html body)})))
      (do
        (warnf "[parse-post] no content: %s", div)
        nil))))

(def months ["January" "February" "March" "April" "May" "June" "July" "August" "September" "October"
             "November" "December"])

(defn yyyymmdd [h2]
  (format "%s-%02d-%s"
          (reaver/text (reaver/select h2 ".y"))
          (inc (.indexOf months (reaver/text (reaver/select h2 ".m"))))
          (reaver/text (reaver/select h2 ".d"))))

(defprocessor :soup
  :cache-template "soup/:who/list/:since"
  :process-fn (fn [document {:keys [earliest] :as context}]
                (let [dates (map yyyymmdd (reaver/select document "h2.date"))
                      moar (-> (reaver/select document "#load_more a") (reaver/attr :href))]
                  (concat
                   (when (and moar (or (not earliest) (>= (compare (last dates) earliest) 0)))
                     (let [since (second (re-find #"/since/(\d+)" moar))]
                       [{:processor :soup, :since since, :url moar}]))
                   (map parse-post (reaver/select document ".post"))))))

(defn download-error-handler
  [error options context]
  (let [{:keys [status]} (ex-data error)
        retry? (or (nil? status) (>= status 500))]
    (if retry?
      (do
        (warnf "[download] Unexpected error %s, retrying" error)
        [context])
      (do
        (warnf "[download] Unexpected error %s, giving up" error)
        (core/signal-error error context)))))

(defn seed [{:keys [earliest]}]
  [{:url "https://tomash.soup.io", :who "tomash", :since "latest", :processor :soup, :earliest earliest}])

(defn scrape-args [opts]
  [(seed opts)
   :parse-fn     core/parse-reaver
   :parallelism  1
   :html-cache   true
   :download-error-handler download-error-handler
   :sleep        1000
   :http-options {:redirect-strategy  :lax
                  :as                 :byte-array
                  :connection-timeout 10000
                  :socket-timeout     10000}])

(defn run [opts]
  (apply core/scrape (scrape-args opts)))

(defn run! [opts]
  (apply core/scrape! (scrape-args opts)))

(def cli-options
  [["-e" "--earliest" "Skip posts older than YYYY-MM-DD"]])

(taoensso.timbre/set-level! :info)
