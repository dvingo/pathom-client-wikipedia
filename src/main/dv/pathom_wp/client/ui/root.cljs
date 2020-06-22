(ns dv.pathom-wp.client.ui.root
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as c :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom :refer [div]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as sm]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [dv.fulcro-util :as fu]
    [dv.pathom-wp.client.ui.task-item :refer [ui-task-list TaskList TaskForm ui-task-form]]
    [dv.pathom-wp.client.application :refer [SPA]]
    [dv.pathom-wp.client.router :as r]
    [dv.pathom-wp.client.ui.task-page :refer [TaskPage]]
))

(dr/defrouter TopRouter
  [this {:keys [current-state route-factory route-props]}]
  {:router-targets [TaskPage]})

(def ui-top-router (c/factory TopRouter))

(defsc Root [this {:root/keys [router] :keys [search] :as props}]
  {:query         [{:root/router (c/get-query TopRouter)}
                   [:search '_]
                   [::sm/asm-id ::TopRouter]]
   :initial-state (fn [_] {:root/router (c/get-initial-state TopRouter {})})}
  [:.ui.container
   [:.ui.secondary.pointing.menu (mapv r/link [:root])]
   (fu/props-data-debug this true)
   [:button {:on-click
             #(df/load! this :search nil {:params {:query "apple"}})} "Search"]
   [:div
    "search: "
    (for [s search]
      [:div {:key s} s])]])

