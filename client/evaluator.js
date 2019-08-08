const {httpGet, httpPut} = xdmp

host = 'localhost'
port = 9999

const get_address = 'http://' + host + ":" + port + "/request-form"
const put_address = 'http://' + host + ":" + port + "/return-result"
const get_options = {
    timeout: 99999
}

function evaluate() {
    let [get_headers, get_resp] = httpGet(get_address, get_options)
    console.log('eval:', get_resp)
    let computed = xdmp.eval(get_resp)
    console.log('computed:', computed)

    let [put_headers, put_resp] = httpPut(put_address, {data: computed.toString()})

    console.log(put_headers.code === 200 ? 'PUT successful' : 'PUT failed')

    evaluate()
}

evaluate()