(ns dv.pathom-wp.client.application
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.networking.mock-server-remote :refer [mock-http-server]]
    [dv.pathom-wp.client.pathom-parser :refer [parser]]
    [dv.pathom-wp.client.wp-resolvers :as wp-resolvers]
    [dv.pathom-wp.client.prn-debug :refer [pprint-str]]
    [sablono.core :refer [html]]
    [taoensso.timbre :as log]))


(def resolvers [wp-resolvers/resolvers])

(defn wp-remote
  ([resolvers env]
   (let [parser    (parser resolvers)
         transmit!
                   (fn [this send-node]
                     (log/info "Transmit this: " this)
                     (log/info "Transmit send-node: " send-node)
                     (:transmit! (mock-http-server {:parser (fn [req] (parser env req))})))]
     {:transmit! (fn [this send-node]
                   (transmit! this send-node))}))
  ([resolvers]
   (wp-remote resolvers {})))

(defonce SPA
  (app/fulcro-app
    {:remotes {:remote (wp-remote resolvers)}
     :render-middleware (fn [this render] (html (render)))}))
