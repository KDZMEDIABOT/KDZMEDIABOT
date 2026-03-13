<template>
  <q-page class="q-pa-md">
    <div class="row items-center justify-between q-mb-md">
      <div class="text-h5">Article Generation Jobs</div>
      <div class="row q-gutter-sm">
        <q-btn color="negative" outline icon="delete_sweep" label="Forget Finished/Cancelled" @click="forgetCompletedJobs" :loading="forgettingCompleted" />
        <q-btn color="primary" icon="refresh" label="Reload" @click="loadJobs" :loading="loading" />
      </div>
    </div>

    <q-table
      title="Jobs"
      :rows="jobs"
      :columns="columns"
      row-key="id"
      :loading="loading"
      :rows-per-page-options="[10, 20, 50]"
      flat
      bordered
    >
      <template #body-cell-createdAt="props">
        <q-td :props="props">{{ formatDateTime(props.row.createdAt) }}</q-td>
      </template>
      <template #body-cell-failureReason="props">
        <q-td :props="props">
          {{ props.row.status === 'FAILED' ? (props.row.errorMessage || 'No failure reason provided') : '' }}
        </q-td>
      </template>
      <template #body-cell-actionsLeft="props">
        <q-td :props="props">
          <q-btn
            v-if="props.row.status === 'RUNNING' || props.row.status === 'QUEUED'"
            size="sm"
            color="warning"
            flat
            icon="cancel"
            label="Cancel"
            :loading="cancellingJobId === props.row.id"
            @click="cancelJob(props.row.id)"
          />
          <q-btn
            v-if="props.row.status === 'CANCELLED'"
            size="sm"
            color="primary"
            flat
            icon="replay"
            label="Restart"
            :loading="restartingJobId === props.row.id"
            @click="restartJob(props.row.id)"
          />
          <q-btn
            v-if="props.row.status === 'FAILED'"
            size="sm"
            color="primary"
            flat
            icon="replay"
            label="Retry"
            :loading="retryingJobId === props.row.id"
            @click="retryJob(props.row.id)"
          />
          <q-btn
            v-if="props.row.status === 'COMPLETED' && !!props.row.articleId"
            size="sm"
            color="secondary"
            flat
            icon="visibility"
            label="Show"
            @click="showArticle(props.row.articleId)"
          />
        </q-td>
      </template>
      <template #body-cell-actions="props">
        <q-td :props="props">
          <q-btn
            v-if="props.row.status === 'RUNNING' || props.row.status === 'QUEUED'"
            size="sm"
            color="warning"
            flat
            icon="cancel"
            label="Cancel"
            :loading="cancellingJobId === props.row.id"
            @click="cancelJob(props.row.id)"
          />
          <q-btn
            v-if="props.row.status === 'CANCELLED'"
            size="sm"
            color="primary"
            flat
            icon="replay"
            label="Restart"
            :loading="restartingJobId === props.row.id"
            @click="restartJob(props.row.id)"
          />
          <q-btn
            v-if="props.row.status === 'FAILED'"
            size="sm"
            color="primary"
            flat
            icon="replay"
            label="Retry"
            :loading="retryingJobId === props.row.id"
            @click="retryJob(props.row.id)"
          />
          <q-btn
            v-if="props.row.status === 'COMPLETED' && !!props.row.articleId"
            size="sm"
            color="secondary"
            flat
            icon="visibility"
            label="Show"
            @click="showArticle(props.row.articleId)"
          />
        </q-td>
      </template>
    </q-table>
  </q-page>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import { useQuasar, type QTableColumn } from 'quasar';
import { useRouter } from 'vue-router';

interface ArticleGenerationJob {
  id: number;
  topic: string;
  contentType: string;
  status: string;
  processDefinitionId: string;
  requesterRole: string;
  requesterUsername: string;
  createdAt: string;
  errorMessage: string | null;
  articleId: number | null;
}

const $q = useQuasar();
const router = useRouter();
const loading = ref(false);
const jobs = ref<ArticleGenerationJob[]>([]);
const forgettingCompleted = ref(false);
const cancellingJobId = ref<number | null>(null);
const restartingJobId = ref<number | null>(null);
const retryingJobId = ref<number | null>(null);
const pollingInFlight = ref(false);
let pollingTimer: ReturnType<typeof setInterval> | null = null;

const columns: QTableColumn<ArticleGenerationJob>[] = [
  { name: 'actionsLeft', label: 'Actions', field: 'actionsLeft', align: 'left' },
  { name: 'id', label: 'ID', field: 'id', align: 'left', sortable: true },
  { name: 'articleId', label: 'Article ID', field: 'articleId', align: 'left', sortable: true },
  { name: 'topic', label: 'Topic', field: 'topic', align: 'left', sortable: true },
  { name: 'contentType', label: 'Type', field: 'contentType', align: 'left' },
  { name: 'status', label: 'Status', field: 'status', align: 'left' },
  { name: 'requesterRole', label: 'Role', field: 'requesterRole', align: 'left' },
  { name: 'requesterUsername', label: 'Launched By', field: 'requesterUsername', align: 'left' },
  { name: 'failureReason', label: 'Failure Reason', field: 'errorMessage', align: 'left' },
  { name: 'processDefinitionId', label: 'Process', field: 'processDefinitionId', align: 'left' },
  { name: 'createdAt', label: 'Created At', field: 'createdAt', align: 'left', sortable: true },
  { name: 'actions', label: 'Actions', field: 'actions', align: 'right' },
];

type LoadJobsOptions = {
  showLoading?: boolean;
  silentError?: boolean;
};

async function loadJobs(options: LoadJobsOptions = {}) {
  const { showLoading = true, silentError = false } = options;
  if (pollingInFlight.value) {
    return;
  }
  pollingInFlight.value = true;
  if (showLoading) {
    loading.value = true;
  }
  try {
    const response = await fetch('/api/article-generation-jobs');
    if (!response.ok) {
      throw new Error('Failed to load jobs');
    }
    jobs.value = await response.json();
  } catch {
    if (!silentError) {
      $q.notify({ type: 'negative', message: 'Failed to load article generation jobs' });
    }
  } finally {
    if (showLoading) {
      loading.value = false;
    }
    pollingInFlight.value = false;
  }
}

function formatDateTime(value: string) {
  if (!value) {
    return '';
  }
  return new Date(value).toLocaleString();
}

async function cancelJob(jobId: number) {
  cancellingJobId.value = jobId;
  try {
    const response = await fetch(`/api/article-generation-jobs/${jobId}/cancel`, { method: 'POST' });
    if (!response.ok) {
      throw new Error('Failed to cancel job');
    }
    $q.notify({ type: 'positive', message: `Job #${jobId} cancelled` });
    await loadJobs();
  } catch {
    $q.notify({ type: 'negative', message: `Failed to cancel job #${jobId}` });
  } finally {
    cancellingJobId.value = null;
  }
}

async function restartJob(jobId: number) {
  restartingJobId.value = jobId;
  try {
    const response = await fetch(`/api/article-generation-jobs/${jobId}/restart`, { method: 'POST' });
    if (!response.ok) {
      throw new Error('Failed to restart job');
    }
    $q.notify({ type: 'positive', message: `Job #${jobId} restarted` });
    await loadJobs();
  } catch {
    $q.notify({ type: 'negative', message: `Failed to restart job #${jobId}` });
  } finally {
    restartingJobId.value = null;
  }
}

async function retryJob(jobId: number) {
  retryingJobId.value = jobId;
  try {
    const response = await fetch(`/api/article-generation-jobs/${jobId}/retry`, { method: 'POST' });
    if (!response.ok) {
      throw new Error('Failed to retry job');
    }
    $q.notify({ type: 'positive', message: `Job #${jobId} retried` });
    await loadJobs();
  } catch {
    $q.notify({ type: 'negative', message: `Failed to retry job #${jobId}` });
  } finally {
    retryingJobId.value = null;
  }
}

function showArticle(articleId: number | null) {
  if (!articleId) {
    $q.notify({ type: 'warning', message: 'This completed job has no article id yet' });
    return;
  }
  void router.push(`/content/articles/preview/${articleId}`);
}

async function forgetCompletedJobs() {
  forgettingCompleted.value = true;
  try {
    const response = await fetch('/api/article-generation-jobs/completed', { method: 'DELETE' });
    if (!response.ok) {
      throw new Error('Failed to forget completed jobs');
    }
    const payload = await response.json().catch(() => ({ deletedCount: 0 }));
    $q.notify({ type: 'positive', message: `Forgot ${payload.deletedCount ?? 0} finished/cancelled jobs` });
    await loadJobs();
  } catch {
    $q.notify({ type: 'negative', message: 'Failed to forget finished/cancelled jobs' });
  } finally {
    forgettingCompleted.value = false;
  }
}

onMounted(() => {
  void loadJobs();
  pollingTimer = setInterval(() => {
    void loadJobs({ showLoading: false, silentError: true });
  }, 1000);
});

onUnmounted(() => {
  if (pollingTimer) {
    clearInterval(pollingTimer);
    pollingTimer = null;
  }
});
</script>
