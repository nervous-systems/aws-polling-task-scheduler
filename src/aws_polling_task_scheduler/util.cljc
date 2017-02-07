(ns aws-polling-task-scheduler.util
  #?(:clj (:import [java.util UUID])))

(let [crypto #?(:clj nil :cljs (js/require "crypto"))]
  (defn token []
    #?(:clj  (str (UUID/randomUUID))
       :cljs (-> (.randomBytes crypto 16)
                 (.toString "hex")))))
