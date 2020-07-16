(ns soupscraper.core
  (:require [cheshire.core :as json]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [java-time]
            [skyscraper.cache :as cache]
            [skyscraper.core :as core :refer [defprocessor]]
            [skyscraper.context :as context]
            [taoensso.timbre :as log :refer [warnf]]
            [taoensso.timbre.appenders.core :as appenders]))

;; logic c/o tomash, cf https://github.com/UlanaXY/BowlOfSoup/pull/1
(defn fullsize-asset-url [url]
  (when url
    (if-let [[_ a b c ext] (re-find #"^https://asset.soup.io/asset/(\d+)/([0-9a-f]+)_([0-9a-f]+)_[0-9]+\.(.*)$" url)]
      (format "http://asset.soup.io/asset/%s/%s_%s.%s" a b c ext)
      (string/replace url "https://" "http://"))))

(defn asset-info [type url]
  (let [url (fullsize-asset-url url)
        [_ prefix asset-id ext] (re-find #"^http://asset.soup.io/asset/(\d+)/([0-9a-f_]+)\.(.*)$" url)]
    {:type type
     :prefix prefix
     :asset-id asset-id
     :ext ext
     :url url
     :processor :asset}))

(let [formatter (-> (java-time/formatter "MMM dd yyyy HH:mm:ss z")
                    (.withLocale java.util.Locale/ENGLISH))]
  (defn parse-post-date [date]
    (try
      (java-time/java-date (java-time/instant formatter date))
      (catch Exception e
        (warnf "Could not parse date: %s" date)
        nil))))

(defn parse-user-container [span]
  (or (reaver/text (reaver/select span ".name"))
      (reaver/attr (reaver/select span "a img") :title)))

(defn parse-reaction [li]
  {:user (parse-user-container (reaver/select li ".toggle1 .user_container"))
   :link (reaver/attr (reaver/select li ".original_link") :href)
   :type (string/trim (reaver/text (reaver/select li ".toggle1 .info")))})

(defn parse-post [div]
  (if-let [content (reaver/select div ".content")]
    (let [imagebox (reaver/select content "a.lightbox")
          imagedirect (reaver/select content ".imagecontainer > img")
          body (reaver/select content ".body")
          h3 (reaver/select content "content > h3")
          video (reaver/select content ".embed video")
          id (subs (reaver/attr div :id) 4)
          time (reaver/select div ".time > abbr")
          reactions (reaver/select div ".reactions li")
          reposts (reaver/select div ".reposted_by .user_container")]
      (merge {:id id
              :date (when time (parse-post-date (reaver/attr time :title)))
              :reactions (mapv parse-reaction reactions)
              :reposts (mapv parse-user-container reposts)}
             (cond
               video (if-let [src-url (reaver/attr video :src)]
                       (asset-info :video src-url)
                       (do
                         (warnf "[parse-post] no video src: %s" video)
                         {:type :unable-to-parse, :post div}))
               imagebox (asset-info :image (reaver/attr imagebox :href))
               imagedirect (asset-info :image (reaver/attr imagedirect :src))
               body {:type :text}
               :otherwise nil)
             (when h3 {:title (reaver/text h3)})
             (when body {:content (.html body)})
             (when (reaver/select div ".ad-marker") {:sponsored true})))
    (do
      (warnf "[parse-post] no content: %s", div)
      {:type :unable-to-parse, :post div})))

(def months ["January" "February" "March" "April" "May" "June" "July" "August" "September" "October"
             "November" "December"])

(defn yyyymmdd [h2]
  (format "%s-%02d-%s"
          (reaver/text (reaver/select h2 ".y"))
          (inc (.indexOf months (reaver/text (reaver/select h2 ".m"))))
          (reaver/text (reaver/select h2 ".d"))))

(defn matches? [selector element]
  (and (instance? org.jsoup.nodes.Element element)
       (= (first (reaver/select element selector)) element)))


(defn date->yyyy-mm-dd [date]
  (some-> date java-time/instant str (subs 0 10)))

(defn propagate
  "Given a key `k` and a seq of maps `s`, goes through `s` and if an
  element doesn't contain `k`, assocs it to the last-seen value.

  (propagate :b [{:a 1 :b 2} {:a 2} {:a 3} {:a 4 :b 5}])
  ;=> [{:a 1 :b 2} {:a 2 :b 2} {:a 3 :b 2} {:a 4 :b 5}]"
  [k [fst & rst :as s]]
  (loop [acc [fst]
         to-go rst
         seen? (contains? fst k)
         last-val (get fst k)]
    (if-not (seq to-go)
      acc
      (let [[fst & rst] to-go
            has? (contains? fst k)]
        (recur (conj acc (if (or has? (not seen?))
                           fst
                           (assoc fst k last-val)))
               rst
               (or seen? (contains? fst k))
               (if has? (get fst k) last-val))))))

(def as-far-as (atom nil))
(def total-assets (atom 0))

(defn matches? [selector element]
  (and (instance? org.jsoup.nodes.Element element)
       (= (first (reaver/select element selector)) element)))

(defn parse-post-or-date [node]
  (condp matches? node
    "h2.date" {:date-from-header (yyyymmdd node)}
    "div.post" (parse-post node)
    nil))

(defn fixup-date [post]
  (if (:date post)
    post
    (assoc post :date (clojure.instant/read-instant-date (:date-from-header post)))))

(defn parse-posts-and-dates [document]
  (when-let [nodes (some-> (reaver/select document "#first_batch") first .childNodes)]
    (->> nodes
         (map parse-post-or-date)
         (remove nil?)
         (propagate :date-from-header)
         (filter :type)
         (map fixup-date)
         (map-indexed #(assoc %2 :num-on-page (- %1))))))

(defprocessor :soup
  :cache-template "soup/:soup/list/:since"
  :process-fn (fn [document {:keys [earliest pages-only] :as context}]
                (let [posts (parse-posts-and-dates document)
                      earliest-on-page (->> posts (map :date) sort first date->yyyy-mm-dd)
                      moar (-> (reaver/select document "#load_more a") (reaver/attr :href))]
                  (when pages-only
                    (swap! total-assets + (count (filter :url posts))))
                  (swap! as-far-as #(or earliest-on-page %))
                  (concat
                   (when (and moar (or (not earliest) (>= (compare earliest-on-page earliest) 0)))
                     (let [since (second (re-find #"/since/(\d+)" moar))]
                       [{:processor :soup, :since since, :url moar}]))
                   (when-not pages-only posts)))))

(defprocessor :asset
  :cache-template "soup/:soup/assets/:prefix/:asset-id"
  :parse-fn (fn [headers body] body)
  :process-fn (fn [document context]
                {:downloaded true}))

(defn download-error-handler
  [error options context]
  (let [{:keys [status]} (ex-data error)
        retry? (or (nil? status) (>= status 500) (= status 429))]
    (cond
      (= status 404)
      (do
        (warnf "[download] %s 404'd, dumping in empty file" (:url context))
        (core/respond-with {:headers {"content-type" "text/plain"}
                            :body (byte-array 0)}
                           options context))

      retry?
      (do
        (if (= status 429)
          (do
            (warnf "[download] Unexpected error %s, retrying after a nap" error)
            (Thread/sleep 5000))
          (warnf "[download] Unexpected error %s, retrying" error))
        [context])

      :otherwise
      (do
        (warnf "[download] Unexpected error %s, giving up" error)
        (core/signal-error error context)))))

(defn seed [{:keys [soup earliest pages-only]}]
  [{:url (format "https://%s.soup.io" soup),
    :soup soup,
    :since "latest",
    :processor :soup,
    :earliest earliest,
    :pages-only pages-only}])

(defn scrape-args [opts]
  [(seed opts)
   :parse-fn               core/parse-reaver
   :parallelism            1
   ;; :max-connections        1
   :html-cache             true
   :download-error-handler download-error-handler
   :sleep                  (:sleep opts)
   :http-options           {:redirect-strategy  :lax
                            :as                 :byte-array
                            :connection-timeout (:connection-timeout opts)
                            :socket-timeout     (:socket-timeout opts)}
   :item-chan              (:item-chan opts)])

(defn scrape [opts]
  (apply core/scrape (scrape-args opts)))

(defn scrape! [opts]
  (apply core/scrape! (scrape-args opts)))

(def cli-options
  [["-e" "--earliest DATE" "Skip posts older than DATE, in YYYY-MM-DD format"]
   ["-o" "--output-dir DIRECTORY" "Save soup data in DIRECTORY" :default "soup"]
   [nil "--connection-timeout MS" "Connection timeout in milliseconds"
    :default 60000
    :parse-fn #(Long/parseLong %)
    :validate [pos? "Must be a positive number"]]
   [nil "--socket-timeout MS" "Socket timeout in milliseconds"
    :default 60000
    :parse-fn #(Long/parseLong %)
    :validate [pos? "Must be a positive number"]]])

(log/set-level! :info)
(log/merge-config! {:appenders {:println {:enabled? false}
                                :spit (appenders/spit-appender {:fname "log/skyscraper.log"})}})

;; some touching of Skyscraper internals here; you're not supposed to understand this
(defn pages-reporter [item-chan]
  (async/thread
    (loop [i 1]
      (when-let [items (async/<!! item-chan)]
        (let [item (first items)]
          (if (and item (= (::core/stage item) `core/split-handler))
            (do
              (printf "\r%d pages fetched, going back as far as %s" i @as-far-as)
              (flush)
              (recur (inc i)))
            (recur i)))))))

(defn assets-reporter [item-chan]
  (async/thread
    (loop [i 1]
      (when-let [items (async/<!! item-chan)]
        (let [item (first items)]
          (if (and item
                   (= (::core/stage item) `core/split-handler)
                   (= (:processor item) :asset))
            (do
              (printf "\r%d/%d assets fetched" i @total-assets)
              (flush)
              (recur (inc i)))
            (recur i)))))))

(defn print-usage [summary]
  (printf "Save your Soup from eternal damnation.

Usage: clojure -A:run [options] soup-name-or-url
Options:
%s" summary)
  (println))

(defn soup-data [orig-posts]
  (let [posts (->> orig-posts
                   (sort-by (juxt :date :since :num-on-page) (comp - compare))
                   (map #(select-keys % [:asset-id :content :date :ext :id :prefix
                                         :reactions :reposts :sponsored :type]))
                   distinct)]
    {:soup-name (-> orig-posts first :soup)
     :posts posts}))

(defn generate-local-copy [{:keys [output-dir] :as opts} posts]
  (let [json-file (str output-dir "/soup.json")
        js-file (str output-dir "/soup.js")
        index-file (str output-dir "/index.html")
        cache (cache/fs core/html-cache-dir)]
    (io/make-parents (io/file json-file))
    (println "Saving assets (this may take a while)...")
    (doseq [{:keys [soup asset-id prefix ext]} posts
            :when asset-id
            :let [in-key (format "soup/%s/assets/%s/%s" soup prefix asset-id)
                  out-file (format "%s/assets/%s/%s.%s" output-dir prefix asset-id ext)
                  {:keys [blob]} (cache/load-blob cache in-key)]]
      (io/make-parents out-file)
      (with-open [in (io/input-stream blob)]
        (with-open [out (io/output-stream out-file)]
          (io/copy in out))))
    (println "Generating viewable soup...")
    (let [json (json/generate-string (soup-data posts))]
      (spit json-file json)
      (spit js-file (str "window.soup=" json))
      (with-open [in (io/input-stream (io/resource "assets/index.html"))]
        (with-open [out (io/output-stream index-file)]
          (io/copy in out))))))

(defn download-soup [opts]
  (println "Downloading infiniscroll pages...")
  (let [item-chan (async/chan)]
    (pages-reporter item-chan)
    (scrape! (assoc opts :sleep 1000 :item-chan item-chan :pages-only true)))
  (println "\nDownloading assets...")
  (let [item-chan (async/chan)]
    (assets-reporter item-chan)
    (scrape! (assoc opts :item-chan item-chan)))
  (println "\nGenerating local copy...")
  (generate-local-copy opts (scrape opts)))

(defn sanitize-soup [soup-name-or-url]
  (when soup-name-or-url
    (or (last (re-find #"^(https?://)?([^.]+)\.soup\.io" soup-name-or-url))
        soup-name-or-url)))

(defn validate-args [args]
  (let [{:keys [options arguments errors summary] :as res} (cli/parse-opts args cli-options)
        soup (sanitize-soup (first arguments))]
    (cond
      errors (println (string/join "\n" errors))
      soup   (assoc options :soup soup)
      :else  (print-usage summary))))

(defn -main [& args]
  (when-let [opts (validate-args args)]
    (download-soup opts))
  (System/exit 0))
