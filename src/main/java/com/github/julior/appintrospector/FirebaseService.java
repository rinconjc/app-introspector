package com.github.julior.appintrospector;

import com.firebase.security.token.TokenGenerator;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User: rinconj
 * Date: 20/02/14 4:49 PM
 */
@Component
public class FirebaseService {
    private final static Logger LOGGER = Logger.getLogger(FirebaseService.class);

    @Value("${app-introspector-console.firebase-secret:}")
    private String fireBaseSecret;

    @Value("${app-introspector-console.firebase-path:}")
    private String fireBaseRef;

    public Map<String, String> getAuthToken(String user) throws JSONException {
        Map<String, String> values = new HashMap<String, String>();
        if(fireBaseRef!=null && fireBaseRef.trim().length()>0){
            values.put("firebaseUrl", fireBaseRef);
            LOGGER.debug("authenticating for remote user " + user);
            values.put("firebaseJwt", new TokenGenerator(fireBaseSecret).createToken(new JSONObject().put("user", user)));
        }
        return values;
    }

/*
    public Map<String, Object> getBindings(){
        if(fireBaseRef==null || fireBaseRef.trim().isEmpty()) return Collections.emptyMap();

    }
*/

    public String getFireBaseSecret() {
        return fireBaseSecret;
    }

    public void setFireBaseSecret(String fireBaseSecret) {
        this.fireBaseSecret = fireBaseSecret;
    }

    public String getFireBaseRef() {
        return fireBaseRef;
    }

    public void setFireBaseRef(String fireBaseRef) {
        this.fireBaseRef = fireBaseRef;
    }
}
