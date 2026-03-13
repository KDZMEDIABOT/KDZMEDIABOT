package com.localmesalevel.aisystemtakeone.contenttypes.service;

import com.localmesalevel.aisystemtakeone.assembly.model.Article;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ArtTherapyGenerator {

    public Article generateActivityArticle(String activityName, String category) {
        Article article = new Article();
        article.setTitle(activityName + " - Art Therapy Activity for " + category);

        List<Article.Section> sections = new ArrayList<>();

        sections.add(createSection("H2", "What is " + activityName + "?", 0));
        sections.add(createSection("PARAGRAPH",
            "This therapeutic art activity combines creative expression with mindful awareness...", 1));

        sections.add(createSection("H2", "Materials You'll Need", 2));
        sections.add(createSection("LIST", generateMaterialsList(activityName), 3));

        sections.add(createSection("H2", "Step-by-Step Instructions", 4));
        sections.addAll(generateInstructions(activityName));

        sections.add(createSection("H2", "Benefits", 6));
        sections.add(createSection("LIST", generateBenefitsList(), 7));

        sections.add(createSection("H3", "For Anxiety Relief", 8));
        sections.add(createSection("PARAGRAPH",
            "This activity helps ground anxious thoughts through tactile engagement...", 9));

        sections.add(createSection("H3", "For Emotional Regulation", 10));
        sections.add(createSection("PARAGRAPH",
            "The process of creation allows for non-verbal expression of complex emotions...", 11));

        sections.add(createSection("H2", "Variations", 12));
        sections.add(createSection("LIST", generateVariations(activityName), 13));

        sections.add(createSection("H2", "When to Practice", 14));
        sections.add(createSection("PARAGRAPH",
            "Dedicate 30-45 minutes when you won't be interrupted...", 15));

        article.setSections(sections);
        return article;
    }

    private String generateMaterialsList(String activityName) {
        return "- Paper or canvas (A4 or larger)\n" +
               "- Colored pencils, markers, or paint\n" +
               "- Any objects found in nature (leaves, stones)\n" +
               "- Comfortable workspace";
    }

    private List<Article.Section> generateInstructions(String activityName) {
        List<Article.Section> sections = new ArrayList<>();

        String[] steps = {
            "Set up your workspace in a quiet, comfortable area",
            "Take 5 deep breaths to center yourself",
            "Begin with the main creative element (drawing, coloring, collage)",
            "Allow your intuition to guide color and form choices",
            "Spend 20-30 minutes in continuous creation",
            "Step back and observe your work without judgment",
            "Reflect on any emotions or thoughts that arose"
        };

        for (int i = 0; i < steps.length; i++) {
            sections.add(createSection("LIST", (i + 1) + ". " + steps[i], 5 + i));
        }

        return sections;
    }

    private String generateBenefitsList() {
        return "- Reduces stress and anxiety\n" +
               "- Improves emotional awareness\n" +
               "- Enhances mindfulness\n" +
               "- Provides creative outlet\n" +
               "- Non-verbal expression of feelings";
    }

    private String generateVariations(String activityName) {
        return "- Group therapy version\n" +
               "- Guided meditation integration\n" +
               "- Nature walk extension\n" +
               "- Journaling accompaniment";
    }

    private Article.Section createSection(String type, String content, int order) {
        Article.Section section = new Article.Section();
        section.setType(type);
        section.setContent(content);
        section.setOrderIndex(order);
        return section;
    }
}
