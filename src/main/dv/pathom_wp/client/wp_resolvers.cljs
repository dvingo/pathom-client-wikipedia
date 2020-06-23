(ns dv.pathom-wp.client.wp-resolvers
  (:require
    [cljs.core.async :refer [<! chan put! go go-loop pipeline take! onto-chan! close!]]
    [clojure.string :as str]
    [httpurr.client :as http]
    [httpurr.client.xhr :refer [client]]
    [com.wsscode.async.async-cljs :as wa :refer [<!p <?]]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [dv.pathom-wp.client.wikipedia :as wp]
    [taoensso.timbre :as log])
  (:import
    [goog.string Const]
    [goog.html TrustedResourceUrl]
    [goog Uri]
    [goog.net Jsonp XhrIo EventType]))

(defn log [& args]
  (.apply (.-log js/console) js/console (to-array args)))

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

#_(pc/defmutation search-term [{:keys [ast]} params]
  {::pc/sym 'dv.pathom-wp.client.ui.root/do-search}
  (let [query (-> ast :params :query)]
    (if-not query
      (throw (js/Error. "Missing query for search"))
      (do (log/info "In search : query: " query)
          (go
            {:search-term query
             :result-list
                          (->> (wp/wp-search query)
                            <!
                            ;; update to return
                            ;{:title :extract}

                            (mapv (fn [v] {:title v}))
                            )})))))

(pc/defmutation search-term [{:keys [ast]} params]
  {::pc/sym 'dv.pathom-wp.client.ui.root/do-search}
  (let [query (-> ast :params :query)]
    (if-not query
      (throw (js/Error. "Missing query for search"))
      (do (log/info "In search : query: " query)

          (let [in (chan) out (chan) answer (chan)]
            (go-loop [previews [] [title extract] (<! out)]
              (log/info "previews: " previews)
              (if (nil? extract)
                (>! answer previews)

                (let [v (<! extract)]
                  (do (log "got extract " v)
                      (recur (conj previews {:title title :extract v}) (<! out))))))

            (go
              (let [terms (<! (wp/wp-search query))]
                (log/info "Terms: " terms)
                (doseq [v terms]
                  (put! out [v (wp/wp-preview v)]))
                (close! out)

                (let [answer-val (<! answer)]
                  (log "answer: " answer-val)
                  (log "final resp: "
                    {:search-term query
                     :result-list answer-val})
                  {:search-term query
                   :result-list answer-val}
                  )))))

      )))


(comment
  ;; here is what I want to do

  ;; do search for term
  ; get list of items back
  ;; for each item do an http call in parallel await them all
  ;; close channel

  (let [in (chan) out (chan) answer (chan)]
    (go-loop [previews [] extract (<! out)]
      (log/info "previews: " previews)
      (if (nil? extract)
        (>! answer previews)
        (let [v (<! extract)]
          (do (log "got extract " v)
              (recur (conj previews v) (<! out))))))

    (go-loop [terms (<! (wp/wp-search "apple"))]
      (log/info "Terms: " terms)
      (doseq [v terms]
        (put! out (wp/wp-preview v)))
      (close! out))

    (go (log "answer: " (<! answer)))
    )

  (let [in (chan) out (chan)]
    (pipeline 10 out (map (fn [i]
                            (log/info "in pipeline: " i)
                            (wp/wp-preview i)))
      in)
    (go-loop [value (<! out)]
      (log "Got value: " (<! value)))
    (go
      (onto-chan! in (<! (wp/wp-search "apple")))
      )
    )

  )

(pc/defresolver fetch-a-thing [{:keys [ast]} _]
  {::pc/output [:search-term
                {:result-list [:title]}]}
  (let [query (-> ast :params :query)]
    (if-not query
      (throw (js/Error. "Missing query for search"))
      (do (log/info "In search : query: " query)
          (go
            (let [ret
                  {:search-term query
                   :result-list
                                (->> (wp/wp-search query)
                                  <!
                                  (mapv (fn [v] {:title v}))
                                  )}]
              (log/info "resolver returning: " ret)
              ret))))))

(def resolvers [fetch-a-thing search-term])
