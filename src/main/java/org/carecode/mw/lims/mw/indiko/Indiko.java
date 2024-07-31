package org.carecode.mw.lims.mw.indiko;

import java.util.concurrent.CountDownLatch;

public class Indiko {

    public static void main(String[] args) {
        System.out.println("MDGPHM");
        SettingsLoader.loadSettings();

        // Start the scheduler for periodic tasks
        TimerScheduler scheduler = new TimerScheduler();
        scheduler.start();

        // Keep the application running
        CountDownLatch latch = new CountDownLatch(1);
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
