<template>
  <q-page padding>
    <div class="row items-center q-mb-md">
      <q-btn flat dense icon="arrow_back" label="Workspaces" to="/workspaces" />
      <div class="text-h6 q-ml-md">{{ workspaceStore.currentWorkspace?.name || 'Workspace' }}</div>
    </div>

    <div class="row q-gutter-md q-mb-md">
      <q-file
        v-model="uploadFileModel"
        label="Upload file"
        outlined
        dense
        style="max-width: 300px"
        @update:model-value="onFileSelected"
      />
    </div>

    <q-list bordered separator v-if="workspaceStore.files.length > 0">
      <q-item v-for="f in workspaceStore.files" :key="f.id">
        <q-item-section avatar>
          <q-icon name="insert_drive_file" color="primary" />
        </q-item-section>
        <q-item-section>
          <q-item-label>{{ f.fileName }}</q-item-label>
          <q-item-label caption>{{ formatBytes(f.fileSize) }} — {{ formatDate(f.createdAt) }}</q-item-label>
        </q-item-section>
        <q-item-section side>
          <q-btn flat round dense icon="delete" size="sm" color="negative" @click="onDeleteFile(f.id)" />
        </q-item-section>
      </q-item>
    </q-list>

    <div v-else class="text-grey-6 q-mt-lg">
      <q-icon name="folder_open" size="4em" />
      <div class="text-h6 q-mt-sm">No files yet</div>
      <div class="text-body2">Upload files to attach them to messages in dialog threads.</div>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useWorkspaceStore } from '../stores/workspace';

const workspaceStore = useWorkspaceStore();
const route = useRoute();
const router = useRouter();
const workspaceId = Number(route.params.id);

const uploadFileModel = ref<File | null>(null);

onMounted(async () => {
  if (!workspaceStore.currentWorkspace || workspaceStore.currentWorkspace.id !== workspaceId) {
    await workspaceStore.fetchWorkspaces();
    await workspaceStore.selectWorkspace(workspaceId);
  }
});

async function onFileSelected(file: File | null) {
  if (!file || !workspaceId) return;
  await workspaceStore.uploadFile(workspaceId, file);
  uploadFileModel.value = null;
}

function onDeleteFile(fileId: number) {
  if (!workspaceId) return;
  workspaceStore.deleteFile(workspaceId, fileId);
}

function formatBytes(bytes: number) {
  if (!bytes) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

function formatDate(dateStr: string | null) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}
</script>
