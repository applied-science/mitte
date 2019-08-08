// logs are written to the MarkLogic install location, eg
// /Users/XYZ/Library/Application Support/MarkLogic/Data/Logs/8000_ErrorLog.txt

const {httpGet, httpPut} = xdmp

const {session, host, repl_port} = options

const get_address = 'http://' + host + ":" + repl_port + "/request-form"
const put_address = 'http://' + host + ":" + repl_port + "/return-result"
const headers = {'x-repl-session': session}
const get_options = {
    timeout: 99999,
    headers
}

function evaluate() {
    try {
        console.log('evaluating')
        let [req_headers, req_eval] = httpGet(get_address, get_options)
        if (req_headers.code !== 200) {
            console.log('get_error', req_headers, req_eval)
            return
        }
        console.log('eval:', req_eval)
        let eval_result = xdmp.eval(req_eval)
        console.log('computed:', eval_result)

        let [put_headers, put_resp] = httpPut(put_address, {data: eval_result.toString()})

        console.log(put_headers.code === 200 ? 'PUT successful' : 'PUT failed')

        evaluate()
    } catch (error) {
        console.log('general evaluation error:', error)
    }
}

evaluate()