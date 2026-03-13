<template>
  <q-page class="q-pa-md">
    <div class="row items-center justify-between q-mb-md">
      <div class="text-h5">Running KIE Processes</div>
      <q-btn color="primary" icon="refresh" label="Reload" @click="loadProcesses" :loading="loading" />
    </div>

    <q-table
      title="Active Processes"
      :rows="rows"
      :columns="columns"
      row-key="processInstanceId"
      :loading="loading"
      :rows-per-page-options="[10, 20, 50]"
      flat
      bordered
    >
      <template #body-cell-startedAt="props">
        <q-td :props="props">{{ formatDateTime(props.row.startedAt) }}</q-td>
      </template>
    </q-table>
  </q-page>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useQuasar, type QTableColumn } from 'quasar';

interface RunningKieProcess {
  processInstanceId: number;
  processDefinitionId: string;
  jobId: number;
  topic: string;
  contentType: string;
  requesterRole: string;
  requesterUserId: number;
  requesterUsername: string;
  startedAt: string;
}

const $q = useQuasar();
const loading = ref(false);
const rows = ref<RunningKieProcess[]>([]);

const columns: QTableColumn<RunningKieProcess>[] = [
  { name: 'processInstanceId', label: 'Process ID', field: 'processInstanceId', align: 'left', sortable: true },
  { name: 'processDefinitionId', label: 'Definition', field: 'processDefinitionId', align: 'left' },
  { name: 'jobId', label: 'Job ID', field: 'jobId', align: 'left', sortable: true },
  { name: 'topic', label: 'Topic', field: 'topic', align: 'left' },
  { name: 'contentType', label: 'Type', field: 'contentType', align: 'left' },
  { name: 'requesterUsername', label: 'Requester', field: 'requesterUsername', align: 'left' },
  { name: 'requesterRole', label: 'Role', field: 'requesterRole', align: 'left' },
  { name: 'startedAt', label: 'Started', field: 'startedAt', align: 'left', sortable: true },
];

async function loadProcesses() {
  loading.value = true;
  try {
    const response = await fetch('/api/article-generation-jobs/running-kie-processes');
    if (!response.ok) {
      throw new Error('Failed to fetch running KIE processes');
    }
    rows.value = await response.json();
  } catch {
    $q.notify({ type: 'negative', message: 'Failed to load running KIE processes' });
  } finally {
    loading.value = false;
  }
}

function formatDateTime(value: string) {
  if (!value) {
    return '';
  }
  return new Date(value).toLocaleString();
}

onMounted(loadProcesses);
</script>
