package ca.yorku.cooppilot.tracking;

import java.time.LocalDate;

/** One entry in an application's history. */
public record StatusEvent(Status from, Status to, LocalDate on, String note) {
}
