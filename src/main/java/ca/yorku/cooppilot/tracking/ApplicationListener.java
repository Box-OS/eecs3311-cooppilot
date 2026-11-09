package ca.yorku.cooppilot.tracking;

/** Observer pattern — Observer of the ApplicationTracker. */
public interface ApplicationListener {

    void onApplicationChanged(Application app, StatusEvent event);
}
