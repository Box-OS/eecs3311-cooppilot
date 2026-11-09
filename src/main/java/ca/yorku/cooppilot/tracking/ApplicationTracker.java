package ca.yorku.cooppilot.tracking;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * F11. Observer pattern — Subject. Creates applications and changes their status (the legality of each
 * move is decided by the State objects), saves them, and notifies listeners such as the dashboard.
 */
@Service
public class ApplicationTracker {

    private final ApplicationRepository repo;
    private final Clock clock;
    private final List<ApplicationListener> listeners = new CopyOnWriteArrayList<>();

    public ApplicationTracker(ApplicationRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    public void addListener(ApplicationListener l) {
        listeners.add(l);
    }

    public Application create(String company, String role, String postingId, String resumeName) {
        Application a = new Application(null, company, role, postingId, resumeName);
        repo.save(a);
        listeners.forEach(l -> l.onApplicationChanged(a, null));
        return a;
    }

    public Application transition(String id, Status target, String note) {
        Application a = find(id);
        StatusEvent e = a.transitionTo(target, LocalDate.now(clock), note);
        repo.save(a);
        listeners.forEach(l -> l.onApplicationChanged(a, e));
        return a;
    }

    public void delete(String id) {
        Application a = find(id);
        repo.delete(id);
        listeners.forEach(l -> l.onApplicationChanged(a, null));
    }

    public Application find(String id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("No application with id " + id));
    }

    /** Newest activity first. */
    public List<Application> all() {
        return repo.findAll().stream()
                .sorted(Comparator.comparing((Application a) -> a.getAppliedOn() == null ? LocalDate.MAX : a.getAppliedOn())
                        .reversed())
                .toList();
    }
}
