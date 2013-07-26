var job='''
importPackage(javax.xml.bind)
importPackage(javax.crypto)
importPackage(javax.crypto.spec)
var keys = {"foxkey":"fsdfsdfsdfasd", "ndmkey":"dfgsdgsdgsdfg"}
var urls = {"hls":"http://foxsports-i.akamaihd.net/hls/live/205818/foxsportsnews/foxsportsnews.m3u8?hdnea="
, "hds":"http://ndmtesthd-lh.akamaihd.net/z/foxsportsnews_0@102937/manifest.f4m?hdnea="}
var asset = "kwNXYxYjovdY-P3C7RsU_dZwucGAzP8j"
var expiry=300
//generate token
function getToken(key, expiry){
    var now = System.currentTimeMillis();
    var tokenStr = new java.lang.String("st="+now+"~exp="+(now + expiry*1000)+"~acl=/*")
    var mac = Mac.getInstance("HmacSHA256")
    mac.init(new SecretKeySpec(DatatypeConverter.parseHexBinary(key),"HmacSHA256"))
    var hmac = DatatypeConverter.printHexBinary(mac.doFinal(tokenStr.getBytes()))
    return tokenStr+"~hmac="+hmac
}
// update url
function updateUrl(){
        var iphoneUrl = urls.hls + getToken(keys.foxkey, expiry)
        var flashUrl = urls.hds + getToken(keys.ndmkey, expiry)
        var patch = '{"stream_urls": {"iphone":"'+ iphoneUrl + '","flash":"'+flashUrl+'"}}'
        var ooyala = ${ooyalaClients}.get("FATWIRE")
        ooyala.patchRequest("/v2/assets/" + asset, patch)
        return toJava(patch);
}

var job = toRunnable(function(){var r=updateUrl(); System.out.println("Rotate URL" + r)})
'''
