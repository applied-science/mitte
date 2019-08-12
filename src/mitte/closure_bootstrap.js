goog.isProvided_ = function (_) {
    return false
}

// These are minimal/temporary implementations, better versions written
// in ClojureScript are loaded later

CLOSURE_IMPORT_SCRIPT = (path, src) => {
    if (src) {
        return src
    }
    return eval_js(resource(`goog/${path}`))
}

goog.require = (name) => {
    return CLOSURE_IMPORT_SCRIPT(goog.dependencies_.nameToPath[name])
}