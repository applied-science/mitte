#!/usr/bin/env node

const marklogic = require('marklogic'),
    fs = require('fs'),
    path = require('path'),
    minimist = require('minimist')

// extract config from command-line args
const args = minimist(process.argv)
const {user, password, port, host, database, mitte_host, mitte_port, session_id} = args

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
        source: fs.createReadStream(path.join(__dirname, '../src', 'cljs_evaluator.js'))
    }).result()
}

let invocation_result = null

const invoke = async (path) => {
    invocation_result = await db.invoke({
        path,
        variables: {options: {session_id: session_id, mitte_host, mitte_port}}
    }).result()
    console.log(invocation_result)
    process.exit(1)
}

const start = async () => {
    try {
        console.log(`-- installing cljs_evaluator...`)
        const {path} = await install()

        console.log(`-- invoking evaluator`)
        invoke(path)

        // after the invocation has begun on MarkLogic,
        // we can shut down this node.js process
        setTimeout(() => {
            process.exit(0)
        }, 300)
    } catch (error) {
        console.error(error.message, error.body)
        process.exit(1)
    }

}

start()
