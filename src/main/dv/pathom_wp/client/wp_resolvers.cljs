(ns dv.pathom-wp.client.wp-resolvers
  (:require
    [cljs.core.async :refer [<! chan put! go go-loop pipeline take! onto-chan! close!]]
    [clojure.string :as str]
    [httpurr.client.xhr :refer [client]]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [dv.pathom-wp.client.wikipedia :as wp]
    [taoensso.timbre :as log]))

(defn log [& args]
  (.apply (.-log js/console) js/console (to-array args)))

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

(pc/defresolver get-preview [{:keys [ast]} {:keys [wp/title]}]
  {::pc/input  #{:wp/title}
   ::pc/output [:wp/preview]}
  (log/info "fetching preview for: " title)
  (go
    {:wp/preview (<! (wp/wp-preview title))}))

(pc/defmutation search-term [{:keys [ast]} params]
  {::pc/sym 'dv.pathom-wp.client.ui.root/do-search
   }
  (let [query (-> ast :params :query)]
    (if-not query
      (throw (js/Error. "Missing query for search"))
      (do (log/info "In search : query: " query)
          (go
            (let [terms (<! (wp/wp-search query))]
              (log/info "Terms: " terms)
              (let [resp       {:search-term query
                                :result-list (mapv (fn [v] {:wp/title v}) terms)}]
                (log "final resp: " resp)
                resp)))))))

(def resolvers [search-term get-preview])
