# mitte
An nREPL adapter for evaluating CLJS in MarkLogic

## Requirements

- Node.js (tested with v12)
- Clojure (tested with 1.10.0)
- MarkLogic Server (tested with 10.0-1) 

## Quickstart

1. Ensure node.js is installed (v12 is recommended, [nvm](https://github.com/nvm-sh/nvm) is handy for managing
versions) and run `yarn install`. You may safely ignore node-gyp install errors. 
1. Run `clj -m mitte.core` to start the nREPL server. A functional REPL will appear 
in the terminal but you will more likely want to connect with an editor (emacs/Cursive) 
using the nrepl port, which is printed and also written to the `.nrepl-port` file.
1. Ensure that you have a local MarkLogic database running on port 8000 with an 
`admin-local:admin` user (or pass your own `:username` and `:password` in an options map
in the next step). 
1. Evaluate `(mitte.core/cljs-repl)` to begin a MarkLogic javascript session and connect
the current REPL to it. You can now eval!

### Logging 

Clojure print statements are forwarded to your original REPL window.

`console.log` statements are written to disk by MarkLogic to an `ErrorLog`
file. The default path on my machine was: 

```
/Users/<ME>/Library/Application Support/MarkLogic/Data/Logs/8000_ErrorLog.txt
```

## Design Notes

`src/cljs_evaluator.js` is a minimal JavaScript client which is deployed
 to a local MarkLogic instance, and establishes a long-polling loop with
 a local JVM server dubbed "Mitte", which is run alongside the nREPL server. 
 It is automatically installed and invoked on each call to 
 `mitte.core/cljs-repl` (deployment is implemented in `scripts/start_evaluator.js`)

`mitte.mitte-server` implements the HTTP server handling the REPL loop.  
 Clients send GET requests to `http://host:port/repl` and await instruction, 
 then respond via POST before the next GET. Unusually for JavaScript,
 the MarkLogic v8 context is fully synchronous.
 
To get the right behavior in this inverted `GET` design on the JVM side, we
 use a pair of blocking concurrent queues, each implemented with a
 `java.util.concurrent.LinkedBlockingQueue` to handle messaging. The
 `/repl` GET handler blocks until there's a JS form in the 
 `evaluation-queue`, at which point it returns it as the body of
 the response to our client. Likewise, the `POST` handler places the 
 body of the incoming request into the `result-queue` to be read by our
 `nREPL` server and returned to the waiting editor process.

When a session begins it is assigned a unique ID. When the server is
 restarted, requests referring to old sessions will be rejected. 

`mitte.repl-env` implements a REPL environment implementing the necessary 
 protocols to work with standard `cljs.repl` tooling. 

The v8 environment is prepared in a multi-step process. 
- load `cljs_evaluator.js` script (no dependencies)
- compile `cljs.core`, then load a few of the emitted files:
  `goog/base`, `goog/deps`, and `<output-dir>deps.js`
- load `closure_bootstrap.js` to enable the Closure Library
  to load additional files on its own
- now we are ready for ClojureScript - load `cljs.core` and
  `mitte/marklogic-client`, which implements some final loader
  behaviour
  
    
## TODO

- Stacktraces
- Try using various MarkLogic api's from cljs
- How to compile a project for deployment?
- Odd error when `(require ...)` appears in a file (low priority,
  this is not supported syntax in cljs)

## Quirks

- JSON objects in MarkLogic are wrapped. To reach the object itself, 
  use `jsonThing.root`.
- Strings in JSON are also wrapped. Use `jsonThing.root.aString.toString()`
- in the node.js api, `db.documents.write` writes a JSON doc to the database.
  in the javascript api, `xdmp.documentGet` does not get a document, it reads 
  from the filesystem. Use `fn.doc` to read a JSON doc from the database. 
  