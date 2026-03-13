package com.localmesalevel.aisystemtakeone.contenttypes.service;

import com.localmesalevel.aisystemtakeone.assembly.model.Article;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RecommendationListGenerator {

    public Article generateListArticle(String topic, String year) {
        Article article = new Article();
        article.setTitle("The Best " + topic + " You Can't Miss in " + year);
        article.setSlug("best-" + topic.toLowerCase().replace(" ", "-") + "-" + year);

        List<Article.Section> sections = new ArrayList<>();

        sections.add(createSection("H2", "Why These " + topic + " Matter", 0));
        sections.add(createSection("PARAGRAPH",
            "After extensive research and testing, we've curated this definitive list...", 1));

        sections.add(createSection("H2", "Our Selection Criteria", 2));
        sections.add(createSection("LIST", generateCriteriaList(), 3));

        // Generate numbered recommendations
        int startIndex = 4;
        List<Recommendation> recommendations = generateRecommendations(topic);

        for (int i = 0; i < recommendations.size(); i++) {
            Recommendation rec = recommendations.get(i);

            sections.add(createSection("H2", (i + 1) + ". " + rec.getName(), startIndex + i * 4));

            StringBuilder details = new StringBuilder();
            details.append("Key Features: ").append(rec.getFeatures()).append("\n\n");
            details.append("Best For: ").append(rec.getBestFor()).append("\n\n");
            details.append("Pros: ");
            for (String pro : rec.getPros()) {
                details.append("+ ").append(pro).append(" ");
            }
            details.append("\n\n");

            sections.add(createSection("PARAGRAPH", details.toString(), startIndex + i * 4 + 1));

            sections.add(createSection("QUOTE",
                "\"" + rec.getTestimonial() + "\"", startIndex + i * 4 + 2));

            sections.add(createSection("H3", "Where to Access", startIndex + i * 4 + 3));
            sections.add(createSection("PARAGRAPH",
                "Available at: " + rec.getLink(), startIndex + i * 4 + 4));
        }

        sections.add(createSection("H2", "Comparison Table", startIndex + recommendations.size() * 4 + 1));
        sections.add(createSection("PARAGRAPH", generateComparisonTable(recommendations),
            startIndex + recommendations.size() * 4 + 2));

        sections.add(createSection("H2", "How to Choose", startIndex + recommendations.size() * 4 + 3));
        sections.add(createSection("LIST", generateSelectionGuide(), startIndex + recommendations.size() * 4 + 4));

        sections.add(createSection("H2", "Final Thoughts", startIndex + recommendations.size() * 4 + 5));
        sections.add(createSection("PARAGRAPH",
            "Choosing the right resources is personal...", startIndex + recommendations.size() * 4 + 6));

        article.setSections(sections);
        return article;
    }

    private String generateCriteriaList() {
        return "- Evidence-based effectiveness\n" +
               "- User satisfaction ratings\n" +
               "- Accessibility and cost\n" +
               "- Professional recommendations\n" +
               "- Long-term value";
    }

    private List<Recommendation> generateRecommendations(String topic) {
        List<Recommendation> recommendations = new ArrayList<>();

        recommendations.add(createRecommendation(
            "Headspace Meditation App",
            "Guided meditations, sleep content, mindful movement",
            "Beginners and busy professionals",
            new String[]{"User-friendly interface", "Variety of content", "Progress tracking"},
            "\"This app transformed my morning routine completely.\"",
            "https://headspace.com"
        ));

        recommendations.add(createRecommendation(
            "Calm",
            "Sleep stories, breathing exercises, masterclasses",
            "Sleep and stress management",
            new String[]{"Quality sleep content", "Celebrity narrators", "Offline access"},
            "\"The sleep stories are incredibly soothing.\"",
            "https://calm.com"
        ));

        recommendations.add(createRecommendation(
            "Insight Timer",
            "Free library, timer, courses, community",
            "Budget-conscious practitioners",
            new String[]{"Largest free library", "Community features", "Customizable timers"},
            "\"Best free meditation resource available.\"",
            "https://insighttimer.com"
        ));

        recommendations.add(createRecommendation(
            "Sanvello",
            "CBT tools, mood tracking, coaching",
            "Anxiety and depression support",
            new String[]{"Evidence-based CBT", "Professional coaching", "Insurance coverage"},
            "\"The CBT exercises are genuinely helpful.\"",
            "https://sanvello.com"
        ));

        recommendations.add(createRecommendation(
            "Ten Percent Happier",
            "Meditation courses, practical approach",
            "Skeptics and busy professionals",
            new String[]{"No-nonsense approach", "Expert teachers", "Short sessions"},
            "\"Finally, meditation explained without the fluff.\"",
            "https://tenpercent.com"
        ));

        return recommendations;
    }

    private Recommendation createRecommendation(String name, String features, String bestFor,
                                                  String[] pros, String testimonial, String link) {
        Recommendation rec = new Recommendation();
        rec.setName(name);
        rec.setFeatures(features);
        rec.setBestFor(bestFor);
        rec.setPros(pros);
        rec.setTestimonial(testimonial);
        rec.setLink(link);
        return rec;
    }

    private String generateComparisonTable(List<Recommendation> recommendations) {
        StringBuilder table = new StringBuilder();
        table.append("| Resource | Best For | Key Feature |\n");
        table.append("|----------|----------|-------------|\n");

        for (Recommendation rec : recommendations) {
            table.append("| ").append(rec.getName())
                 .append(" | ").append(rec.getBestFor())
                 .append(" | ").append(rec.getFeatures().split(",")[0])
                 .append(" |\n");
        }

        return table.toString();
    }

    private String generateSelectionGuide() {
        return "- Budget conscious? Start with Insight Timer's free tier\n" +
               "- Sleep issues? Prioritize Calm's sleep stories\n" +
               "- Clinical support needed? Consider Sanvello\n" +
               "- Busy schedule? Ten Percent Happier's short sessions\n" +
               "- Comprehensive approach? Headspace offers the most variety";
    }

    private Article.Section createSection(String type, String content, int order) {
        Article.Section section = new Article.Section();
        section.setType(type);
        section.setContent(content);
        section.setOrderIndex(order);
        return section;
    }

    private static class Recommendation {
        private String name;
        private String features;
        private String bestFor;
        private String[] pros;
        private String testimonial;
        private String link;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getFeatures() { return features; }
        public void setFeatures(String features) { this.features = features; }
        public String getBestFor() { return bestFor; }
        public void setBestFor(String bestFor) { this.bestFor = bestFor; }
        public String[] getPros() { return pros; }
        public void setPros(String[] pros) { this.pros = pros; }
        public String getTestimonial() { return testimonial; }
        public void setTestimonial(String testimonial) { this.testimonial = testimonial; }
        public String getLink() { return link; }
        public void setLink(String link) { this.link = link; }
    }
}
