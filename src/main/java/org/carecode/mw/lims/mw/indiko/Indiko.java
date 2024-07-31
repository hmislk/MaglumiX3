package org.carecode.mw.lims.mw.indiko;

public class Indiko {

    public static void main(String[] args) {
        System.out.println("MDGPHM");
        SettingsLoader.loadSettings();

        Scheduler scheduler = new Scheduler();
        scheduler.start();
    }
}
