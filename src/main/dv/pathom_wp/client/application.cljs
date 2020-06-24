(ns dv.pathom-wp.client.application
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [dv.fulcro-util :as fu]
    [dv.pathom-wp.client.wp-resolvers :as wp-resolvers]
    [dv.pathom-wp.client.prn-debug :refer [pprint-str]]
    [dv.pathom :refer [make-parser]]
    [reagent.core :as r]
    [reagent.dom :as rdom]
    [taoensso.timbre :as log]))

(def resolvers [wp-resolvers/resolvers])

(def parser (make-parser resolvers))

(def SPA
  (app/fulcro-app
    {:remotes           {:remote (fu/local-remote parser {})}
     :render-middleware (fn [this render] (r/as-element (render)))
     :render-root!      rdom/render}))

(comment
  (go
    (js/console.log
      (<! (parser {}
            [{(list 'dv.pathom-wp.client.ui.root/do-search {:query "banana"})
              [:search-term
               {:result-list [:wp/title :wp/preview]}]}]))))
  )
