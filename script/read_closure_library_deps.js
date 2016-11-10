require("google-closure-library");
require("google-closure-library/closure/goog/deps.js");
fs = require("fs")
fs.writeFileSync(__dirname + "/deps_cache.json", JSON.stringify(goog.dependencies_));
console.log(JSON.stringify(goog.dependencies_));