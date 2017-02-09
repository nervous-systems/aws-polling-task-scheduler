(defproject aws-polling-task-scheduler "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure       "1.8.0"]
                 [org.clojure/clojurescript "1.8.51"]
                 [io.nervous/cljs-lambda    "0.3.4"]
                 [io.nervous/hildebrand     "0.4.5"]]
  :plugins [[lein-cljsbuild "1.1.5"]
            [lein-npm       "0.6.2"]
            [io.nervous/lein-cljs-lambda "0.6.4"]]
  :cljsbuild
  {:builds [{:id "aws-polling-task-scheduler"
             :source-paths ["src"]
             :compiler {:output-to     "target/aws-polling-task-scheduler/aws_polling_task_scheduler.js"
                        :output-dir    "target/aws-polling-task-scheduler"
                        :target        :nodejs
                        :language-in   :ecmascript5
                        :optimizations :none}}]})
