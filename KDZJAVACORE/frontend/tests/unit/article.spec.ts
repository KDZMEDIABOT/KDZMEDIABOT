import { describe, it, expect } from 'vitest';
import { ref, computed } from 'vue';

// Simple article store for testing
function useArticleStore() {
  const articles = ref<{ id: number; title: string; topic: string }[]>([]);
  const isLoading = ref(false);

  const articleCount = computed(() => articles.value.length);

  async function generateArticle(topic: string): Promise<boolean> {
    isLoading.value = true;
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 100));
      articles.value.push({
        id: articles.value.length + 1,
        title: `Article about ${topic}`,
        topic
      });
      return true;
    } finally {
      isLoading.value = false;
    }
  }

  function clearArticles() {
    articles.value = [];
  }

  return {
    articles,
    isLoading,
    articleCount,
    generateArticle,
    clearArticles
  };
}

describe('Article Store', () => {
  it('starts with empty articles', () => {
    const store = useArticleStore();
    expect(store.articles.value).toHaveLength(0);
    expect(store.articleCount.value).toBe(0);
  });

  it('adds article on generate', async () => {
    const store = useArticleStore();
    const result = await store.generateArticle('AI content');
    expect(result).toBe(true);
    expect(store.articles.value).toHaveLength(1);
    expect(store.articleCount.value).toBe(1);
    expect(store.articles.value[0].topic).toBe('AI content');
  });

  it('clears all articles', async () => {
    const store = useArticleStore();
    await store.generateArticle('Topic 1');
    await store.generateArticle('Topic 2');
    store.clearArticles();
    expect(store.articles.value).toHaveLength(0);
    expect(store.articleCount.value).toBe(0);
  });
});
