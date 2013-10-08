package com.github.julior.appintrospector;

import java.util.TimerTask;

/**
 * User: rinconj
 * Date: 4/9/13 11:40 AM
 */
public class RunnableTimerTask extends TimerTask {
    private final Runnable body;

    public RunnableTimerTask(Runnable body) {
        this.body = body;
    }

    @Override
    public void run() {
        body.run();
    }
}
