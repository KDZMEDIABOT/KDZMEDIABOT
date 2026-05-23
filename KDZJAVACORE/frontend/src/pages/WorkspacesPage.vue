<template>
  <q-page padding>
    <div class="text-h6 q-mb-md">Workspaces</div>

    <div class="row q-gutter-md q-mb-md">
      <q-input
        v-model="newWorkspaceName"
        label="New workspace name"
        outlined
        dense
        style="max-width: 300px"
        @keyup.enter="onCreateWorkspace"
      />
      <q-btn color="primary" label="Create" :disable="!newWorkspaceName.trim()" @click="onCreateWorkspace" />
    </div>

    <q-list bordered separator v-if="workspaceStore.workspaces.length > 0">
      <q-item
        v-for="ws in workspaceStore.workspaces"
        :key="ws.id"
        clickable
        v-ripple
        @click="goToWorkspace(ws.id)"
      >
        <q-item-section avatar>
          <q-icon name="folder" color="primary" />
        </q-item-section>
        <q-item-section>
          <q-item-label>{{ ws.name }}</q-item-label>
          <q-item-label caption>{{ formatDate(ws.createdAt) }}</q-item-label>
        </q-item-section>
        <q-item-section side>
          <q-btn flat round dense icon="edit" size="sm" @click.stop="onRename(ws.id, ws.name)" />
          <q-btn flat round dense icon="delete" size="sm" color="negative" @click.stop="onDelete(ws.id)" />
        </q-item-section>
      </q-item>
    </q-list>

    <div v-else class="text-grey-6 q-mt-lg">
      <q-icon name="folder_open" size="4em" />
      <div class="text-h6 q-mt-sm">No workspaces yet</div>
      <div class="text-body2">Create a workspace above to start uploading files.</div>
    </div>

    <!-- Rename Dialog -->
    <q-dialog v-model="showRenameDialog" persistent>
      <q-card style="min-width: 300px">
        <q-card-section>
          <div class="text-h6">Rename Workspace</div>
        </q-card-section>
        <q-card-section>
          <q-input v-model="renameName" label="New name" outlined dense autofocus @keyup.enter="confirmRename" />
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="Cancel" color="primary" v-close-popup />
          <q-btn flat label="Rename" color="primary" @click="confirmRename" :disable="!renameName.trim()" v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>

    <!-- Delete Confirmation -->
    <q-dialog v-model="showDeleteDialog" persistent>
      <q-card style="min-width: 300px">
        <q-card-section>
          <div class="text-h6">Delete Workspace</div>
        </q-card-section>
        <q-card-section>Are you sure? This will also delete all files in the workspace.</q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="Cancel" color="primary" v-close-popup />
          <q-btn flat label="Delete" color="negative" @click="confirmDelete" v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useWorkspaceStore } from '../stores/workspace';

const workspaceStore = useWorkspaceStore();
const router = useRouter();

const newWorkspaceName = ref('');
const showRenameDialog = ref(false);
const renameName = ref('');
const renameId = ref<number | null>(null);
const showDeleteDialog = ref(false);
const deleteId = ref<number | null>(null);

onMounted(() => {
  workspaceStore.fetchWorkspaces();
});

function onCreateWorkspace() {
  const name = newWorkspaceName.value.trim();
  if (!name) return;
  workspaceStore.createWorkspace(name);
  newWorkspaceName.value = '';
}

function onRename(id: number, currentName: string) {
  renameId.value = id;
  renameName.value = currentName;
  showRenameDialog.value = true;
}

function confirmRename() {
  if (renameId.value != null && renameName.value.trim()) {
    workspaceStore.renameWorkspace(renameId.value, renameName.value.trim());
  }
  showRenameDialog.value = false;
  renameId.value = null;
}

function onDelete(id: number) {
  deleteId.value = id;
  showDeleteDialog.value = true;
}

function confirmDelete() {
  if (deleteId.value != null) {
    workspaceStore.deleteWorkspace(deleteId.value);
  }
  showDeleteDialog.value = false;
  deleteId.value = null;
}

function goToWorkspace(id: number) {
  router.push(`/workspaces/${id}`);
}

function formatDate(dateStr: string | null) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}
</script>
