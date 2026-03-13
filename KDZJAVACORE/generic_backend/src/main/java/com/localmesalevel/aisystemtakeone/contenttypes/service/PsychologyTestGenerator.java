package com.localmesalevel.aisystemtakeone.contenttypes.service;

import com.localmesalevel.aisystemtakeone.assembly.model.Article;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PsychologyTestGenerator {

    public Article generateTestArticle(String testName, String description) {
        Article article = new Article();
        article.setTitle(testName + " - Free Online Self-Assessment");
        article.setSlug("");

        List<Article.Section> sections = new ArrayList<>();

        sections.add(createSection("H2", "Understanding " + testName, 0));
        sections.add(createSection("PARAGRAPH",
            "This scientifically validated assessment helps identify...", 1));

        sections.add(createSection("H3", "Who Should Take This Test?", 2));
        sections.add(createSection("LIST", generateAudienceList(), 3));

        sections.add(createSection("H2", "The 10 Key Questions", 4));
        sections.add(createSection("PARAGRAPH",
            "Answer honestly based on your experiences over the past month.", 5));

        List<Article.Section> questions = generateTestQuestions(testName);
        sections.addAll(questions);

        sections.add(createSection("H2", "Interpreting Your Results", 6));
        sections.add(createSection("PARAGRAPH",
            "After completing the assessment, you'll receive...", 7));

        sections.add(createSection("H3", "Score Ranges", 8));
        sections.add(createSection("LIST", generateScoreRanges(), 9));

        sections.add(createSection("H2", "Next Steps", 10));
        sections.add(createSection("PARAGRAPH",
            "If your results suggest further evaluation...", 11));

        article.setSections(sections);
        return article;
    }

    private List<Article.Section> generateTestQuestions(String testName) {
        List<Article.Section> questions = new ArrayList<>();

        String[] sampleQuestions = {
            "I often feel overwhelmed by everyday tasks",
            "I find it difficult to make decisions without others' input",
            "I seek approval from others before taking action",
            "I feel anxious when I'm alone",
            "I avoid confrontation even when necessary",
            "I feel responsible for others' emotions",
            "I have trouble setting boundaries",
            "I often put others' needs before my own",
            "I find it hard to say 'no'",
            "I feel guilty when prioritizing myself"
        };

        int startIndex = 20;
        for (int i = 0; i < sampleQuestions.length; i++) {
            questions.add(createSection("H3", "Question " + (i + 1), startIndex + i * 2));
            questions.add(createSection("PARAGRAPH", sampleQuestions[i], startIndex + i * 2 + 1));
        }

        return questions;
    }

    private String generateAudienceList() {
        return "- Individuals questioning their relationship patterns\n" +
               "- People seeking self-awareness\n" +
               "- Those wanting to understand emotional dependencies\n" +
               "- Adults over 18 years old";
    }

    private String generateScoreRanges() {
        return "- 0-30: Low concern - Continue monitoring\n" +
               "- 31-60: Moderate - Consider professional consultation\n" +
               "- 61-100: High - Strongly recommend professional support";
    }

    private Article.Section createSection(String type, String content, int order) {
        Article.Section section = new Article.Section();
        section.setType(type);
        section.setContent(content);
        section.setOrderIndex(order);
        return section;
    }
}
