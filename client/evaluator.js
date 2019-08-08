// can put this file in the root directory of a MarkLogic app server and run from a browser

const {httpGet, httpPut} = xdmp,
    scriptIndex = Math.random() // for debugging, to differentiate concurrently running processes

host = 'localhost'
port = 9999

const get_address = 'http://' + host + ":" + port + "/request-form"
const put_address = 'http://' + host + ":" + port + "/return-result"
const get_options = {
    timeout: 99999
}

function evaluate() {

    try {
        console.log('evaluating')
        let [get_headers, get_resp] = httpGet(get_address, get_options)
        if (get_headers.code !== 200) {
              console.log('get_error', get_headers)
              return
        }
        console.log('eval:', get_resp)
        let computed = xdmp.eval(get_resp)
        console.log('computed:', computed)

        let [put_headers, put_resp] = httpPut(put_address, {data: 'process: '+ scriptIndex + ', result:' + computed.toString()})

        console.log(put_headers.code === 200 ? 'PUT successful' : 'PUT failed')

        evaluate()
    } catch (error) {
          console.log('general evaluation error:', error)
    }

}

evaluate()