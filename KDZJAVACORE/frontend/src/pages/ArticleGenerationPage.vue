<template>
  <q-page class="q-pa-md">
    <div class="text-h5 q-mb-md">Generate Article</div>

    <q-card flat bordered>
      <q-card-section>
        <q-form class="q-gutter-md" @submit.prevent="runGeneration">
          <q-input
            v-model="form.topic"
            label="Article topic"
            filled
            :rules="[(v) => !!v || 'Topic is required']"
          />

          <q-select
            v-model="form.category"
            :options="categoryOptions"
            label="Content type"
            filled
            emit-value
            map-options
          />

          <q-input v-model="form.primaryKeyword" label="Primary keyword (optional)" filled />

          <div class="row items-center q-gutter-sm">
            <q-btn type="submit" color="primary" label="Run Generation" :loading="running" />
            <q-btn flat color="grey-7" label="Reset" @click="resetForm" :disable="running" />
          </div>
        </q-form>
      </q-card-section>
    </q-card>

    <q-card v-if="resultMessage" class="q-mt-md" flat bordered>
      <q-card-section>
        <div class="text-subtitle1">Result</div>
        <div class="q-mt-sm">{{ resultMessage }}</div>
        <div class="q-mt-md">
          <q-btn color="primary" outline label="Open Article Jobs List" to="/content/generation-jobs" />
        </div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useQuasar } from 'quasar';
import { useAuthStore } from '../stores/auth';

type Category = 'PSYCHOLOGY_TEST' | 'ART_THERAPY' | 'RECOMMENDATION_LIST';

const $q = useQuasar();
const auth = useAuthStore();
const running = ref(false);
const resultMessage = ref('');

const categoryOptions = [
  { label: 'Psychology test', value: 'PSYCHOLOGY_TEST' },
  { label: 'Art therapy', value: 'ART_THERAPY' },
  { label: 'Recommendation list', value: 'RECOMMENDATION_LIST' },
];

const form = ref({
  topic: '',
  category: 'ART_THERAPY' as Category,
  primaryKeyword: '',
});

async function runGeneration() {
  if (!form.value.topic.trim()) {
    $q.notify({ type: 'warning', message: 'Please enter a topic' });
    return;
  }

  running.value = true;
  resultMessage.value = '';
  try {
    const response = await fetch('/api/articles/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        topic: form.value.topic.trim(),
        category: form.value.category,
        primaryKeyword: form.value.primaryKeyword.trim(),
        requesterRole: auth.currentUser?.role || 'unknown',
        requesterUserId: auth.currentUser?.id || '',
      }),
    });

    if (!response.ok) {
      throw new Error('Generation failed');
    }

    const payload = await response.json();
    const generatedId = payload.jobId ? ` (job #${payload.jobId})` : '';
    resultMessage.value = payload.message || `Generation started for topic: ${form.value.topic.trim()}${generatedId}`;
    $q.notify({ type: 'positive', message: 'Generation started' });
  } catch {
    $q.notify({ type: 'negative', message: 'Failed to run generation' });
  } finally {
    running.value = false;
  }
}

function resetForm() {
  form.value = {
    topic: '',
    category: 'ART_THERAPY',
    primaryKeyword: '',
  };
  resultMessage.value = '';
}
</script>
