<template>
  <q-page class="column full-height" style="flex-wrap: nowrap;">
    <!-- Thread header -->
    <q-bar class="bg-primary text-white q-py-sm" style="width: 100%;">
      <div class="q-px-md text-weight-bold text-subtitle1 ellipsis" style="width: 100%;">
        {{ dialogStore.currentThread?.title || 'Conversation' }}
      </div>
      <q-space />
    </q-bar>

    <!-- Messages -->
    <div ref="scrollAreaRef" class="q-pa-md" style="background-color: #f5f5f5; width: 100%">
      <div>
        <div v-if="!dialogStore.currentThread" class="flex flex-center full-height">
          <div class="text-center text-grey-6">
            <q-icon name="chat_bubble" size="4em" />
            <div class="text-h6 q-mt-sm">Select a thread</div>
            <div class="text-body2">Choose an existing conversation or create a new one from the sidebar.</div>
          </div>
        </div>

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
                  {{ msg.role }}</div>
                <div class="text-body2" style="white-space: pre-wrap;">{{ msg.content }}</div>
                <div v-if="msg.toolName" class="text-caption q-mt-xs" :class="msg.role === 'user' ? 'text-blue-2' : 'text-grey-6'">
                  Tool: {{ msg.toolName }}
                </div>
                <!-- File attachments -->
                <div v-if="msg.attachedFiles && msg.attachedFiles.length" class="text-caption q-mt-xs" :class="msg.role === 'user' ? 'text-blue-2' : 'text-grey-6'">
                  Files: {{ msg.attachedFiles.map(f => f.fileName).join(', ') }}
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
      </div>
    </div>

    <!-- Input -->
    <q-separator />
    <div class="q-pa-sm bg-white column" style="width: 100%;">
      <!-- Selected file chips -->
      <div v-if="selectedFiles.length" class="row q-gutter-x-sm q-mb-xs">
        <q-chip
          v-for="f in selectedFiles"
          :key="f.id"
          removable
          dense
          color="primary"
          text-color="white"
          :label="f.fileName"
          @remove="removeFile(f)"
        />
      </div>
      <div class="row items-center q-gutter-x-sm">
        <q-select
          v-model="selectedFileModel"
          label="Attach files..."
          :options="workspaceStore.files"
          option-value="id"
          option-label="fileName"
          use-input
          outlined
          dense
          style="min-width: 200px"
          @update:model-value="onFileSelected"
          clearable
        />
        <q-input
          v-model="newMessage"
          placeholder="Type a message..."
          type="textarea"
          autogrow
          outlined
          dense
          class="col"
          @keydown.enter="send"
          :disable="dialogStore.isSending || !dialogStore.currentThread"
        />
        <q-btn
          color="primary"
          icon="send"
          @click="send"
          :disable="!newMessage.trim() || dialogStore.isSending"
          round
          dense
        />
      </div>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted } from 'vue';
import { useDialogStore } from '../stores/dialog';
import { useWorkspaceStore, type WorkspaceFile } from '../stores/workspace';
import { useRoute, useRouter } from 'vue-router';

const dialogStore = useDialogStore();
const workspaceStore = useWorkspaceStore();
const route = useRoute();
const router = useRouter();

const newMessage = ref('');
const scrollAreaRef = ref<any>(null);
const selectedFiles = ref<WorkspaceFile[]>([]);
const selectedFileModel = ref<WorkspaceFile | null>(null);

onMounted(async () => {
  await workspaceStore.fetchWorkspaces();
  if (workspaceStore.currentWorkspace) {
    await workspaceStore.fetchFiles(workspaceStore.currentWorkspace.id);
  }
});

function onFileSelected(file: WorkspaceFile | null) {
  if (!file) return;
  if (!selectedFiles.value.some((f) => f.id === file.id)) {
    selectedFiles.value.push(file);
  }
  selectedFileModel.value = null;
}

function removeFile(file: WorkspaceFile) {
  selectedFiles.value = selectedFiles.value.filter((f) => f.id !== file.id);
}

function send(event?: KeyboardEvent) {
  if (event && !event.ctrlKey) {
    return;
  }
  const content = newMessage.value.trim();
  if (!content || !dialogStore.currentThread) return;
  const fileIds = selectedFiles.value.map((f) => f.id);
  selectedFiles.value = [];
  newMessage.value = '';
  dialogStore.sendMessage(content, fileIds);
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
      scrollAreaRef.value.scrollTop = scrollAreaRef.value.scrollHeight;
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
