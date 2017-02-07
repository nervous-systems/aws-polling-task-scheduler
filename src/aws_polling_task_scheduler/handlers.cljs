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
            [glossop.core :as g]
            [#?(:clj  clojure.core.async
                :cljs cljs.core.async) :as async]))

(def ^:private task-parallelism 5)

(defn- process-one! [table task out-ch]
  (g/go-catching
    (g/<? (core/execute! task))
    (let [task (assoc task :next (time/next-timestamp (task :request)))]
      (async/>! out-ch task))))

(m/deflambda poll-scheduled [_ ctx]
  (let [expiry  (+ (time/msecs) (* 1000 (ctx/env ctx :RATE)))
        table   (ctx/env ctx :TASK_TABLE)
        puts    (dynamo/putting-items table)]
    (async/pipeline-async
     task-parallelism
     puts
     (partial process-one! table)
     (dynamo/by-timestamp! table expiry))))

(defn- parse-req [req]
  {:pre [(string? (:body req))]}
  (-> req :body js/JSON.parse (js->clj :keywordize-keys true)))

(m/defgateway schedule
  "Turn an API Gateway request into a DynamoDB item in the scheduling table.

Response JSON contains an item w/ a `:token` key."
  [req ctx]
  (let [req   (parse-req req)
        item  (assoc (select-keys req [:method :params])
                :token   (util/token)
                :request req
                :next    (time/next-timestamp req))
        table (ctx/env ctx :TASK_TABLE)]
    (g/go-catching
      (g/<? (dynamo/put! table item))
      {:status  200
       :headers {:content-type "application/json"}
       :body    (js/JSON.stringify item)})))
