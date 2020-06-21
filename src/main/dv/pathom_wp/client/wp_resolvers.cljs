(ns dv.pathom-wp.client.wp-resolvers
  (:require
    [clojure.string :as str]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [cljs.core.async :refer [<! chan put! go go-loop]]
    [taoensso.timbre :as log])
  (:import
    [goog.string Const]
    [goog.html TrustedResourceUrl]
    [goog Uri]
    [goog.net Jsonp XhrIo EventType]))

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

(comment
  (let [x (XhrIo.)]
    (.listen x EventType/COMPLETE
      (fn [a]
        (log/info "answer: " a)
        (log/info "body:" (.getResponseBody x))
        (log/info "json:" (.getResponseJson x))
        (log/info "text:" (.getResponseText x))
        (log/info "type:" (.getResponseType x))
        ))
    (.send x (search-uri "apple")))
  (.send XhrIo
    (search-uri "apple")
    (fn [r] (log/info "got r: " r)
      (log/info "status " (.getStatus r))
      (log/info "body " (.getResponseBody r))
      )
    )
  )

(comment
  (wp-preview-uri "test")
  (TrustedResourceUrl/fromConstant
    (.from Const
      (wp-preview-uri "test")) ) )

(defn mk-uri
  [uri]
  (TrustedResourceUrl/format (.from Const uri)))

(comment
  (mk-uri )
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
                    js/Object.keys first)
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


  (search-uri "apple")
  (put! send-chan "apple")
  (js/console.log "HI")
  (jsonp (chan) (search-uri "apple"))
  (Jsonp. (Uri. (search-uri "apple")))
  (.send (Jsonp. (Uri. (search-uri "apple")))
    nil (fn [answer]
          (log/info "answer: " answer))
    )

  (.send (Jsonp.
           (TrustedResourceUrl/format
             (.from Const "https://www.google.com/search?q=%{query}")
             #js{:query "apple"}) )
    nil (fn [answer]
          (log/info "answer: " answer))
    )
  (TrustedResourceUrl/format
    (.from Const "https://www.google.com/search?q=%{query}")
    #js{:query "apple"})

  (js-keys (Uri. (search-uri "apple")))
  (TrustedResourceUrl/fromConstant "HI")
  )

(pc/defresolver fetch-a-thing [env params]
  {::pc/output [:a-thing]}
  (log/info "In get a thing")
  )

(def resolvers [fetch-a-thing])
