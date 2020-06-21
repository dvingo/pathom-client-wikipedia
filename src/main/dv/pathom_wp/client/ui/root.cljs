(ns dv.pathom-wp.client.ui.root
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as c :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom :refer [div]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as sm]
    [dv.pathom-wp.client.ui.task-item :refer [ui-task-list TaskList TaskForm ui-task-form]]
    [dv.pathom-wp.client.application :refer [SPA]]
    [dv.pathom-wp.client.router :as r]
    [dv.pathom-wp.client.ui.task-page :refer [TaskPage]]
))

(dr/defrouter TopRouter
  [this {:keys [current-state route-factory route-props]}]
  {:router-targets [TaskPage]})

(def ui-top-router (c/factory TopRouter))
  (defsc PageContainer [this {:root/keys [router] :as props}]
    {:query         [{:root/router (c/get-query TopRouter)}
                     [::sm/asm-id ::TopRouter]]
     :ident         (fn [] [:component/id :page-container])
     :initial-state (fn [_] {:root/router (c/get-initial-state TopRouter {})})}
    (let [current-tab (r/current-route this)]
      [:.ui.container
       [:.ui.secondary.pointing.menu
         (mapv r/link [:root])]
       ^:inline (ui-top-router router)]))

(def ui-page-container (c/factory PageContainer))

(defsc Root [_ {:root/keys [page-container]}]
  {:query         [{:root/page-container (c/get-query PageContainer)}]
   :initial-state (fn [_] {:root/page-container (c/get-initial-state PageContainer {})})}
  ^:inline (ui-page-container page-container))
