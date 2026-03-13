<template>
  <q-page class="q-pa-md">
    <div class="text-h4 q-mb-md">Topic Management</div>

    <q-card>
      <q-card-section>
        <q-form @submit="createTopic" class="q-gutter-md">
          <q-input v-model="newTopic.title" label="Topic Title" filled :rules="[val => !!val || 'Required']" />
          <q-select
            v-model="newTopic.category"
            :options="categories"
            label="Category"
            filled
            :rules="[val => !!val || 'Required']"
          />
          <q-input v-model="newTopic.primaryKeyword" label="Primary Keyword" filled />
          <q-btn type="submit" color="primary" label="Create Topic" />
        </q-form>
      </q-card-section>
    </q-card>

    <q-card class="q-mt-md">
      <q-card-section>
        <div class="text-h6">Pending Topics</div>
        <TopicList />
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useQuasar } from 'quasar';
import TopicList from '../components/TopicList.vue';

const $q = useQuasar();

const newTopic = ref({
  title: '',
  category: '',
  primaryKeyword: ''
});

const categories = ['PSYCHOLOGY_TEST', 'ART_THERAPY', 'RECOMMENDATION_LIST'];

const createTopic = async () => {
  try {
    const response = await fetch('/api/topics', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: newTopic.value.title,
        category: newTopic.value.category,
        primaryKeyword: newTopic.value.primaryKeyword
      })
    });

    if (response.ok) {
      $q.notify({ type: 'positive', message: 'Topic created!' });
      newTopic.value = { title: '', category: '', primaryKeyword: '' };
    } else {
      throw new Error('Failed to create topic');
    }
  } catch (error) {
    $q.notify({ type: 'negative', message: 'Failed to create topic' });
  }
};
</script>
