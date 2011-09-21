importPackage(java.util);

//converts a JS object into a java HashMap
function map(obj){
    var hm = new HashMap()
    for(k in obj){
        if(typeof obj[k] =="object"){
            hm.put(k, map(obj[k]));
        } else {
            hm.put(k, obj[k]);
        }
    }
    return hm;
}
//add more functions

var functions = map({"map":map});
//return
functions

