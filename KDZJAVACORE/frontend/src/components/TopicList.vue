<template>
  <q-list bordered separator>
    <q-item v-for="topic in topics" :key="topic.id">
      <q-item-section>
        <q-item-label>{{ topic.title }}</q-item-label>
        <q-item-label caption>{{ topic.category }} - {{ topic.status }}</q-item-label>
      </q-item-section>
      <q-item-section side>
        <q-btn-group flat>
          <q-btn color="positive" label="Approve" @click="approveTopic(topic.id)" />
          <q-btn color="negative" label="Reject" @click="rejectTopic(topic.id)" />
        </q-btn-group>
      </q-item-section>
    </q-item>
  </q-list>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';

interface Topic {
  id: number;
  title: string;
  category: string;
  status: string;
}

const topics = ref<Topic[]>([]);

const fetchTopics = async () => {
  try {
    const response = await fetch('/api/topics');
    const data = await response.json();
    topics.value = data;
  } catch (error) {
    console.error('Failed to fetch topics:', error);
  }
};

const approveTopic = async (id: number) => {
  try {
    await fetch(`/api/topics/${id}/approve`, { method: 'POST' });
    await fetchTopics();
  } catch (error) {
    console.error('Failed to approve topic:', error);
  }
};

const rejectTopic = async (id: number) => {
  try {
    await fetch(`/api/topics/${id}/reject?reason=not+suitable`, { method: 'POST' });
    await fetchTopics();
  } catch (error) {
    console.error('Failed to reject topic:', error);
  }
};

onMounted(fetchTopics);
</script>
