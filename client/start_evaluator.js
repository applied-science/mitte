#!/usr/bin/env node

// by default we assume you have created user: 'local-admin' with password: 'admin'

argv = require('minimist')(process.argv);

const user = argv["user"] || "local-admin"
const password = argv["password"] || "admin"
const port = argv["port"] || '8000'
const host = argv["host"] || 'localhost'
const database = argv["database"] || 'Documents'

const opts = {
    authType: 'DIGEST',
    host,
    port,
    database,
    user,
    password
}

const marklogic = require('marklogic'),
    fs = require('fs'),
    path = require('path'),
    db = marklogic.createDatabaseClient(opts),
    evaluatorPath = '/ext/invoke/cljs_evaluator.sjs'

const install = () => {
    return db.config.extlibs.write({
        path: evaluatorPath,
        contentType: 'application/vnd.marklogic-javascript',
        source: fs.createReadStream(path.join(__dirname, 'evaluator.js'))
    }).result()
}

const start = async () => {
    const {path} = await install()
    db.invoke({path}).result()
}

start()
