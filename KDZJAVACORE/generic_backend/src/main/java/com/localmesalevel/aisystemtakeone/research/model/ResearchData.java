package com.localmesalevel.aisystemtakeone.research.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "research_data")
public class ResearchData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long topicId;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @ElementCollection
    @CollectionTable(name = "research_sources", joinColumns = @JoinColumn(name = "research_id"))
    private List<Source> sources;

    @ElementCollection
    @CollectionTable(name = "research_citations", joinColumns = @JoinColumn(name = "research_id"))
    private List<String> citations;

    @Column
    private String expertQuotes;

    @Column
    private String statistics;

    @Column
    private LocalDateTime createdAt = LocalDateTime.now();

    @Embeddable
    public static class Source {
        private String url;
        private String title;
        private String author;
        private String year;

        public Source() {}
        public Source(String url, String title, String author, String year) {
            this.url = url;
            this.title = title;
            this.author = author;
            this.year = year;
        }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public String getYear() { return year; }
        public void setYear(String year) { this.year = year; }
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTopicId() { return topicId; }
    public void setTopicId(Long topicId) { this.topicId = topicId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public List<Source> getSources() { return sources; }
    public void setSources(List<Source> sources) { this.sources = sources; }
    public List<String> getCitations() { return citations; }
    public void setCitations(List<String> citations) { this.citations = citations; }
    public String getExpertQuotes() { return expertQuotes; }
    public void setExpertQuotes(String expertQuotes) { this.expertQuotes = expertQuotes; }
    public String getStatistics() { return statistics; }
    public void setStatistics(String statistics) { this.statistics = statistics; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
