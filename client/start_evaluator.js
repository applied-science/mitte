#!/usr/bin/env node

const marklogic = require('marklogic'),
    fs = require('fs'),
    path = require('path'),
    minimist = require('minimist')

// extract config from command-line args
const args = minimist(process.argv)
const {user, password, port, host, database, repl_host, repl_port, session_id} = args

// create MarkLogic nodejs client
const db = marklogic.createDatabaseClient({
    authType: 'DIGEST',
    host,
    port,
    database,
    user,
    password
})

const install = () => {
    // install evaluator script in MarkLogic
    return db.config.extlibs.write({
        path: '/ext/invoke/cljs_evaluator.sjs',
        contentType: 'application/vnd.marklogic-javascript',
        source: fs.createReadStream(path.join(__dirname, 'evaluator.js'))
    }).result()
}

const start = async () => {
    const {path} = await install()
    db.invoke({path, variables: {options: {session_id: session_id, repl_host, repl_port}}}).result()
}

start()
