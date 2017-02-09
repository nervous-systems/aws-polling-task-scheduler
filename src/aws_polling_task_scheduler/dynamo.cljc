(ns aws-polling-task-scheduler.dynamo
  (:require [hildebrand.core      :as dynamo]
            [hildebrand.channeled :as dynamo.chan]
            [eulalie.creds        :as creds]
            [#?(:clj  clojure.core.async
                :cljs cljs.core.async) :as async]))

(defn deleting-items
  [task-table]
  (dynamo.chan/batching-deletes (creds/env) task-table))

(defn delete! [task-table item]
  (dynamo/delete-item! (creds/env) task-table item))

(defn put!
  "Return a channel containing the inserted item."
  [task-table item]
  (dynamo/put-item!
   (creds/env) task-table item
   {:chan (async/chan 1 (map (constantly item)))}))

(defn by-id! [{:keys [task-table id-index id]}]
  (dynamo/query! (creds/env) task-table {:id [:= id]} {:index id-index}))

(defn by-timestamp! [task-table watershed]
  (dynamo.chan/query!
   (creds/env)
   task-table
   {:type      [:=  :task]
    :timestamp [:<= watershed]}))
