(ns dv.pathom-wp.client.wikipedia
  (:require
    [cljs.core.async :refer [<! chan put! go go-loop]]
    [clojure.string :as str]
    [httpurr.client :as http]
    [httpurr.client.xhr :refer [client]]
    [com.wsscode.async.async-cljs :as wa :refer [<!p <?]]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [taoensso.timbre :as log]))


(comment
  (wa/go-promise
    (-> (js/fetch "/") <!p
      .text <!p
      log)) )

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
  Returns a channel.

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
                  :titles  title}})
      <!p decode
      :body :query :pages
      vals first :extract)))

(comment
  (go (log (<! (wp-preview "Apple"))))

  (go
    (let [r  (<! (wp-search "Apple"))
          _  (log "preview for: " (second r))
          r2 (<! (wp-preview (second r)))]
      (log "r2: " r2)
      ))
  )

(comment
  "
  For example
  this is a preview query:
  https://en.wikipedia.org/w/api.php?action=query&prop=extracts&exchars=1000&format=json&titles=apple-designed%20processors
  ")

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
