<template>
  <q-page class="q-pa-md">
    <div class="row items-center justify-between q-mb-md">
      <div class="text-h5">Article Preview</div>
      <q-btn flat color="primary" icon="arrow_back" label="Back to list" to="/content/articles" />
    </div>

    <q-card v-if="article" flat bordered>
      <q-card-section>
        <div class="text-h4">{{ article.title }}</div>
        <div class="text-caption q-mt-sm">{{ article.metaDescription || 'No meta description' }}</div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div v-html="renderedContent"></div>
      </q-card-section>
    </q-card>

    <q-card v-else flat bordered>
      <q-card-section class="text-grey">Article not found.</q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useQuasar } from 'quasar';

interface ArticleSection {
  type: string;
  content: string;
  orderIndex: number;
}

interface Article {
  id: number;
  title: string;
  metaDescription?: string;
  content?: string;
  sections?: ArticleSection[];
}

const $q = useQuasar();
const route = useRoute();
const article = ref<Article | null>(null);

const renderedContent = computed(() => {
  if (!article.value) {
    return '';
  }
  if (!article.value.sections || article.value.sections.length === 0) {
    return `<p>${article.value.content || ''}</p>`;
  }

  return [...article.value.sections]
    .sort((a, b) => a.orderIndex - b.orderIndex)
    .map((section) => {
      if (section.type === 'H2') return `<h2>${section.content}</h2>`;
      if (section.type === 'H3') return `<h3>${section.content}</h3>`;
      if (section.type === 'LIST') {
        return `<ul>${section.content
          .split('\n')
          .filter(Boolean)
          .map((item) => `<li>${item}</li>`)
          .join('')}</ul>`;
      }
      if (section.type === 'QUOTE') return `<blockquote>${section.content}</blockquote>`;
      return `<p>${section.content}</p>`;
    })
    .join('');
});

async function loadArticle() {
  const id = Number(route.params.id);
  if (!Number.isFinite(id)) {
    return;
  }
  try {
    const response = await fetch('/api/articles');
    if (!response.ok) {
      throw new Error('Failed to load');
    }
    const items: Article[] = await response.json();
    article.value = items.find((item) => item.id === id) ?? null;
  } catch {
    $q.notify({ type: 'negative', message: 'Failed to load article preview' });
  }
}

onMounted(loadArticle);
</script>
