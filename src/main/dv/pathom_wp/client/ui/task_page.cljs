(ns dv.pathom-wp.client.ui.task-page
  (:require
    [com.fulcrologic.fulcro.components :as c :refer [defsc]]
    [dv.pathom-wp.client.ui.task-item :refer [ui-task-list TaskList TaskForm ui-task-form TaskItem]]
    [dv.pathom-wp.client.router :as r]))

(defsc TaskPage [this {:keys [task-list task-form]}]
  {:query         [{:task-list (c/get-query TaskList)}
                   {:task-form (c/get-query TaskForm)}]
   :route-segment (r/route-segment :tasks)
   :initial-state     (fn [_] {:task-form (c/get-initial-state TaskForm)
                               :task-list (c/get-initial-state TaskList)})

   :ident         (fn [_] [:component/id :root])}
  [:div
   [:h1 "Here's a search form"]
   (ui-task-form task-form)
   [:h1 "Here's a task list:"]
   (ui-task-list task-list)])
