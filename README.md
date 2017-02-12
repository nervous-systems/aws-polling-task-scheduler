# aws-polling-task-scheduler

The project is self-contained - `serverless deploy` ought to remotely create the
API without any other inputs.

## Schedule

`curl -X POST https://xyz.execute-api.us-east-1.amazonaws.com/dev/task/schedule -H'Content-Type: application/json' -d'{"method": "lambda", "function": "task-ok", "params": {"a": 1}, "timestamp": 0}'`

The above'll create a task which'll run in the next poll interval.

 - `method`: Arbitrary, is used as dispatch value of core/execute!
 - `function`, `params`: Consumed by the `:lambda` implementation of `core/execute!`
 - `timestamp`: msecs since the epoch
 - `id`: reference for deletion.  if not supplied, the response will contain the assigned ID

### Lambda

The `poll-scheduled` function must have sufficient privileges to invoke whatever
functions are scheduled for Lambda invocation.  By default there's an IAM
statement permitting calling any function prefixed with `task-`
(i.e. `task-hello-world`).

## Unschedule

`curl -X DELETE https://xyz.execute-api.us-east-1.amazonaws.com/dev/task/{id}`

(e.g. `/dev/task/xxx`)

## Polling

Polling occurs by default every 5 minutes, and executes all expired tasks
(i.e. where `timestamp` < now).  Executes a configurable number of tasks in
parallel, deleting the Dynamo record of any task it's unable to issue.

There ought to be useful information in the CloudWatch logs for each function.
