(ns dv.pathom-wp.client.ui.root
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as c :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom :refer [div]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as sm]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [sablono.core :refer [html]]
    [dv.fulcro-util :as fu]
    [dv.pathom-wp.client.ui.task-item :refer [ui-task-list TaskList TaskForm ui-task-form]]
    [dv.pathom-wp.client.application :refer [SPA]]
    [dv.pathom-wp.client.router :as r]
    [dv.pathom-wp.client.ui.task-page :refer [TaskPage]]))

(dr/defrouter TopRouter
  [this {:keys [current-state route-factory route-props]}]
  {:router-targets [TaskPage]})

(def ui-top-router (c/factory TopRouter))

(defsc Page [this {:root/keys [router] :keys [ui/search-term search] :as props}]
  {:query         [{:root/router (c/get-query TopRouter)}
                   [:search '_]
                   :ui/search-term
                   [::sm/asm-id ::TopRouter]]
   :initial-state (fn [_] {:root/router (c/get-initial-state TopRouter {})})}
  [:.ui.container
   [:.ui.secondary.pointing.menu (mapv r/link [:root])]
   ;(fu/props-data-debug this true)
   [:button {:on-click
             #(df/load! this :search nil {:params {:query "apple"}})} "Search"]
   [:div
    "search: "
    (for [s search]
      [:div {:key s} s])
    [:.form {:class-name "" #_(when checked? "error") :as "div"
             :on-change  (fn [e]
                           true)}
     [:.field nil
      (fu/ui-textfield this "Search: " :ui/search-term props :tabIndex 1
        :autofocus? true)]]]])

(def ui-page (c/factory Page))

(defsc Root [_ {:root/keys [page]}]
  {:query         [{:root/page (c/get-query Page)}]
   :initial-state (fn [_] {:root/page (c/get-initial-state Page{})})}
  ^:inline (ui-page page))
