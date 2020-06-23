(ns dv.pathom-wp.client.application
  (:require
    [cljs.core.async :refer [<! chan put! go go-loop]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.components :as c]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.networking.mock-server-remote :refer [mock-http-server]]
    [dv.pathom-wp.client.pathom-parser :refer [make-parser]]
    [dv.pathom-wp.client.wp-resolvers :as wp-resolvers]
    [dv.pathom-wp.client.prn-debug :refer [pprint-str]]
    [sablono.core :refer [html]]
    [taoensso.timbre :as log]))

(def resolvers [wp-resolvers/resolvers])

(def parser (make-parser resolvers))

(defn wp-remote
  [parser env]
  (let [transmit!
        (fn [this send-node]
          (log/info "Transmit this: " this)
          (log/info "Transmit send-node: " send-node)
          ((:transmit! (mock-http-server
                         {:parser
                          (fn [req]
                            (log/info "in TRANSMIT")
                            (go
                              ;; todo wrap in try catch ?
                              (let [out (<! (parser env req))]
                                (log/info "parser output: " out)
                                out)))}))
           this send-node))]
    {:transmit! (fn [this send-node]
                  (log/info "in outer transmit")
                  (transmit! this send-node))}))

(def SPA
  (app/fulcro-app
    {:remotes           {:remote (wp-remote parser {})}
     :render-middleware (fn [this render] (html (render)))}))

(comment
  (c/transact! SPA [(search-term {:query "fred"})])
  (df/load! SPA :search nil {:params {:query "apple"}})

  (go (js/console.log "out" (<! (parser {} ['(:search {:query "Apple"})]))))
  )
