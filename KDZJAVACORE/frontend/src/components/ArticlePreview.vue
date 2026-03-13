<template>
  <q-card v-if="article" class="article-preview">
    <q-card-section>
      <div class="text-h4">{{ article.title }}</div>
      <div class="text-caption q-mt-sm">{{ article.metaDescription }}</div>
    </q-card-section>

    <q-separator />

    <q-card-section>
      <div v-html="renderedContent"></div>
    </q-card-section>

    <q-separator />

    <q-card-section>
      <div class="text-subtitle2">FAQ Section</div>
      <q-list v-if="article.faqSection">
        <q-expansion-item
          v-for="(item, index) in article.faqSection.items"
          :key="index"
          :label="item.question"
        >
          <q-card>
            <q-card-section>{{ item.answer }}</q-card-section>
          </q-card>
        </q-expansion-item>
      </q-list>
    </q-card-section>

    <q-card-actions align="right">
      <q-btn color="negative" label="Request Revision" @click="requestRevision" />
      <q-btn color="positive" label="Approve & Publish" @click="approve" />
    </q-card-actions>

    <q-dialog v-model="revisionDialog">
      <q-card style="min-width: 350px">
        <q-card-section>
          <div class="text-h6">Request Revision</div>
        </q-card-section>
        <q-card-section>
          <q-input
            v-model="revisionFeedback"
            label="Feedback"
            type="textarea"
            filled
          />
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="Cancel" v-close-popup />
          <q-btn color="primary" label="Submit" @click="submitRevision" v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-card>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useQuasar } from 'quasar';

const $q = useQuasar();

interface ArticleSection {
  type: string;
  content: string;
  orderIndex: number;
}

interface FaqItem {
  question: string;
  answer: string;
}

interface Article {
  id: number;
  title: string;
  metaDescription: string;
  content: string;
  sections: ArticleSection[];
  faqSection?: {
    items: FaqItem[];
  };
}

const article = ref<Article | null>(null);
const revisionDialog = ref(false);
const revisionFeedback = ref('');

const renderedContent = computed(() => {
  if (!article.value?.sections) return '';
  return article.value.sections
    .sort((a, b) => a.orderIndex - b.orderIndex)
    .map(s => {
      if (s.type === 'H2') return `<h2>${s.content}</h2>`;
      if (s.type === 'H3') return `<h3>${s.content}</h3>`;
      if (s.type === 'LIST') return `<ul>${s.content.split('\\n').map(i => `<li>${i}</li>`).join('')}</ul>`;
      if (s.type === 'QUOTE') return `<blockquote>${s.content}</blockquote>`;
      return `<p>${s.content}</p>`;
    })
    .join('');
});

const approve = async () => {
  if (!article.value) return;
  try {
    await fetch(`/api/articles/${article.value.id}/approve`, { method: 'POST' });
    $q.notify({ type: 'positive', message: 'Article approved!' });
  } catch (error) {
    $q.notify({ type: 'negative', message: 'Failed to approve article' });
  }
};

const requestRevision = () => {
  revisionDialog.value = true;
};

const submitRevision = async () => {
  if (!article.value) return;
  try {
    await fetch(`/api/articles/${article.value.id}/revision`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ feedback: revisionFeedback.value })
    });
    $q.notify({ type: 'positive', message: 'Revision requested!' });
    revisionFeedback.value = '';
  } catch (error) {
    $q.notify({ type: 'negative', message: 'Failed to request revision' });
  }
};
</script>
