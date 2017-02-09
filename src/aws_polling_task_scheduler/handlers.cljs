(ns "`schedule` inserts a Dynamo item with a `next` key holding the next
  timestamp at which the task ought to be run, returning a `token` (for
  cancellation) stored on the item.

`poll-scheduled` runs periodically, querying for items scheduled prior to now +
`RATE`, executes them, and inserts an item with same `token` but holding the
updated `next` value."
  aws-polling-task-scheduler.handlers
  (:require [cljs-lambda.macros  :as m]
            [cljs-lambda.context :as ctx]
            [aws-polling-task-scheduler.time   :as time]
            [aws-polling-task-scheduler.dynamo :as dynamo]
            [aws-polling-task-scheduler.core   :as core]
            [taoensso.timbre :as log]
            [glossop.core    :as g]
            [#?(:clj  clojure.core.async
                :cljs cljs.core.async) :as async]))

(def ^:private task-parallelism 5)

(defn- process-one! [task out-ch]
  (g/go-catching
    (log/info "Processing task" task)
    (g/<? (core/execute! (task :params)))
    (async/>! out-ch task)
    (async/close! out-ch)))

(m/deflambda poll-scheduled [_ ctx]
  (let [table  (ctx/env ctx :TASK_TABLE)
        done   (dynamo/deleting-items table)]
    (async/pipeline-async
     task-parallelism
     (done :in-chan)
     process-one!
     (dynamo/by-timestamp! table (js/Date.now)))))

(defn- from-json [x]
  (-> x js/JSON.parse (js->clj :keywordize-keys true)))

(defn- req->task [req]
  {:pre [(string? (:body req)) (map? (:params req))]
   :post [(every? % [:method :timestamp :id])]}
  (let [task (-> req
                :body
                from-json
                (select-keys [:method :params :id :timestamp])
                (assoc :type :task))]
    (cond-> task
      (not (task :id)) (assoc :id (util/token)))))

(defn- response [body & [ks]]
  (merge
   {:status  200
    :headers {:content-type "application/json"}
    :body    (js/JSON.stringify body)}
   ks))

(m/defgateway schedule
  "Turn an API Gateway request into a DynamoDB item in the scheduling table.

Response JSON contains an item w/ an `:id` key."
  [req ctx]
  (let [task  (req->task req)
        table (ctx/env ctx :TASK_TABLE)]
    (log/debug "Creating task" task "in" table)
    (g/go-catching
      (g/<? (dynamo/put! table task))
      (response task))))

(m/defgateway delete
  [req ctx]
  (let [table (ctx/env ctx :TASK_TABLE)
        index (ctx/env ctx :ID_INDEX)]
    (if-let [id (-> req :path-parameters :id)]
      (g/go-catching
        (if-let [item (g/<? (dynamo/by-id!
                             {:task-table table
                              :id-index   index
                              :id         (-> req :path-parameters :id)}))]
          (do
            (g/<? (dynamo/delete! task-table item))
            (response nil {:status 200}))
          (response nil {:status 404})))
      (response
       {:type    :validation
        :message "No id"
        :request req}
       {:status 400}))))
