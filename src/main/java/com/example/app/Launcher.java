package com.example.app;

/**
 * Bootstrap entry point for the shaded (fat) jar.
 *
 * The JDK launcher refuses to start a Main-Class that extends
 * javafx.application.Application when JavaFX is on the classpath
 * ("JavaFX runtime components are missing"). Launching through this
 * plain bootstrap class avoids that check; JavaFX classes load from
 * the shaded jar normally.
 */
public class Launcher {

    public static void main(String[] args) {
        javafx.application.Application.launch(MainApp.class, args);
    }
}
