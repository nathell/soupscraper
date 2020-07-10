(ns soupscraper.core
  (:require [skyscraper.core :as core :refer [defprocessor]]))

(require '[skyscraper.dev :refer :all])

(def seed [{:url "https://tomash.soup.io", :who "tomash", :since "latest", :processor :soup}])

(defn parse-post [div]
  (let [content (reaver/select div ".content")
        imagebox (reaver/select content "a.lightbox")
        imagedirect (reaver/select content ".imagecontainer > img")
        body (reaver/select content ".body")
        h3 (reaver/select content "content > h3")
        video (reaver/select content ".embed video")
        id (subs (reaver/attr div :id) 4)]
    (merge {:id id}
           (cond
             video {:type :video, :url (reaver/attr video :src)}
             imagebox {:type :image, :url (reaver/attr imagebox :href)}
             imagedirect {:type :image, :url (reaver/attr imagedirect :src)}
             body {:type :text}
             :otherwise nil)
           (when h3 {:title (reaver/text h3)})
           (when body {:content (.html body)}))))

(defprocessor :soup
  :cache-template "soup/:who/list/:since"
  :process-fn (fn [document context]
                (let [moar (-> (reaver/select document "#load_more a") (reaver/attr :href))]
                  (concat
                   (when moar
                     (let [since (second (re-find #"/since/(\d+)" moar))]
                       [{:processor :soup, :since since, :url moar}]))))))

(defn run! []
  (core/scrape! seed
                :parse-fn     core/parse-reaver
                :parallelism  1
                :html-cache   true
                :http-options {:connection-timeout 120000
                               :socket-timeout     120000}))

(taoensso.timbre/set-level! :info)
