# mitte
An nREPL adapter for evaluating CLJS in MarkLogic

## Usage 

`mitte.core` provides the high-level API. Evaluate `cljs-repl`
 to turn an `nrepl` connection into a ClojureScript REPL connected to
 your local MarkLogic database (this nrepl connection must have [piggieback](https://github.com/nrepl/piggieback)
 middleware installed.) Evaluate `restart-server` to start an independent 
 `nrepl` server that already has the appropriate middleware. The `nrepl` 
 port will be printed to the screen.

Default settings expect a MarkLogic server at port `8000` with a user
`admin-local`, password `admin`.  

### Logging 
   
`console.log` statements are written to disk by MarkLogic to an `ErrorLog`
file. The default path on my machine was:

```
/Users/<ME>/Library/Application Support/MarkLogic/Data/Logs/8000_ErrorLog.txt`
```  

## Design Notes

`client/evaluator.js` is a minimal JavaScript client which is deployed
 to a local MarkLogic server, and establishes a long-polling loop with
 a local JVM server. It is automatically installed when a REPL session
 is initiated (deployment is handled by `client/start_evaluator`)

`mitte.marklogic-session` implements a small HTTP server. The client 
 sends a GET request to `http://host:port/repl` and waits for instruction, 
 from the startup routine or by editor commands. Evaluated results are
 returned by POST before the loop begins again with a new GET request.
 
In order to get the right behavior in this inverted `GET` design, we
 use a pair of blocking concurrent queues, each implemented with a
 `java.util.concurrent.LinkedBlockingQueue` to handle messaging. The
 `/repl` GET handler blocks until there's a JS form in the 
 `evaluation-queue`, at which point it returns it as the body of
 the response to our client. Likewise, the `POST` handler places the 
 body of the incoming request into the `result-queue` to be read by our
 `nREPL` server and returned to the waiting editor process.

When a session begins it is assigned a unique ID. When the server is
 restarted, a new session created with a fresh ID which causes requests
 from old processes to be rejected, stopping stale processes.

`mitte.marklogic-repl` implements a REPL environment for use with 
 standard `cljs.repl` tooling. 

The v8 environment is prepared in a multi-step process. 
- load the `evaluator.js` script (no dependencies)
- compile `cljs.core`, and load key files emitted by Closure
  (`goog/base`, `goog/deps`, `<output-dir>deps.js`)
- load `closure_bootstrap.js` to enable resource-loading via our
  HTTP server
- now we can use ClojureScript - so we load `cljs.core` and
  `mitte/marklogic-client`, which implements some final loader
  behaviour
  
    
## TODO

- Stacktraces
- Try using various MarkLogic api's from cljs
- How to compile a project for deployment?
- Odd error when `(require ...)` appears in a file (low priority,
  this is not supported syntax in cljs)
- Capture console.log statements and pass back ?  

## Quirks

- JSON objects in MarkLogic are wrapped. To reach the object itself, 
  use `jsonThing.root`.
- Strings in JSON are also wrapped. Use `jsonThing.root.aString.toString()`
- in the node.js api, `db.documents.write` writes a JSON doc to the database.
  in the javascript api, `xdmp.documentGet` does not get a document, it reads 
  from the filesystem. Use `fn.doc` to read a JSON doc from the database. 
  