importPackage(java.util);

//converts a JS object into a java HashMap
function toMap(obj){
    var hm = new HashMap()
    for(k in obj){
        hm.put(k, asJson(obj[k]));
    }
    return hm;
}

//convert a JS array into a java ArrayList

function toArray(arr){
    var list = new ArrayList(arr.length);
    for(i in arr)
        list.add(asJson(arr[i]));
    return list;
}

//converts a json object into the equivalent java object
function asJson(obj){
    if(obj instanceof Array){
        return toArray(obj);
    } else if(typeof obj == "object"){
        return toMap(obj);
    } else{
        return obj;
    }
}
//add more functions


//register functions below

var functions = asJson({"asJson":asJson});

//return
functions

