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

   (Uri. (wp-preview-uri "Apple-designed processors"))
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
  {::pc/output [:search]}
  (log/info "In get a thing")
  {:search "Did it work?"}
  )

(def resolvers [fetch-a-thing])
