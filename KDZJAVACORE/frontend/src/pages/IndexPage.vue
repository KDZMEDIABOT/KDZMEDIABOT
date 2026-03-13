<template>
  <q-page class="flex flex-center">
    <div class="text-center full-width page-content">
      <h1 class="text-h3 q-mb-md">Welcome to AI Content Generator</h1>
      <template v-if="auth.isLoggedIn">
        <p class="text-body1">Signed in as {{ auth.currentUser?.name }} ({{ auth.currentUser?.role }})</p>
        <div v-if="auth.isAdmin" class="q-mt-md">
          <h2 class="text-h5 q-mb-sm">Admin interface</h2>
          <p class="text-body2">Manage topics, approvals, and system-level publishing controls.</p>
          <q-btn class="q-mt-md" color="primary" outline label="Manage Users" to="/admin/users" />
          <q-btn class="q-mt-md q-ml-sm" color="secondary" outline label="Running KIE Processes" to="/admin/running-kie-processes" />
        </div>
        <div v-else-if="auth.isMaintainer" class="q-mt-md">
          <h2 class="text-h5 q-mb-sm">Maintainer interface</h2>
          <p class="text-body2">Review generated drafts and continue content workflow operations.</p>
        </div>
        <div v-else class="q-mt-md">
          <h2 class="text-h6 q-mb-sm">Unknown role</h2>
          <p class="text-body2">Your account role is not mapped to an interface yet.</p>
        </div>
        <div v-if="auth.isAdmin || auth.isMaintainer" class="q-mt-md q-gutter-sm">
          <q-btn color="primary" label="Articles List" to="/content/articles" />
          <q-btn color="secondary" outline label="Create Article" to="/content/generate" />
        </div>
        <div class="q-mt-sm q-gutter-sm">
          <q-btn v-if="auth.isLoggedIn" color="accent" outline label="Generation Jobs" to="/content/generation-jobs" />
        </div>
      </template>
      <template v-else>
        <p class="text-body1">Generate SEO-optimized articles with AI assistance.</p>
        <q-btn
          color="primary"
          label="Sign In"
          to="/login"
          size="lg"
          class="q-mt-lg"
        />
      </template>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();
</script>

<style scoped>
.page-content {
  max-width: 860px;
}
</style>
