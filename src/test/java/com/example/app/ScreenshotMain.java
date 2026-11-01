package com.example.app;

/** plain bootstrap: the JDK refuses Main-classes extending Application on the classpath */
public class ScreenshotMain {
    public static void main(String[] args) {
        javafx.application.Application.launch(ScreenshotHarness.class, args);
    }
    static { } // no-op
}
