package ca.yorku.cooppilot.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** File naming (CV_Company_Role / CL_Company_Role) and, later, writing .tex files. */
@Service
public class ExportService {

    private static final Set<String> COMPANY_SUFFIXES = Set.of("canada", "usa", "group", "inc", "inc.", "ltd", "ltd.", "corp", "corporation");
    private static final Set<String> ROLE_FILLER = Set.of("co-op", "coop", "co", "op", "intern", "internship", "student",
            "winter", "summer", "fall", "term", "months", "month", "the", "and", "of", "-", "–", "(", ")");

    /** fileName("CV", "Northwind Labs Canada", "Software Developer Co-op (Winter 2027)") -> CV_NorthwindLabs_SoftwareDeveloper */
    public String fileName(String prefix, String company, String role) {
        return prefix + "_" + companyPart(company) + "_" + rolePart(role);
    }

    static String companyPart(String company) {
        String c = company == null ? "" : company.trim();
        if (c.toLowerCase(Locale.ROOT).contains(" of canada")) {
            // Royal Bank of Canada -> RBC
            return Arrays.stream(c.split("\\s+"))
                    .filter(w -> !w.isEmpty() && !Set.of("of", "the", "and", "&").contains(w.toLowerCase(Locale.ROOT)))
                    .map(w -> w.substring(0, 1).toUpperCase(Locale.ROOT)).collect(Collectors.joining());
        }
        String[] words = c.split("\\s+");
        int end = words.length;
        while (end > 1 && COMPANY_SUFFIXES.contains(words[end - 1].toLowerCase(Locale.ROOT))) end--;
        String joined = camel(Arrays.copyOf(words, end));
        return joined.isEmpty() ? "Company" : joined;
    }

    static String rolePart(String role) {
        String[] words = (role == null ? "" : role).replaceAll("[()/,]", " ").split("\\s+");
        String r = camel(Arrays.stream(words)
                .filter(w -> !w.isBlank() && !ROLE_FILLER.contains(w.toLowerCase(Locale.ROOT)) && !w.matches("\\d+"))
                .toArray(String[]::new));
        if (r.length() > 24) r = r.replace("Software", "S").replace("Engineer", "E").replace("Developer", "D");
        return r.isEmpty() ? "Role" : r;
    }

    private static String camel(String[] words) {
        return Arrays.stream(words).filter(w -> !w.isBlank())
                .map(w -> w.replaceAll("[^A-Za-z0-9+#]", ""))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining());
    }
}
