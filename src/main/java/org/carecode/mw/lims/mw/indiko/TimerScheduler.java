package org.carecode.mw.lims.mw.indiko;

import java.util.Timer;
import java.util.TimerTask;

public class TimerScheduler {
    private Timer timer;

    public void start() {
        timer = new Timer(true);
        // Schedule tasks at fixed intervals
        timer.scheduleAtFixedRate(new PullSampleDataTask(), 0, 5 * 60 * 1000); // Every 5 minutes
        timer.scheduleAtFixedRate(new PushResultsTask(), 0, 10 * 60 * 1000); // Every 10 minutes
    }

    class PullSampleDataTask extends TimerTask {
        @Override
        public void run() {
            LISCommunicator.pullSampleData();
        }
    }

    class PushResultsTask extends TimerTask {
        @Override
        public void run() {
            ResultProcessor.pushResults();
        }
    }
}
