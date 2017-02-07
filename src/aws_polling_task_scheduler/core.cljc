(ns aws-polling-task-scheduler.core
  (:require [#?(:clj  clojure.core.async
                :cljs cljs.core.async) :as async]))

(defmulti execute! "Execute a given task, return a channel" :method)

(defmethod execute! :default [task]
  (async/go "Stub implementation"))
