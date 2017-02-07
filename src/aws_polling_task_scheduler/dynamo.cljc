(ns aws-polling-task-scheduler.dynamo
  (:require [hildebrand.core      :as dynamo]
            [hildebrand.channeled :as dynamo.chan]
            [eulalie.creds        :as creds]
            [#?(:clj  clojure.core.async
                :cljs cljs.core.async) :as async]))

(defn putting-items
  "Return a channel which'll insert items placed on it"
  [task-table]
  (dynamo.chan/batching-puts (creds/env) task-table))

(defn put!
  "Return a channel containing the inserted item."
  [task-table item]
  (dynamo/put-item!
   (creds/env) task-table item
   {:chan (async/chan 1 (map (constantly item)))}))

(defn by-timestamp! [task-table expiry]
  (dynamo.chan/scan! (creds/env) task-table {:filter [:< [:now] expiry]}))
