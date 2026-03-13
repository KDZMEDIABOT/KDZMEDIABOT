<template>
  <q-page class="q-pa-md">
    <div class="page-wrap q-mx-auto">
      <h1 class="text-h5 q-mb-md">Settings</h1>

      <div v-if="!auth.isLoggedIn" class="text-center q-mt-lg">
        <p class="text-body1 q-mb-md">Please sign in to access settings.</p>
        <q-btn color="primary" label="Sign In" to="/login" />
      </div>

      <template v-else>
        <q-card v-if="canManageLlm" flat bordered class="q-mb-md">
          <q-card-section>
            <div class="text-subtitle1 q-mb-sm">LLM Endpoints</div>
            <div class="row q-col-gutter-sm items-end">
              <div class="col-12 col-md-3">
                <q-input v-model="endpointDisplayName" label="Display name" outlined dense />
              </div>
              <div class="col-12 col-md-3">
                <q-select
                  v-model="llmApiType"
                  :options="apiTypeOptions"
                  label="API type"
                  outlined
                  dense
                  emit-value
                  map-options
                />
              </div>
              <div class="col-12 col-md-3">
                <q-input v-model="baseURL" label="Base URL" outlined dense />
                <div class="text-caption text-grey-7 q-mt-xs">
                  {{ endpointPathHint }}
                </div>
              </div>
              <div class="col-12 col-md-3">
                <q-input v-model="apiKey" label="API key" outlined dense type="password" />
              </div>
              <div class="col-12 col-md-3">
                <q-input v-model="modelName" label="Model name" outlined dense />
              </div>
            </div>
            <div class="row q-mt-sm justify-end">
              <q-btn color="primary" label="Add endpoint" :loading="saving" @click="createEndpoint" />
            </div>
          </q-card-section>
        </q-card>

        <q-table
          v-if="canManageLlm"
          flat
          bordered
          :rows="rows"
          :columns="columns"
          row-key="id"
          :loading="loading"
          no-data-label="No endpoint credentials found"
        >
          <template #body-cell-actions="props">
            <q-td :props="props">
              <q-btn
                flat
                size="sm"
                color="primary"
                label="Edit"
                class="q-mr-sm"
                :disable="editingId === props.row.id"
                @click="openEditDialog(props.row)"
              />
              <q-btn
                flat
                size="sm"
                color="negative"
                label="Delete"
                :loading="deletingId === props.row.id"
                @click="deleteEndpoint(props.row.id, props.row.endpointDisplayName)"
              />
            </q-td>
          </template>
          <template #body-cell-current="props">
            <q-td :props="props">
              <q-badge v-if="props.row.current" color="positive" label="Current" />
              <q-btn
                v-else
                flat
                size="sm"
                color="primary"
                label="Set Current"
                :loading="selectingId === props.row.id"
                @click="selectCurrent(props.row.id)"
              />
            </q-td>
          </template>
        </q-table>

        <q-dialog v-model="editDialogOpen" persistent>
          <q-card style="min-width: 720px; max-width: 95vw;">
            <q-card-section>
              <div class="text-h6">Edit LLM Endpoint</div>
            </q-card-section>
            <q-card-section>
              <div class="row q-col-gutter-sm">
                <div class="col-12 col-md-6">
                  <q-input v-model="editEndpointDisplayName" label="Display name" outlined dense />
                </div>
                <div class="col-12 col-md-6">
                  <q-select
                    v-model="editLlmApiType"
                    :options="apiTypeOptions"
                    label="API type"
                    outlined
                    dense
                    emit-value
                    map-options
                  />
                </div>
                <div class="col-12">
                  <q-input v-model="editBaseURL" label="Base URL" outlined dense />
                </div>
                <div class="col-12 col-md-6">
                  <q-input v-model="editModelName" label="Model name" outlined dense />
                </div>
                <div class="col-12 col-md-6">
                  <q-input
                    v-model="editApiKey"
                    label="API key (leave empty to keep unchanged)"
                    outlined
                    dense
                    type="password"
                  />
                </div>
              </div>
            </q-card-section>
            <q-card-actions align="right">
              <q-btn flat label="Cancel" :disable="editing" @click="editDialogOpen = false" />
              <q-btn color="primary" label="Save" :loading="editing" @click="saveEndpointEdit" />
            </q-card-actions>
          </q-card>
        </q-dialog>

        <q-card flat bordered class="q-mt-md q-mb-md">
          <q-card-section>
            <div class="text-subtitle1">Change Password</div>
            <div class="text-caption q-mt-xs">
              Update your account password for this environment.
            </div>
          </q-card-section>
          <q-card-section>
            <q-form class="q-gutter-md" @submit="onChangePassword">
              <q-input
                v-model="currentPassword"
                label="Current password"
                type="password"
                outlined
              />
              <q-input
                v-model="newPassword"
                label="New password"
                type="password"
                outlined
              />
              <q-input
                v-model="confirmNewPassword"
                label="Confirm new password"
                type="password"
                outlined
              />
              <div>
                <q-btn
                  type="submit"
                  color="primary"
                  label="Update Password"
                  :loading="submittingPasswordChange"
                />
              </div>
            </q-form>
          </q-card-section>
        </q-card>
      </template>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useQuasar, type QTableColumn } from 'quasar';
import { useAuthStore } from '../stores/auth';

type LlmApiType = 'OpenAICompatible' | 'AnthropicCompatible';

type EndpointRow = {
  id: number;
  endpointDisplayName: string;
  llmApiType: LlmApiType;
  baseURL: string;
  modelName?: string | null;
  maskedApiKey: string;
  current: boolean;
};

const auth = useAuthStore();
const $q = useQuasar();

const currentPassword = ref('');
const newPassword = ref('');
const confirmNewPassword = ref('');
const submittingPasswordChange = ref(false);

const rows = ref<EndpointRow[]>([]);
const loading = ref(false);
const saving = ref(false);
const selectingId = ref<number | null>(null);
const deletingId = ref<number | null>(null);
const editingId = ref<number | null>(null);
const editing = ref(false);
const editDialogOpen = ref(false);
const editEndpointId = ref<number | null>(null);
const editEndpointDisplayName = ref('');
const editLlmApiType = ref<LlmApiType>('OpenAICompatible');
const editBaseURL = ref('');
const editModelName = ref('');
const editApiKey = ref('');

const endpointDisplayName = ref('');
const llmApiType = ref<LlmApiType>('OpenAICompatible');
const baseURL = ref('');
const apiKey = ref('');
const modelName = ref('');

const apiTypeOptions = [
  { label: 'OpenAI compatible', value: 'OpenAICompatible' },
  { label: 'Anthropic compatible', value: 'AnthropicCompatible' },
];

const columns: QTableColumn<EndpointRow>[] = [
  { name: 'endpointDisplayName', label: 'Display Name', field: 'endpointDisplayName', align: 'left', sortable: true },
  { name: 'llmApiType', label: 'API Type', field: 'llmApiType', align: 'left', sortable: true },
  { name: 'baseURL', label: 'Base URL', field: 'baseURL', align: 'left' },
  { name: 'modelName', label: 'Model Name', field: 'modelName', align: 'left' },
  { name: 'maskedApiKey', label: 'API Key', field: 'maskedApiKey', align: 'left' },
  { name: 'current', label: 'Current Endpoint', field: 'current', align: 'right' },
  { name: 'actions', label: 'Actions', field: 'actions', align: 'right' },
];

const canManageLlm = computed(() => auth.isAdmin || auth.isMaintainer);
const userId = computed(() => Number(auth.currentUser?.id ?? 0));
const endpointPathHint = computed(() => {
  const suffix = llmApiType.value === 'OpenAICompatible'
    ? '/v1/chat/completions'
    : '/v1/messages';
  const trimmedBaseURL = baseURL.value.trim();
  return trimmedBaseURL ? `${trimmedBaseURL}${suffix}` : `${'${baseURL}'}${suffix}`;
});

async function onChangePassword() {
  if (!currentPassword.value || !newPassword.value) {
    $q.notify({ type: 'negative', message: 'Current password and new password are required' });
    return;
  }
  if (newPassword.value.length < 8) {
    $q.notify({ type: 'negative', message: 'New password must be at least 8 characters' });
    return;
  }
  if (newPassword.value !== confirmNewPassword.value) {
    $q.notify({ type: 'negative', message: 'New passwords do not match' });
    return;
  }

  submittingPasswordChange.value = true;
  const result = await auth.changePassword(currentPassword.value, newPassword.value);
  submittingPasswordChange.value = false;

  if (!result.ok) {
    $q.notify({ type: 'negative', message: result.error || 'Password change failed' });
    return;
  }

  currentPassword.value = '';
  newPassword.value = '';
  confirmNewPassword.value = '';
  $q.notify({ type: 'positive', message: 'Password updated successfully' });
}

async function loadEntries() {
  if (!canManageLlm.value || !userId.value) {
    return;
  }

  loading.value = true;
  try {
    const response = await fetch(`/api/llm-endpoints?userId=${encodeURIComponent(String(userId.value))}`);
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(payload.error || 'Failed to load endpoint credentials');
    }
    rows.value = Array.isArray(payload.items) ? payload.items : [];
  } catch (error) {
    $q.notify({ type: 'negative', message: error instanceof Error ? error.message : 'Failed to load endpoint credentials' });
  } finally {
    loading.value = false;
  }
}

async function createEndpoint() {
  if (!endpointDisplayName.value.trim() || !baseURL.value.trim() || !apiKey.value.trim()) {
    $q.notify({ type: 'negative', message: 'Display name, base URL and API key are required' });
    return;
  }

  saving.value = true;
  try {
    const response = await fetch(`/api/llm-endpoints?userId=${encodeURIComponent(String(userId.value))}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        endpointDisplayName: endpointDisplayName.value.trim(),
        llmApiType: llmApiType.value,
        baseURL: baseURL.value.trim(),
        apiKey: apiKey.value.trim(),
        modelName: modelName.value.trim() || null,
      }),
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(payload.error || 'Failed to create endpoint credentials');
    }
    endpointDisplayName.value = '';
    llmApiType.value = 'OpenAICompatible';
    baseURL.value = '';
    apiKey.value = '';
    modelName.value = '';
    $q.notify({ type: 'positive', message: 'Endpoint credentials added' });
    await loadEntries();
  } catch (error) {
    $q.notify({ type: 'negative', message: error instanceof Error ? error.message : 'Failed to create endpoint credentials' });
  } finally {
    saving.value = false;
  }
}

async function selectCurrent(endpointId: number) {
  selectingId.value = endpointId;
  try {
    const response = await fetch(`/api/llm-endpoints/current?userId=${encodeURIComponent(String(userId.value))}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ endpointId }),
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(payload.error || 'Failed to select current endpoint');
    }
    $q.notify({ type: 'positive', message: 'Current endpoint updated' });
    await loadEntries();
  } catch (error) {
    $q.notify({ type: 'negative', message: error instanceof Error ? error.message : 'Failed to select current endpoint' });
  } finally {
    selectingId.value = null;
  }
}

async function deleteEndpoint(endpointId: number, endpointDisplayName: string) {
  $q.dialog({
    title: 'Delete endpoint',
    message: `Delete LLM endpoint "${endpointDisplayName}"?`,
    cancel: true,
    persistent: true,
  }).onOk(async () => {
    deletingId.value = endpointId;
    try {
      const response = await fetch(
        `/api/llm-endpoints/${encodeURIComponent(String(endpointId))}?userId=${encodeURIComponent(String(userId.value))}`,
        { method: 'DELETE' }
      );
      if (!response.ok && response.status !== 204) {
        const payload = await response.json().catch(() => ({}));
        throw new Error(payload.error || 'Failed to delete endpoint');
      }
      $q.notify({ type: 'positive', message: 'Endpoint deleted' });
      await loadEntries();
    } catch (error) {
      $q.notify({ type: 'negative', message: error instanceof Error ? error.message : 'Failed to delete endpoint' });
    } finally {
      deletingId.value = null;
    }
  });
}

function openEditDialog(row: EndpointRow) {
  editEndpointId.value = row.id;
  editEndpointDisplayName.value = row.endpointDisplayName;
  editLlmApiType.value = row.llmApiType;
  editBaseURL.value = row.baseURL;
  editModelName.value = row.modelName ?? '';
  editApiKey.value = '';
  editDialogOpen.value = true;
}

async function saveEndpointEdit() {
  if (!editEndpointId.value) {
    return;
  }
  if (!editEndpointDisplayName.value.trim() || !editBaseURL.value.trim()) {
    $q.notify({ type: 'negative', message: 'Display name and base URL are required' });
    return;
  }

  editing.value = true;
  editingId.value = editEndpointId.value;
  try {
    const response = await fetch(
      `/api/llm-endpoints/${encodeURIComponent(String(editEndpointId.value))}?userId=${encodeURIComponent(String(userId.value))}`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          endpointDisplayName: editEndpointDisplayName.value.trim(),
          llmApiType: editLlmApiType.value,
          baseURL: editBaseURL.value.trim(),
          modelName: editModelName.value.trim() || null,
          apiKey: editApiKey.value.trim() || null,
        }),
      }
    );
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(payload.error || 'Failed to update endpoint');
    }
    $q.notify({ type: 'positive', message: 'Endpoint updated' });
    editDialogOpen.value = false;
    await loadEntries();
  } catch (error) {
    $q.notify({ type: 'negative', message: error instanceof Error ? error.message : 'Failed to update endpoint' });
  } finally {
    editing.value = false;
    editingId.value = null;
  }
}

onMounted(() => {
  void loadEntries();
});
</script>

<style scoped>
.page-wrap {
  max-width: 1200px;
}
</style>
