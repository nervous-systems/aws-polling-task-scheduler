(ns aws-polling-task-scheduler.handlers
  "`schedule` inserts a Dynamo item with a `next` key holding the next
  timestamp at which the task ought to be run, returning a `token` (for
  cancellation) stored on the item.

  `poll-scheduled` runs periodically, querying for items scheduled prior to now +
  `RATE`, executes them, and inserts an item with same `token` but holding the
  updated `next` value."
  (:require [cljs-lambda.macros  :as m]
            [cljs-lambda.context :as ctx]
            [aws-polling-task-scheduler.dynamo :as dynamo]
            [aws-polling-task-scheduler.util   :as util]
            [aws-polling-task-scheduler.core   :as core]
            [glossop.core    :as g]
            [cljs.core.async :as async]
            [taoensso.timbre :as log]))

(def ^:private task-parallelism 5)

(defn- process-one! [table task out-ch]
  (log/debug "Processing" task)
  (g/go-catching
    (try
      (g/<? (core/execute! task))
      (catch js/Error e
        (log/error e "Unable to process task, deleting.")))
    (dynamo/delete!
     table
     (select-keys task [:type :timestamp])
     {:chan out-ch})))

(m/deflambda poll-scheduled [_ ctx]
  (let [table  (ctx/env ctx :TASK_TABLE)]
    (async/pipeline-async
     task-parallelism
     (async/chan (async/dropping-buffer 1))
     (partial process-one! table)
     (dynamo/by-timestamp! table (js/Date.now)))))

(defn- from-json [x]
  (-> x js/JSON.parse (js->clj :keywordize-keys true)))

(defn- req->task [req]
  {:pre [(string? (:body req))]
   :post [(every? % [:method :timestamp :id])]}
  (let [task (-> req
                :body
                from-json
                (assoc :type :task))]
    (cond-> task
      (not (task :id)) (assoc :id (util/token)))))

(defn- response [body & [ks]]
  (merge
   {:status  200
    :headers {:content-type "application/json"}
    :body    (js/JSON.stringify (clj->js body))}
   ks))

(m/defgateway schedule
  "Turn an API Gateway request into a DynamoDB item in the scheduling table.

Response JSON contains an item w/ an `:id` key."
  [req ctx]
  (log/debug "Received request" req)
  (let [task  (req->task req)
        table (ctx/env ctx :TASK_TABLE)]
    (log/debug "Inserting" task "into" table)
    (g/go-catching
      (g/<? (dynamo/put! table task))
      (response task))))

(m/defgateway unschedule
  [req ctx]
  (log/debug "Received request" req)
  (let [table (ctx/env ctx :TASK_TABLE)
        index (ctx/env ctx :ID_INDEX)]
    (if-let [id (-> req :path-parameters :id)]
      (g/go-catching
        (if-let [item (first
                       (g/<? (dynamo/by-id!
                              {:task-table table
                               :id-index   index
                               :id         (-> req :path-parameters :id)})))]
          (do
            (log/info "Deleting" item)
            (g/<? (dynamo/delete! table (select-keys item [:type :timestamp])))
            (response nil {:status 200}))
          (response nil {:status 404})))
      (response
       {:type    :validation
        :message "No id"
        :request req}
       {:status 400}))))
