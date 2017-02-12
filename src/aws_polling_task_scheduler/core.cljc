(ns aws-polling-task-scheduler.core
  (:require [eulalie.lambda.util :as lambda]
            [eulalie.creds       :as creds]
            [#?(:clj  clojure.core.async
                :cljs cljs.core.async) :as async]))

(defmulti execute! "Execute a given task, return a channel" (comp keyword :method))

(defmethod execute! :lambda [task]
  {:pre [(task :function)]}
  (lambda/invoke! (creds/env) (task :function) :event (task :params)))
