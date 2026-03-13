<template>
  <q-page class="q-pa-md">
    <div class="row items-center justify-between q-mb-md">
      <div class="text-h5">Articles Workspace</div>
      <q-btn color="primary" icon="refresh" label="Reload" @click="loadArticles" :loading="loading" />
    </div>

    <q-table
      title="Articles"
      :rows="articles"
      :columns="columns"
      row-key="id"
      v-model:pagination="pagination"
      :loading="loading"
      :rows-per-page-options="[5, 10, 20, 50]"
      flat
      bordered
    >
      <template #body-cell-actions="props">
        <q-td :props="props">
          <q-btn
            size="sm"
            color="secondary"
            flat
            icon="visibility"
            label="Preview"
            @click="goPreview(props.row.id)"
          />
          <q-btn
            size="sm"
            color="primary"
            flat
            icon="edit"
            label="Edit"
            @click="openEditDialog(props.row)"
          />
        </q-td>
      </template>
    </q-table>

    <q-dialog v-model="editDialog">
      <q-card style="min-width: 680px; max-width: 92vw">
        <q-card-section class="row items-center q-pb-none">
          <div class="text-h6">Edit Article</div>
          <q-space />
          <q-btn icon="close" flat round dense v-close-popup />
        </q-card-section>

        <q-card-section class="q-gutter-md">
          <q-input v-model="editingArticle.title" filled label="Title" />
          <q-input v-model="editingArticle.slug" filled label="Slug" />
          <q-input v-model="editingArticle.metaDescription" filled label="Meta description" type="textarea" />
          <q-input v-model="editingArticle.content" filled label="Content" type="textarea" autogrow />
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="Cancel" color="grey-7" v-close-popup />
          <q-btn color="primary" label="Save" :loading="saving" @click="saveArticle" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useQuasar, type QTableColumn } from 'quasar';

interface ArticleSection {
  type: string;
  content: string;
  orderIndex: number;
}

interface Article {
  id: number;
  title: string;
  slug: string;
  metaDescription?: string;
  status: string;
  content?: string;
  sections?: ArticleSection[];
}

const $q = useQuasar();
const router = useRouter();

const loading = ref(false);
const saving = ref(false);
const articles = ref<Article[]>([]);
const editDialog = ref(false);
const editingArticle = ref<Article>({
  id: 0,
  title: '',
  slug: '',
  metaDescription: '',
  status: 'DRAFT',
  content: '',
  sections: [],
});

const pagination = ref({
  sortBy: 'id',
  descending: true,
  page: 1,
  rowsPerPage: 10,
});

const columns: QTableColumn<Article>[] = [
  { name: 'id', label: 'ID', field: 'id', align: 'left', sortable: true },
  { name: 'title', label: 'Title', field: 'title', align: 'left', sortable: true },
  { name: 'status', label: 'Status', field: 'status', align: 'left', sortable: true },
  { name: 'slug', label: 'Slug', field: 'slug', align: 'left' },
  { name: 'actions', label: 'Actions', field: 'actions', align: 'right' },
];

async function loadArticles() {
  loading.value = true;
  try {
    const response = await fetch('/api/articles');
    if (!response.ok) {
      throw new Error('Failed to load articles');
    }
    articles.value = await response.json();
  } catch {
    $q.notify({ type: 'negative', message: 'Failed to load article list' });
  } finally {
    loading.value = false;
  }
}

function goPreview(articleId: number) {
  router.push(`/content/articles/preview/${articleId}`);
}

function openEditDialog(article: Article) {
  editingArticle.value = { ...article };
  editDialog.value = true;
}

async function saveArticle() {
  saving.value = true;
  try {
    const response = await fetch('/api/articles', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(editingArticle.value),
    });

    if (!response.ok) {
      throw new Error('Save failed');
    }

    $q.notify({ type: 'positive', message: 'Article saved' });
    editDialog.value = false;
    await loadArticles();
  } catch {
    $q.notify({ type: 'negative', message: 'Failed to save article' });
  } finally {
    saving.value = false;
  }
}

onMounted(loadArticles);
</script>
