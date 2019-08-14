// console.log statements are written to the MarkLogic install location, eg
// /Users/XYZ/Library/Application Support/MarkLogic/Data/Logs/8000_ErrorLog.txt

// networking utils
const {httpGet, httpPut} = xdmp

// options are passed in via the nodejs api command that runs this script
const {session_id, mitte_host, mitte_port} = options

// our local repl web service
const service_address = (path) => 'http://' + mitte_host + ":" + mitte_port + path
const repl_address = service_address("/repl")
const resource_address = service_address("/resource")

const repl_options = {
    timeout: 99999,
    headers: {'x-repl-session-id': session_id}
}

const resource = (path) => {
    const url = resource_address + '/' + path
    const [headers, body] = httpGet(url, {timeout: 1})
    if (headers.code !== 200) {
        throw Error(`${headers.code}: ${headers.message}, could not require ${path}.`)
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
// will be overwritten by mitte.marklogic-repl implementation
var format_result = (result) => JSON.stringify(result)

function put_result(data) {
    let [put_headers, put_resp] = httpPut(repl_address, {
        data: format_result(data), ...repl_options,
        headers: {'Content-Type': this.mitte ? 'application/edn' : 'application/json'}
    })
    console.log(put_headers.code === 200 ? 'PUT successful' : ['PUT failed', put_headers])
}

function invoke() {
    console.log(`invoke...`)
    const [req_headers, req_body] = httpGet(repl_address, repl_options)
    let payload = req_body.root
    let action = payload.action.toString()

    if (req_headers.code !== 200) {
        console.log('repl:GET error', action, code)
        return
    }

    switch (action) {
        case 'eval':
            try {
                const code = payload.code.toString(),
                    result = eval_js(code)
                put_result({
                    status: "success",
                    value: result
                })

            } catch (error) {
                console.log('log', error.stack)
                console.log(error.toString())
                put_result({
                    status: "exception",
                    value: error.stack.toString()
                })
            }
            break
        case 'quit':
            put_result({
                type: "success",
                value: ":cljs/quit"
            })
            return
        default:
            console.log(`unknown action ${payload.action}`)
    }
    invoke()

}

invoke()