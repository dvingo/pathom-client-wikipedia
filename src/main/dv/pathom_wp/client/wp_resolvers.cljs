(ns dv.pathom-wp.client.wp-resolvers
  (:require
    [cljs.core.async :refer [<! chan put! go go-loop]]
    [clojure.string :as str]
    [httpurr.client :as http]
    [httpurr.client.xhr :refer [client]]
    [com.wsscode.async.async-cljs :as wa :refer [<!p <?]]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [taoensso.timbre :as log])
  (:import
    [goog.string Const]
    [goog.html TrustedResourceUrl]
    [goog Uri]
    [goog.net Jsonp XhrIo EventType]))

(defn decode
  [response]
  (update response :body #(js->clj (js/JSON.parse %) :keywordize-keys true)))

(defn log [& args]
  (.apply (.-log js/console) js/console (to-array args)))

(defn wp-search
  "
  Notes on response shapes
  https://www.mediawiki.org/wiki/API:Data_formats

  https://en.wikipedia.org/wiki/Special:ApiSandbox#action=opensearch&search=Te
  "
  [query]
  (go
    (->
      (http/send! client
            {:method :get
             :url    "https://en.wikipedia.org/w/api.php"
             :query-params
                     {:action "opensearch" :origin "*" :format "json" :search query}})
      <!p decode :body second)))

(comment
  (go
    (let [r (<! (wp-search "Apple"))]
      (log r)
      )))

(defn wp-preview
  "
  Return a channel.

https://www.mediawiki.org/wiki/API:Query
list=random for a random list of pages

 https://en.wikipedia.org/w/api.php?action=help&modules=query
 https://en.wikipedia.org/w/api.php?action=help&modules=query%2Bextracts

 https://www.mediawiki.org/wiki/API:Tutorial#How_to_use_it
  "
  [title]
  (go
    (->
      (http/send! client
        {:method :get
         :url    "https://en.wikipedia.org/w/api.php"
         :query-params
                 {:action  "query"
                  :origin  "*"
                  ;; can also do links, info, categories, templates,
                  :prop    "extracts"
                  :exchars 1000
                  :exlimit 1
                  :format  "json"
                  :titles title}})
      <!p decode
      :body :query :pages
      vals first :extract)))

(comment
  (go (log (<! (wp-preview "Apple"))))

  (go
    (let [r (<! (wp-search "Apple"))
          _ (log "preview for: " (second r))
          r2 (<! (wp-preview (second r)))]
      (log "r2: " r2)
      ))
  )

(def base-uri "https://en.wikipedia.org/w/api.php?")

(defn search-uri
  "Query wp"
  [query]
  (str base-uri
    (str/join "&" ["action=opensearch" "origin=*" "format=json" (str "search=" query)])))

(defn wp-preview-uri
  [page-title]
  (str base-uri
    (str/join "&"
      ["action=query" "prop=extracts" "exchars=1000" "format=json"
       (str "titles=" page-title)])))

;;;
;;; general notes:
;;; user enters search term, we hit search-uri
;;; you get back page titles that you can pass to the
;;; "extracts" action which returns an html preview of the page
;;; render that into a grid or list
;;;;

(comment
  "
  For example
  this is a preview query:
  https://en.wikipedia.org/w/api.php?action=query&prop=extracts&exchars=1000&format=json&titles=apple-designed%20processors
  ")
;;;

(comment
  (let [x (XhrIo.)]
    (.listen x EventType/COMPLETE
      (fn [a]
        (log/info "output" (-> (.getResponseJson x)
                             js->clj
                             (get 1)
                             ))
        (log/info "json:" (js->clj (.getResponseJson x)))))
    (.send x (search-uri "apple")))
  )


(comment
  (go
    (log "response: "
      (<!p
        (http/send! client
          {:method :get
           :url    (search-uri "apple")}))))
  )

(comment
  ;; execute a search

  (let [query "apples"]
    (go
      (let [resp
                 (<!p (http/send! client
                        {:method :get
                         :url    "https://en.wikipedia.org/w/api.php"
                         :query-params
                                 {:action "opensearch" :origin "*" :format "json" :search query}}))

            resp (decode resp)
            out  (-> resp :body (get 1))]
        (def resp2 resp)
        (log "resp: " resp)
        (log "out : " out)
        )))
  (-> resp2 :body (get 1))
  )

(comment
  ;; execute a preview ("extract")
  (go
    (->
      (http/send! client
        {:method :get
         :url    "https://en.wikipedia.org/w/api.php"
         :query-params
                 {:action  "query"
                  :origin  "*"
                  :prop    "extracts"
                  :exchars 1000
                  :exlimit 1
                  :format  "json" :titles "Appleseed Ex Machina"}})
      <!p
      decode
      :body
      :query
      :pages
      vals
      first
      (select-keys [:title :extract])
      log
      )))
(comment
  (Uri. (wp-preview-uri "Apple-designed processors")))
(comment
  (wp-preview-uri "test")
  (TrustedResourceUrl/fromConstant
    (.from Const
      (wp-preview-uri "test"))))

(defn mk-uri
  [uri]
  (TrustedResourceUrl/format (.from Const uri)))

(comment
  (mk-uri)
  (.parse Uri "hi")

  (.send (Jsonp. (mk-uri (search-uri "apple")))
    (fn [answer]
      (log/info "response: " answer)))
  )

(defn jsonp
  ([uri] (jsonp (chan) uri))
  ([c uri]
   (let [gjsonp (Jsonp. (mk-uri uri))]
     (.send gjsonp nil #(put! c %))
     c))
  ([c uri & args]
   (let [gjsonp (Jsonp. (mk-uri uri))]
     (.send gjsonp nil #(put! c (conj args %)))
     c)))

(defn search-loop [c get-page-chan]
  (go-loop [query (<! c)]
    (log/info "QUERY: " query)
    (let [[_ results] (<! (jsonp (search-uri query)))]
      (doseq [title results]
        (jsonp get-page-chan (wp-preview-uri title) title))
      results)
    (recur (<! c))))

(defn get-page-loop [c]
  (go-loop [[page title cb] (<! c)]
    (let [id      (-> (aget page "query" "pages")
                    js-keys first)
          preview (aget page "query" "pages" id "extract")
          preview (.substring preview 0 (- (.-length preview) 3))]
      (cb {:search/pages-by-name {title {:preview preview :name title}}})
      (recur (<! c)))))

(def send-chan (chan))
(def get-page-chan (chan))

(search-loop send-chan get-page-chan)
(get-page-loop get-page-chan)


(comment
  (go (<! (jsonp (mk-uri (search-uri "apple")))))
  )

(pc/defresolver fetch-a-thing [{:keys [ast]} _]
  {::pc/output [:search]}
  (let [query (-> ast :params :query)]
    (if-not query
      (throw (js/Error. "Missing query for search"))
      (do (log/info "In search : query: " query)
          (go
            {:search (<! (wp-search query))})))))

(def resolvers [fetch-a-thing])
