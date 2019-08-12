// console.log statements are written to the MarkLogic install location, eg
// /Users/XYZ/Library/Application Support/MarkLogic/Data/Logs/8000_ErrorLog.txt

// networking utils
const {httpGet, httpPut, httpPost} = xdmp

// options are passed in via the nodejs api command that runs this script
const {session_id, repl_host, repl_port} = options

// our local repl web service
const service_address = (path) => 'http://' + repl_host + ":" + repl_port + path
const repl_address = service_address("/repl")
const resource_address = service_address("/resource")

const repl_options = {
    timeout: 99999,
    headers: {'x-repl-session-id': session_id}
}

const resource = (path) => {
    const [headers, body] = httpPost(resource_address, {data: path})
    if (headers.code !== 200) {
        console.log(body)
        throw `could not require ${path}`
    }
    return body.toString()
}

var CLJS_ROOT = "."

this.goog = {} // must define this here to work around MarkLogic global context issue

const eval_js = (_js_src_) => {
    // use `with` to force v8 to look at global object first,
    // due to a quirk in MarkLogic's js context.
    with (this) {
        return eval(_js_src_)
    }
}

var format_result = (result) => {
    // will be overwritten by mitte.marklogic-repl,
    // to also return the formatted result
    return {status: result.status}
}

function put(data) {
    let [put_headers, put_resp] = httpPut(repl_address, {data: JSON.stringify(format_result(data)), ...repl_options})
    console.log(put_headers.code === 200 ? 'PUT successful' : ['PUT failed', put_headers])
}

function evaluate() {

    let [req_headers, req_body] = httpGet(repl_address, repl_options),
        payload = req_body.root,
        action = payload.action.toString()

    if (req_headers.code !== 200) {
        console.log('repl:GET error', action, code)
        return
    }

    switch (action) {
        case 'eval':
            try {
                const code = payload.code.toString(),
                    result = eval_js(code)
                put({
                    status: "success",
                    value: result
                })

            } catch (error) {
                put({
                    status: "exception",
                    value: error.toString()
                })
            }
            break
        case 'quit':
            put({
                type: "success",
                value: ":cljs/quit"
            })
            return
        default:
            console.log(`unknown action ${payload.action}`)
    }
    evaluate()

}

evaluate()