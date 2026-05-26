<template>
  <q-layout view="hHh lpR fFf">
    <q-header elevated>
      <q-toolbar>
        <q-btn flat dense round icon="menu" aria-label="Menu" @click="toggleLeftDrawer" />
        <q-toolbar-title>
          AI System
        </q-toolbar-title>
        <template v-if="auth.isLoggedIn">
          <div class="q-mr-md text-caption">
            {{ auth.currentUser?.name }} ({{ auth.currentUser?.role }})
          </div>
        </template>
      </q-toolbar>
    </q-header>

    <q-drawer v-model="leftDrawerOpen" show-if-above bordered>
      <q-list>
        <q-item clickable v-ripple to="/" exact @click="leftDrawerOpen = false">
          <q-item-section avatar>
            <q-icon name="home" />
          </q-item-section>
          <q-item-section>Home</q-item-section>
        </q-item>

        <q-item v-if="auth.isAdmin" clickable v-ripple to="/admin/users" @click="leftDrawerOpen = false">
          <q-item-section avatar>
            <q-icon name="group" />
          </q-item-section>
          <q-item-section>Users</q-item-section>
        </q-item>

        <q-item v-if="auth.isAdmin" clickable v-ripple to="/admin/running-kie-processes" @click="leftDrawerOpen = false">
          <q-item-section avatar>
            <q-icon name="account_tree" />
          </q-item-section>
          <q-item-section>Running KIE Processes</q-item-section>
        </q-item>

        <q-item v-if="canManageContent" clickable v-ripple to="/content/articles" @click="leftDrawerOpen = false">
          <q-item-section avatar>
            <q-icon name="article" />
          </q-item-section>
          <q-item-section>Articles List</q-item-section>
        </q-item>

        <q-item v-if="canManageContent" clickable v-ripple to="/content/generate" @click="leftDrawerOpen = false">
          <q-item-section avatar>
            <q-icon name="auto_awesome" />
          </q-item-section>
          <q-item-section>Create Article</q-item-section>
        </q-item>

        <q-item v-if="auth.isLoggedIn" clickable v-ripple to="/content/generation-jobs" @click="leftDrawerOpen = false">
          <q-item-section avatar>
            <q-icon name="list_alt" />
          </q-item-section>
          <q-item-section>Generation Jobs</q-item-section>
        </q-item>

        <q-item v-if="!auth.isLoggedIn" clickable v-ripple to="/login" @click="leftDrawerOpen = false">
          <q-item-section avatar>
            <q-icon name="login" />
          </q-item-section>
          <q-item-section>Sign In</q-item-section>
        </q-item>

        <q-item v-if="auth.isLoggedIn" clickable v-ripple to="/workspaces" @click="leftDrawerOpen = false">
          <q-item-section avatar>
            <q-icon name="folder" />
          </q-item-section>
          <q-item-section>Workspaces</q-item-section>
        </q-item>

        <template v-if="auth.isLoggedIn">
          <q-separator spaced />
          <q-item-label header class="text-uppercase text-caption text-grey-7 q-pa-sm">
            Dialog Threads
          </q-item-label>

          <q-item clickable v-ripple @click="onNewThread">
            <q-item-section avatar>
              <q-icon name="add_circle" color="primary" />
            </q-item-section>
            <q-item-section class="text-primary">New Thread</q-item-section>
          </q-item>

          <q-item
            v-for="thread in dialogStore.paginatedThreads"
            :key="thread.id"
            clickable v-ripple
            :to="`/dialogs/${thread.id}`"
            :active="currentThreadId === thread.id"
            @click=""
          >
            <q-item-section avatar>
              <q-icon name="chat_bubble" />
            </q-item-section>
            <q-item-section>
              <q-item-label class="ellipsis" style="max-width: 180px;">{{ thread.title }}</q-item-label>
              <q-item-label caption>{{ formatDate(thread.lastMessageAt) }}</q-item-label>
            </q-item-section>
            <q-item-section side>
              <q-btn
                flat round dense icon="edit" size="sm"
                @click.prevent.stop="onRenameThread(thread.id, thread.title)"
              />
              <q-btn
                flat round dense icon="close" size="sm"
                @click.prevent.stop="onDeleteThread(thread.id)"
              />
            </q-item-section>
          </q-item>

          <div class="q-pa-sm flex flex-center">
            <q-pagination
              v-model="dialogStore.threadsPage"
              :max="dialogStore.totalThreadPages"
              direction-links
              boundary-links
              :max-pages="6"
              size="sm"
            />
          </div>

          <q-separator spaced />
        </template>

        <q-item v-if="auth.isLoggedIn" clickable v-ripple to="/settings" @click="leftDrawerOpen = false">
          <q-item-section avatar>
            <q-icon name="settings" />
          </q-item-section>
          <q-item-section>Settings</q-item-section>
        </q-item>

        <q-item v-if="auth.isLoggedIn" clickable v-ripple @click="onLogoutFromDrawer">
          <q-item-section avatar>
            <q-icon name="logout" />
          </q-item-section>
          <q-item-section>Logout</q-item-section>
        </q-item>
      </q-list>
    </q-drawer>

    <q-page-container>
      <router-view />
    </q-page-container>

    <!-- New Thread Dialog -->
    <q-dialog v-model="showNewThreadDialog" persistent>
      <q-card style="min-width: 300px; max-width: 90vw;">
        <q-card-section>
          <div class="text-h6">New Thread</div>
        </q-card-section>
        <q-card-section>
          <q-input
            v-model="newThreadTitle"
            label="Thread title"
            outlined
            dense
            autofocus
            @keyup.enter="createNewThread"
          />
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="Cancel" color="primary" v-close-popup />
          <q-btn flat label="Create" color="primary" @click="createNewThread" :disable="!newThreadTitle.trim()" />
        </q-card-actions>
      </q-card>
    </q-dialog>

    <!-- Rename Thread Dialog -->
    <q-dialog v-model="showRenameThreadDialog" persistent>
      <q-card style="min-width: 300px; max-width: 90vw;">
        <q-card-section>
          <div class="text-h6">Rename Thread</div>
        </q-card-section>
        <q-card-section>
          <q-input
            v-model="renameThreadTitle"
            label="New title"
            outlined
            dense
            autofocus
            @keyup.enter="confirmRenameThread"
          />
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="Cancel" color="primary" v-close-popup />
          <q-btn flat label="Rename" color="primary" @click="confirmRenameThread" :disable="!renameThreadTitle.trim()" />
        </q-card-actions>
      </q-card>
    </q-dialog>

    <!-- Delete Thread Confirmation Dialog -->
    <q-dialog v-model="showDeleteThreadDialog" persistent>
      <q-card style="min-width: 300px; max-width: 90vw;">
        <q-card-section>
          <div class="text-h6">Delete Thread</div>
        </q-card-section>
        <q-card-section>
          <p>Are you sure you want to delete this thread? This action cannot be undone.</p>
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="Cancel" color="primary" v-close-popup />
          <q-btn flat label="Delete" color="negative" @click="confirmDeleteThread" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-layout>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { useDialogStore } from '../stores/dialog';

const auth = useAuthStore();
const dialogStore = useDialogStore();
const leftDrawerOpen = ref(false);
const canManageContent = computed(() => auth.isAdmin || auth.isMaintainer);
const route = useRoute();
const router = useRouter();
const currentThreadId = ref<number | null>(null);

watch(
  () => route.params.id,
  (id) => {
    currentThreadId.value = id ? Number(id) : null;
  },
  { immediate: true }
);

function toggleLeftDrawer() {
  leftDrawerOpen.value = !leftDrawerOpen.value;
}

async function onLogout() {
  await auth.logout();
}

async function onLogoutFromDrawer() {
  leftDrawerOpen.value = false;
  await onLogout();
}

// Dialog thread functions
const newThreadTitle = ref('');
const showNewThreadDialog = ref(false);

async function onNewThread() {
  newThreadTitle.value = '';
  showNewThreadDialog.value = true;
}

async function createNewThread() {
  const title = newThreadTitle.value.trim();
  console.log('[LAYOUT] createNewThread() title=', title);
  if (!title) return;
  const thread = await dialogStore.createThread(title);
  console.log('[LAYOUT] createNewThread() dialogStore.createThread returned:', thread);
  if (thread) {
    newThreadTitle.value = '';
    showNewThreadDialog.value = false;
    router.push(`/dialogs/${thread.id}`);
  }
}

const showDeleteThreadDialog = ref(false);
const threadToDeleteId = ref<number | null>(null);

function onDeleteThread(id: number) {
  threadToDeleteId.value = id;
  showDeleteThreadDialog.value = true;
}

async function confirmDeleteThread() {
  if (threadToDeleteId.value != null) {
    await dialogStore.deleteThread(threadToDeleteId.value);
  }
  showDeleteThreadDialog.value = false;
  threadToDeleteId.value = null;
}

// Rename thread
const showRenameThreadDialog = ref(false);
const renameThreadTitle = ref('');
const threadToRenameId = ref<number | null>(null);

function onRenameThread(id: number, currentTitle: string) {
  threadToRenameId.value = id;
  renameThreadTitle.value = currentTitle;
  showRenameThreadDialog.value = true;
}

async function confirmRenameThread() {
  const title = renameThreadTitle.value.trim();
  if (!title || threadToRenameId.value == null) return;
  await dialogStore.renameThread(threadToRenameId.value, title);
  showRenameThreadDialog.value = false;
  threadToRenameId.value = null;
}

function formatDate(dateStr: string | null) {
  if (!dateStr) return 'New';
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

watch(
  () => auth.isLoggedIn,
  (isLoggedIn) => {
    if (isLoggedIn) {
      dialogStore.fetchThreads();
    }
  },
  { immediate: true }
);
</script>
