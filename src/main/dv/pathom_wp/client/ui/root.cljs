(ns dv.pathom-wp.client.ui.root
  (:require
    [com.fulcrologic.fulcro.dom.events :as e]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as c :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom :refer [div]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as sm]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [sablono.core :refer [html]]
    [dv.pathom-wp.client.prn-debug :refer [pprint]]
    [dv.fulcro-util :as fu]
    [dv.cljs-emotion :refer [defstyled]]
    [dv.pathom-wp.client.ui.task-item :refer [ui-task-list TaskList TaskForm ui-task-form]]
    [dv.pathom-wp.client.application :refer [SPA]]
    [dv.pathom-wp.client.router :as r]
    [dv.pathom-wp.client.ui.task-page :refer [TaskPage]]
    [taoensso.timbre :as log]))

(comment
  (println "app state: ")
  (pprint (-> SPA app/current-state keys sort))
  )

(dr/defrouter TopRouter
  [this {:keys [current-state route-factory route-props]}]
  {:router-targets [TaskPage]})

(def ui-top-router (c/factory TopRouter))

(defsc ResultItem [this {:wp/keys [title preview]}]
  {:query [:wp/title :wp/preview]}
  [:.ui.card
   [:.content
    [:.ui.grid [:.twelve.wide.column [:.ui.header title]]]
    [:.meta
     [:.ui.tiny.relaxed.horizontal.list {:style {:margin-bottom 0}}
      [:.item
       [:.content
        [:div {:dangerouslySetInnerHTML {:__html preview}}] ]]] ]] ] )

(def ui-result-item (c/factory ResultItem {:keyfn :wp/title}))

(defstyled flex :div
  {:display "flex"
   :flex-wrap "wrap"
   :align-items "baseline"
   :justify-content "space-evenly"})

(defsc SearchResult [_ {:keys [search-term result-list]}]
  {:query [:search-term {:result-list (c/get-query ResultItem)}]
   :ident [:search-by/search-term :search-term]}
  [:div
   [:h2 "Search term: " search-term]
   [:h2 "Results: "]

   (flex
     (for [i result-list]
       (ui-result-item i)))])
(def ui-search-result (c/factory SearchResult))

(m/defmutation do-search
  [{:keys [query]}]
  (action [_]
    (log/info "In search-term"))
  (remote [{:keys [ast] :as env}]
    (log/info "Keys" (keys env))
    (log/info "ast in remote search: " ast)
    (-> env
      ;(m/with-target [:] [:search])
      (m/returning SearchResult))
    )
  (ok-action [{:keys [state transmitted-ast transacted-ast dispatch mutation-ast result] :as env}]
    (let [sym (:dispatch-key transmitted-ast)
          v   (-> result :body sym)
          {:keys [search-term]} v]
      (log/spy transmitted-ast)
      ;(log/spy transacted-ast)
      ;(log/spy dispatch)
      ;(log/spy mutation-ast)
      (log/spy result)
      (log/info "ok: " (keys env))
      ;(dorun (map (fn [] ())))

      (fu/->s!
        state
        (fu/conj-set :all-search-terms search-term)
        (assoc :current-search-result [:search-by/search-term search-term]))
      (log/info "Value: " v))))
(comment
  do-search)

(defsc Page [this {:root/keys [router] :keys [ui/search-term search current-search-result] :as props}]
  {:query         [{:root/router (c/get-query TopRouter)}
                   [:search '_]
                   {[:current-search-result '_] (c/get-query SearchResult)}
                   :ui/search-term
                   [::sm/asm-id ::TopRouter]]
   :ident         (fn [_] [:component/id ::page])
   :initial-state (fn [_] {:root/router (c/get-initial-state TopRouter {})})}
  [:.ui.container
   [:.ui.secondary.pointing.menu (mapv r/link [:root])]
   ;(fu/props-data-debug this true)
   [:button {:on-click #(df/load! this :search nil {:params {:query "apple"}})} "Search"]
   (log/info "search: " search)
   [:div

    [:.form {:class-name "" #_(when checked? "error") :as "div"
             :on-submit  #(c/transact! this [(do-search {:query search-term})])
             ;:on-change  (fn [e] true)
             }
     [:.field
      (fu/ui-textfield this "Search: " :ui/search-term props :tabIndex 1
        :autofocus? true
        :onKeyDown #(when (e/enter? %)
                      (c/transact! this [(do-search {:query search-term})])
                      (log/info "GOT CHANGE")))]]
    (ui-search-result current-search-result)]])

(def ui-page (c/factory Page))

(defsc Root [_ {:root/keys [page]}]
  {:query         [{:root/page (c/get-query Page)}]
   :initial-state (fn [_] {:root/page (c/get-initial-state Page {})})}
  ^:inline (ui-page page))
