package ca.yorku.cooppilot.service.edit;

import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.service.LatexText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** "What changed" between two versions of a resume's blocks (UC07 step 5): added, removed and reworded items. */
public final class ResumeDiff {

    public enum Kind { ADDED, REMOVED, REWORDED, BLOCK_ADDED, BLOCK_REMOVED }

    /** One change; {@code before}/{@code after} are plain text (empty when not applicable). */
    public record Change(Kind kind, String block, String before, String after) {}

    private ResumeDiff() {}

    public static List<Change> between(List<ResumeBlock> before, List<ResumeBlock> after) {
        Map<String, ResumeBlock> old = new LinkedHashMap<>();
        before.forEach(b -> old.put(b.getId(), b));
        Map<String, ResumeBlock> now = new LinkedHashMap<>();
        after.forEach(b -> now.put(b.getId(), b));
        List<Change> out = new ArrayList<>();
        for (ResumeBlock b : before) {
            if (!now.containsKey(b.getId())) out.add(new Change(Kind.BLOCK_REMOVED, b.getTitle(), b.getTitle(), ""));
        }
        for (ResumeBlock b : after) {
            ResumeBlock o = old.get(b.getId());
            if (o == null) {
                out.add(new Change(Kind.BLOCK_ADDED, b.getTitle(), "", b.getTitle()));
                continue;
            }
            List<String> ob = o.getBullets();
            List<String> nb = b.getBullets();
            int n = Math.max(ob.size(), nb.size());
            for (int i = 0; i < n; i++) {
                String x = i < ob.size() ? ob.get(i) : null;
                String y = i < nb.size() ? nb.get(i) : null;
                if (x != null && y != null && !x.equals(y)) {
                    // a removed bullet shifts the rest: report it as a removal when the next old bullet matches
                    if (i + 1 < ob.size() && ob.get(i + 1).equals(y) && ob.size() > nb.size()) {
                        out.add(new Change(Kind.REMOVED, b.getTitle(), LatexText.plain(x), ""));
                        ob = new ArrayList<>(ob);
                        ob.remove(i);
                        n = Math.max(ob.size(), nb.size());
                        i--;
                        continue;
                    }
                    out.add(new Change(Kind.REWORDED, b.getTitle(), LatexText.plain(x), LatexText.plain(y)));
                } else if (x != null && y == null) {
                    out.add(new Change(Kind.REMOVED, b.getTitle(), LatexText.plain(x), ""));
                } else if (x == null && y != null) {
                    out.add(new Change(Kind.ADDED, b.getTitle(), "", LatexText.plain(y)));
                }
            }
        }
        return out;
    }
}
