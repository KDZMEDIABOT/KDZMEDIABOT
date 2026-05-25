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
                <div v-if="editingMessageId !== msg.id" class="text-body2" style="white-space: pre-wrap;">{{ msg.content }}</div>
                <div v-if="msg.toolName" class="text-caption q-mt-xs" :class="msg.role === 'user' ? 'text-blue-2' : 'text-grey-6'">
                  Tool: {{ msg.toolName }}
                </div>
                <!-- File attachments -->
                <div v-if="msg.attachedFiles && msg.attachedFiles.length" class="q-mt-sm row q-gutter-x-xs items-center">
                  <q-chip
                    v-for="f in msg.attachedFiles"
                    :key="'file-' + f.id"
                    dense
                    size="sm"
                    color="primary"
                    text-color="white"
                    icon="attach_file"
                    :label="f.fileName"
                    class="q-ma-none"
                  />
                </div>
                <!-- Workspace attachments -->
                <div v-if="msg.attachedWorkspaces && msg.attachedWorkspaces.length" class="q-mt-xs row q-gutter-x-xs items-center">
                  <q-chip
                    v-for="w in msg.attachedWorkspaces"
                    :key="'ws-' + w.id"
                    dense
                    size="sm"
                    color="secondary"
                    text-color="white"
                    icon="folder"
                    :label="w.name"
                    class="q-ma-none"
                  />
                </div>
                <!-- Action buttons for every message -->
                <div class="q-mt-sm row" :class="msg.role === 'user' ? 'justify-end' : 'justify-start'" style="gap: 4px;">
                  <q-btn
                    v-if="editingMessageId !== msg.id"
                    size="xs"
                    flat
                    dense
                    color="grey-6"
                    icon="edit"
                    title="Edit"
                    @click="startEdit(msg)"
                  />
                  <q-btn
                    v-if="editingMessageId === msg.id"
                    size="xs"
                    flat
                    dense
                    color="positive"
                    icon="check"
                    title="Save"
                    @click="saveEdit(msg.id)"
                  />
                  <q-btn
                    v-if="editingMessageId === msg.id"
                    size="xs"
                    flat
                    dense
                    color="negative"
                    icon="close"
                    title="Cancel"
                    @click="cancelEdit"
                  />
                  <q-btn
                    size="xs"
                    flat
                    dense
                    color="grey-6"
                    icon="delete"
                    title="Delete"
                    @click="deleteMessage(msg.id)"
                  />
                  <!-- Retry button for failed user messages -->
                  <q-btn
                    v-if="msg.role === 'user' && hasErrorReply(msg.id)"
                    size="xs"
                    color="negative"
                    icon="refresh"
                    label="Retry"
                    :loading="dialogStore.isSending"
                    @click="retry(msg.id)"
                    dense
                  />                </div>
                <!-- Inline edit form -->
                <div v-if="editingMessageId === msg.id" class="q-mt-sm">
                  <q-input
                    v-model="editingContent"
                    type="textarea"
                    autogrow
                    outlined
                    dense
                    class="q-mb-xs"
                  />
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
      <!-- Selected workspace chips -->
      <div v-if="selectedWorkspaces.length" class="row q-gutter-x-sm q-mb-xs">
        <q-chip
          v-for="w in selectedWorkspaces"
          :key="w.id"
          removable
          dense
          color="secondary"
          text-color="white"
          :label="w.name"
          @remove="removeWorkspace(w)"
        />
      </div>
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
          v-model="selectedWorkspaceModel"
          label="Attach workspace..."
          :options="workspaceStore.workspaces"
          option-value="id"
          option-label="name"
          outlined
          dense
          style="min-width: 160px"
          @update:model-value="onWorkspaceSelected"
          clearable
        />
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
        <!-- Clip icon for native file upload to default workspace -->
        <q-btn
          color="accent"
          icon="attach_file"
          @click="triggerFileUpload"
          :disable="dialogStore.isSending || !dialogStore.currentThread"
          round
          dense
          title="Attach files from computer"
        />
        <q-btn
          color="primary"
          icon="send"
          @click="send"
          :disable="!newMessage.trim() || dialogStore.isSending"
          round
          dense
        />
        <input
          ref="fileInputRef"
          type="file"
          multiple
          style="display: none"
          @change="handleFileUpload"
        />
      </div>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted } from 'vue';
import { useDialogStore } from '../stores/dialog';
import { useWorkspaceStore, type WorkspaceFile, type Workspace } from '../stores/workspace';
import { useRoute, useRouter } from 'vue-router';

const dialogStore = useDialogStore();
const workspaceStore = useWorkspaceStore();
const route = useRoute();
const router = useRouter();

const newMessage = ref('');
const scrollAreaRef = ref<any>(null);
const selectedFiles = ref<WorkspaceFile[]>([]);
const selectedFileModel = ref<WorkspaceFile | null>(null);
const selectedWorkspaces = ref<Workspace[]>([]);
const selectedWorkspaceModel = ref<Workspace | null>(null);
const fileInputRef = ref<HTMLInputElement | null>(null);
const editingMessageId = ref<number | null>(null);
const editingContent = ref('');

onMounted(async () => {
  await workspaceStore.fetchWorkspaces();
  await workspaceStore.fetchAllFiles();
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

function onWorkspaceSelected(ws: Workspace | null) {
  if (!ws) return;
  if (!selectedWorkspaces.value.some((w) => w.id === ws.id)) {
    selectedWorkspaces.value.push(ws);
  }
  selectedWorkspaceModel.value = null;
}

function removeWorkspace(ws: Workspace) {
  selectedWorkspaces.value = selectedWorkspaces.value.filter((w) => w.id !== ws.id);
}

function startEdit(msg: typeof dialogStore.messages[0]) {
  editingMessageId.value = msg.id;
  editingContent.value = msg.content;
}

function cancelEdit() {
  editingMessageId.value = null;
  editingContent.value = '';
}

async function saveEdit(messageId: number) {
  if (!dialogStore.currentThread) return;
  await dialogStore.editMessage(messageId, dialogStore.currentThread.id, editingContent.value);
  editingMessageId.value = null;
  editingContent.value = '';
}

async function deleteMessage(messageId: number) {
  if (!dialogStore.currentThread) return;
  await dialogStore.deleteMessage(messageId, dialogStore.currentThread.id);
}

function triggerFileUpload() {
  fileInputRef.value?.click();
}

async function handleFileUpload(e: Event) {
  const input = e.target as HTMLInputElement;
  if (!input.files || input.files.length === 0) return;
  for (const file of Array.from(input.files)) {
    const uploaded = await workspaceStore.uploadFileToDefault(file);
    if (uploaded) {
      selectedFiles.value.push(uploaded);
    }
  }
  input.value = '';
}

function send(event?: KeyboardEvent) {
  if (event && event instanceof KeyboardEvent && !event.ctrlKey) {
    return;
  }
  const content = newMessage.value.trim();
  if (!content || !dialogStore.currentThread) return;
  const fileIds = selectedFiles.value.map((f) => f.id);
  const workspaceIds = selectedWorkspaces.value.map((w) => w.id);
  selectedFiles.value = [];
  selectedWorkspaces.value = [];
  newMessage.value = '';
  dialogStore.sendMessage(content, fileIds, workspaceIds);
}

function hasErrorReply(messageId: number): boolean {
  return dialogStore.messages.some(
    (m) => m.isReplyTo === messageId && m.error === true
  );
}

async function retry(messageId: number) {
  if (!dialogStore.currentThread) return;
  await dialogStore.retryMessage(messageId, dialogStore.currentThread.id);
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
