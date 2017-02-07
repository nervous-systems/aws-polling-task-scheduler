(ns aws-polling-task-scheduler.time
  (require #?(:clj  [clj-time.core  :as t]
              :cljs [cljs-time.core :as t])
           #?(:clj  [clj-time.coerce  :as t.coerce]
              :cljs [cljs-time.coerce :as t.coerce])))

(defn- msecs []
  (-> (t/now) (t.coerce/to-long)))

(defn- next-timestamp "Stub implementation"
  [request]
  (-> (t/now) (t/plus (t/minutes 5)) t.coerce/to-long))
