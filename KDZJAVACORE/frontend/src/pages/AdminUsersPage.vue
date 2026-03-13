<template>
  <q-page class="q-pa-md">
    <div class="page-wrap q-mx-auto">
      <h1 class="text-h5 q-mb-md">User Management</h1>

      <div v-if="!auth.isLoggedIn" class="text-center q-mt-lg">
        <p class="text-body1 q-mb-md">Please sign in to access admin user management.</p>
        <q-btn color="primary" label="Sign In" to="/login" />
      </div>

      <div v-else-if="!auth.isAdmin" class="text-center q-mt-lg">
        <p class="text-body1 q-mb-md">This page is available to admin users only.</p>
        <q-btn color="primary" outline label="Back to Home" to="/" />
      </div>

      <div v-else>
        <div class="row q-col-gutter-sm q-mb-md items-end">
          <div class="col-12 col-md-5">
            <q-input
              v-model="searchUsername"
              label="Search by username"
              outlined
              dense
              @keyup.enter="onSearch"
            />
          </div>
          <div class="col-auto">
            <q-btn color="primary" label="Search" @click="onSearch" :loading="loading" />
          </div>
          <div class="col-auto">
            <q-btn flat color="primary" label="Reset" @click="onResetSearch" :disable="loading" />
          </div>
          <div class="col">
            <div class="row justify-end">
              <q-btn color="primary" icon="add" label="Create User" @click="openCreateDialog" />
            </div>
          </div>
        </div>

        <q-table
          flat
          bordered
          :rows="rows"
          :columns="columns"
          row-key="id"
          :loading="loading"
          hide-pagination
          no-data-label="No users found"
        >
          <template #body-cell-actions="props">
            <q-td :props="props">
              <q-btn size="sm" flat color="primary" label="Edit" @click="openEditDialog(props.row)" />
              <q-btn size="sm" flat color="negative" label="Delete" @click="onDeleteUser(props.row)" />
            </q-td>
          </template>
        </q-table>

        <div class="row q-mt-md items-center justify-between">
          <div class="text-caption">Total users: {{ totalElements }}</div>
          <q-pagination
            v-model="uiPage"
            :max="Math.max(totalPages, 1)"
            direction-links
            boundary-links
            @update:model-value="onPageChange"
          />
        </div>

        <q-dialog v-model="showDialog">
          <q-card style="min-width: 420px; max-width: 95vw;">
            <q-card-section>
              <div class="text-h6">{{ editMode ? 'Edit User' : 'Create User' }}</div>
            </q-card-section>
            <q-card-section class="q-gutter-md">
              <q-input v-model="form.username" label="Initial username" outlined />
              <q-select
                v-model="form.role"
                :options="roleOptions"
                label="Initial role"
                outlined
                emit-value
                map-options
              />
              <q-input
                v-model="form.password"
                :label="editMode ? 'Initial password (leave empty to keep current)' : 'Initial password'"
                type="password"
                outlined
              />
            </q-card-section>
            <q-card-actions align="right">
              <q-btn flat label="Cancel" v-close-popup />
              <q-btn color="primary" :label="editMode ? 'Save' : 'Create'" @click="onSubmitForm" :loading="saving" />
            </q-card-actions>
          </q-card>
        </q-dialog>
      </div>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useQuasar } from 'quasar';
import { useAuthStore } from '../stores/auth';

type UserRow = {
  id: number;
  username: string;
  role: string;
};

const AUTH_SERVICE_URL = (import.meta as ImportMeta & { env: { VITE_AUTH_SERVICE_URL?: string } }).env?.VITE_AUTH_SERVICE_URL || 'http://localhost:3001';
const auth = useAuthStore();
const $q = useQuasar();

const rows = ref<UserRow[]>([]);
const loading = ref(false);
const saving = ref(false);
const page = ref(0);
const size = ref(10);
const totalPages = ref(1);
const totalElements = ref(0);
const searchUsername = ref('');

const showDialog = ref(false);
const editMode = ref(false);
const editingUserId = ref<number | null>(null);
const form = ref({
  username: '',
  role: 'maintainer',
  password: '',
});

const roleOptions = [
  { label: 'Admin', value: 'admin' },
  { label: 'Maintainer', value: 'maintainer' },
];

const uiPage = computed({
  get: () => page.value + 1,
  set: (value: number) => {
    page.value = Math.max(value - 1, 0);
  },
});

const columns = [
  { name: 'id', label: 'ID', field: 'id', align: 'left' as const, sortable: true },
  { name: 'username', label: 'Username', field: 'username', align: 'left' as const, sortable: true },
  { name: 'role', label: 'Role', field: 'role', align: 'left' as const, sortable: true },
  { name: 'actions', label: 'Actions', field: 'actions', align: 'right' as const },
];

async function fetchUsers() {
  if (!auth.isLoggedIn || !auth.isAdmin) {
    return;
  }
  loading.value = true;
  try {
    const response = await fetch(
      `${AUTH_SERVICE_URL}/api/admin/users?page=${page.value}&size=${size.value}&username=${encodeURIComponent(searchUsername.value.trim())}`,
      { credentials: 'include' }
    );
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(payload.error || 'Failed to load users');
    }
    rows.value = Array.isArray(payload.items) ? payload.items : [];
    totalPages.value = Number.isFinite(payload.totalPages) ? payload.totalPages : 1;
    totalElements.value = Number.isFinite(payload.totalElements) ? payload.totalElements : rows.value.length;
  } catch (error) {
    $q.notify({ type: 'negative', message: error instanceof Error ? error.message : 'Failed to load users' });
  } finally {
    loading.value = false;
  }
}

function onSearch() {
  page.value = 0;
  void fetchUsers();
}

function onResetSearch() {
  searchUsername.value = '';
  page.value = 0;
  void fetchUsers();
}

function onPageChange(nextPage: number) {
  page.value = Math.max(nextPage - 1, 0);
  void fetchUsers();
}

function openCreateDialog() {
  editMode.value = false;
  editingUserId.value = null;
  form.value = { username: '', role: 'maintainer', password: '' };
  showDialog.value = true;
}

function openEditDialog(row: UserRow) {
  editMode.value = true;
  editingUserId.value = row.id;
  form.value = { username: row.username, role: row.role, password: '' };
  showDialog.value = true;
}

async function onSubmitForm() {
  if (!form.value.username.trim() || !form.value.role.trim()) {
    $q.notify({ type: 'negative', message: 'Username and role are required' });
    return;
  }
  if (!editMode.value && !form.value.password) {
    $q.notify({ type: 'negative', message: 'Initial password is required' });
    return;
  }
  saving.value = true;
  try {
    const endpoint = editMode.value && editingUserId.value !== null
      ? `${AUTH_SERVICE_URL}/api/admin/users/${editingUserId.value}`
      : `${AUTH_SERVICE_URL}/api/admin/users`;
    const method = editMode.value ? 'PUT' : 'POST';
    const response = await fetch(endpoint, {
      method,
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: form.value.username.trim(),
        role: form.value.role,
        password: form.value.password,
      }),
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(payload.error || (editMode.value ? 'Failed to update user' : 'Failed to create user'));
    }
    $q.notify({ type: 'positive', message: editMode.value ? 'User updated' : 'User created' });
    showDialog.value = false;
    await fetchUsers();
  } catch (error) {
    $q.notify({ type: 'negative', message: error instanceof Error ? error.message : 'Save failed' });
  } finally {
    saving.value = false;
  }
}

async function onDeleteUser(row: UserRow) {
  try {
    await $q.dialog({
      title: 'Delete user',
      message: `Delete user "${row.username}"?`,
      cancel: true,
      persistent: true,
    });
  } catch {
    return;
  }

  try {
    const response = await fetch(`${AUTH_SERVICE_URL}/api/admin/users/${row.id}`, {
      method: 'DELETE',
      credentials: 'include',
    });
    if (!response.ok && response.status !== 204) {
      const payload = await response.json().catch(() => ({}));
      throw new Error(payload.error || 'Failed to delete user');
    }
    $q.notify({ type: 'positive', message: 'User deleted' });
    await fetchUsers();
  } catch (error) {
    $q.notify({ type: 'negative', message: error instanceof Error ? error.message : 'Delete failed' });
  }
}

onMounted(() => {
  void fetchUsers();
});
</script>

<style scoped>
.page-wrap {
  max-width: 1080px;
}
</style>
