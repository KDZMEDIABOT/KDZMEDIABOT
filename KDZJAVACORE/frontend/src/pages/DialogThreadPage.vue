<template>
  <q-page class="column full-height">
    <!-- Thread header -->
    <q-bar class="bg-primary text-white q-py-sm">
      <div class="q-px-md text-weight-bold text-subtitle1 ellipsis">
        {{ dialogStore.currentThread?.title || 'Conversation' }}
      </div>
      <q-space />
      <q-btn flat dense icon="close" @click="onClose" label="Close" size="sm" />
    </q-bar>

    <!-- Messages -->
    <q-scroll-area ref="scrollAreaRef" class="col q-pa-md" style="background-color: #f5f5f5;">
      <!-- Empty state when no thread selected -->
      <div v-if="!dialogStore.currentThread" class="flex flex-center full-height">
        <div class="text-center text-grey-6">
          <q-icon name="chat_bubble" size="4em" />
          <div class="text-h6 q-mt-sm">Select a thread</div>
          <div class="text-body2">Choose an existing conversation or create a new one from the sidebar.</div>
        </div>
      </div>

      <!-- Loading state -->
      <div v-else-if="dialogStore.isLoading && dialogStore.messages.length === 0" class="flex flex-center full-height">
        <div class="text-center text-grey-6">
          <q-spinner-dots color="primary" size="3em" />
          <div class="text-body2 q-mt-sm">Loading messages...</div>
        </div>
      </div>

      <template v-else>
        <div v-for="msg in dialogStore.messages" :key="msg.id" class="q-mb-md">
          <div
            :class="[
              'row',
              msg.role === 'user' ? 'justify-end' : 'justify-start'
            ]"
          >
            <div
              :class="[
                'q-pa-md rounded-borders',
                msg.role === 'user'
                  ? 'bg-primary text-white'
                  : 'bg-white text-dark shadow-1'
              ]"
              style="max-width: 80%; min-width: 120px;"
            >
              <div class="text-caption text-weight-bold q-mb-xs" :class="msg.role === 'user' ? 'text-white' : 'text-grey-7'">
                {{ msg.role === 'user' ? 'You' : 'AI' }}
              </div>
              <div class="text-body2" style="white-space: pre-wrap;">{{ msg.content }}</div>
              <div v-if="msg.toolName" class="text-caption q-mt-xs" :class="msg.role === 'user' ? 'text-blue-2' : 'text-grey-6'">
                Tool: {{ msg.toolName }}
              </div>
            </div>
          </div>
        </div>

      <!-- Loading indicator -->
      <div v-if="dialogStore.isSending" class="row justify-start q-mt-md">
        <div class="q-pa-md rounded-borders bg-white shadow-1" style="max-width: 80%;">
          <q-spinner-dots color="primary" size="2em" />
          <span class="q-ml-sm text-grey-6">AI is thinking...</span>
        </div>
      </div>
      </template>
    </q-scroll-area>

    <!-- Input -->
    <q-separator />
    <div class="q-pa-sm bg-white">
      <q-input
        v-model="newMessage"
        placeholder="Type a message..."
        outlined
        dense
        class="full-width"
        @keyup.enter="send"
        :disable="dialogStore.isSending || !dialogStore.currentThread"
      >
        <template v-slot:after>
          <q-btn
            color="primary"
            icon="send"
            @click="send"
            :disable="!newMessage.trim() || dialogStore.isSending"
            round
            dense
          />
        </template>
      </q-input>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue';
import { useDialogStore } from '../stores/dialog';
import { useRoute, useRouter } from 'vue-router';

const dialogStore = useDialogStore();
const route = useRoute();
const router = useRouter();

const newMessage = ref('');
const scrollAreaRef = ref<any>(null);

function send() {
  const content = newMessage.value.trim();
  if (!content || !dialogStore.currentThread) return;
  newMessage.value = '';
  dialogStore.sendMessage(content);
}

function onClose() {
  dialogStore.clearCurrentThread();
  router.push('/');
}

// Auto-scroll to bottom when messages change
watch(
  () => dialogStore.messages.length,
  async () => {
    await nextTick();
    if (scrollAreaRef.value) {
      scrollAreaRef.value.setScrollPosition('vertical', 999999, 300);
    }
  }
);

// Load thread from route param
watch(
  () => route.params.id,
  async (id) => {
    if (id) {
      await dialogStore.selectThread(Number(id));
    }
  },
  { immediate: true }
);
</script>

<style scoped>
.full-height {
  height: 100vh;
  max-height: 100vh;
}

.rounded-borders {
  border-radius: 12px;
}
</style>
