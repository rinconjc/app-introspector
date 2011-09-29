importPackage(java.util);

//converts a JS object into a java HashMap
function toMap(obj){
    var hm = new HashMap()
    for(k in obj){
        hm.put(k, toJava(obj[k]));
    }
    return hm;
}

//convert a JS array into a java ArrayList

function toArray(arr){
    var list = new ArrayList(arr.length);
    for(i in arr)
        list.add(toJava(arr[i]));
    return list;
}

//converts a json object into the equivalent java object
function toJava(obj){
    if(obj instanceof Array){
        return toArray(obj);
    } else if(obj instanceof java.lang.Object){
        return obj;
    } else if(typeof obj == "object"){
        return toMap(obj);
    } else{
        return obj;
    }
}
//add more functions


//register functions below

var functions = toJava({"toJava":toJava});

//return
functions

