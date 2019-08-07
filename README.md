# mitte
An nREPL adapter for evaluating CLJS in MarkLogic

## Design Notes

We've started with a `client-stub` that pretends to be our future
captive JS runtime within MarkLogic. It's a very small `node`
application that performs an `HTTP GET` against
`http://host:port/request-form`, evaluates the body of whatever
request it receives, then performs an `HTTP PUT` to
`http://host:port/return-result`. There's currently no real error
handling in this code, but it's enough to get started on the Clojure
side.

The file `mitte.clj` contains a minimal implementation of the Clojure
side of our middleware that services the `HTTP` endpoint with which
the `client-stub` interacts.

In order to get the right behavior in this inverted `GET` design, we
use a pair of blocking concurrent queues, each implemented with a
`java.util.concurrent.LinkedBlockingQueue` to handle messaging. The
handler for the `request-form` endpoint blocks until there's a JS form
in the `evaluation-queue`, at which point it returns it as the body of
the response to our client. Likewise, the `return-result` endpoint
places the body of the incoming request into the `result-queue` to be
read by our `nREPL` server and returned to the waiting editor process.

## TODO

It looks to me like we can use `nrepl.server` and `cider.piggieback`
to handle the editor client side of this project. The main thing seems
to be implementing a few protocols to create an `IJavaScriptEnv` with
which to create a `piggieback` handler to pass to `nrepl.server`.

Details regarding Piggieback:

https://github.com/nrepl/piggieback

An example of a wrapped JS runtime:

https://github.com/clojure/clojurescript/blob/master/src/main/clojure/cljs/repl/nashorn.clj

I've pulled in, commented out, and renamed the `NashhornEnv`
definition in the above file to `MarkLogicEnv`, but haven't started
coding the functions yet.
