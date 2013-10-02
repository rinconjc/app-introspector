package com.github.julior.springinspector;

import org.apache.log4j.Logger;

/**
 *
 */
public class ServerMain {
    private final static Logger logger = Logger.getLogger(ServerMain.class);

    public static void main(String... args){
        try {
            JettyServer server = new JettyServer(8080);
            server.start();
            server.join();
        } catch (Exception e) {
            logger.error("Failed starting server", e);
        }
    }
}
