<template>
  <q-page class="q-pa-md">
    <div class="text-h4 q-mb-md">Article Review Queue</div>

    <div class="row q-col-gutter-md">
      <div class="col-12 col-md-4">
        <q-card>
          <q-card-section>
            <div class="text-h6">Articles Awaiting Review</div>
          </q-card-section>
          <q-list separator>
            <q-item
              v-for="article in pendingArticles"
              :key="article.id"
              clickable
              @click="selectArticle(article)"
              :active="selectedArticle?.id === article.id"
            >
              <q-item-section>
                <q-item-label>{{ article.title }}</q-item-label>
                <q-item-label caption>{{ article.category }}</q-item-label>
              </q-item-section>
            </q-item>
          </q-list>
        </q-card>
      </div>

      <div class="col-12 col-md-8">
        <ArticlePreview v-if="selectedArticle" :article="selectedArticle" />
        <q-card v-else>
          <q-card-section class="text-center text-grey">
            Select an article to review
          </q-card-section>
        </q-card>
      </div>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import ArticlePreview from '../components/ArticlePreview.vue';

interface Article {
  id: number;
  title: string;
  category: string;
  status: string;
}

const pendingArticles = ref<Article[]>([]);
const selectedArticle = ref<Article | null>(null);

const fetchPendingArticles = async () => {
  try {
    const response = await fetch('/api/articles/UNDER_REVIEW');
    pendingArticles.value = await response.json();
  } catch (error) {
    console.error('Failed to fetch articles:', error);
  }
};

const selectArticle = (article: Article) => {
  selectedArticle.value = article;
};

onMounted(fetchPendingArticles);
</script>
