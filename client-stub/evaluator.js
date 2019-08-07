//const request = require('request');
const got = require('got');

var argv = require('minimist')(process.argv.slice(2));
var host = argv["host"] || "localhost";
var port = argv["port"] || "9999";
var get_address = 'http://' + host + ":" + port + "/request-form";
var put_address = 'http://' + host + ":" + port + "/return-result";

function evaluate() {
    got(get_address, { json: false }).then(response => {
        console.log("received: '" + response.body + "'");
        let computed = eval(response.body);
        console.log("computed: '" + computed + "'");
        got.put(put_address, {body: String(computed)})
            .then(response => {
                console.log("PUT successful.");
            }).catch(error => {
                console.log("PUT failed: " + error);
            });
        evaluate();
    }).catch(error => {
        console.log(error);
        evaluate();
    });
}

evaluate();
