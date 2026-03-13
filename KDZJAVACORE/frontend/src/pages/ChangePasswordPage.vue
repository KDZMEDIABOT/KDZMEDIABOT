<template>
  <q-page class="flex flex-center">
    <q-card class="password-card">
      <q-card-section>
        <h1 class="text-h5 text-center q-ma-none">Change Password</h1>
        <div class="text-caption text-center q-mt-xs">
          Update your account password for this environment.
        </div>
      </q-card-section>

      <q-card-section v-if="auth.isLoggedIn">
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
          <div class="row q-gutter-sm">
            <q-btn
              type="submit"
              color="primary"
              label="Update Password"
              :loading="submittingPasswordChange"
            />
            <q-btn flat color="primary" label="Back to Home" to="/" />
          </div>
        </q-form>
      </q-card-section>

      <q-card-section v-else class="text-center">
        <div class="text-body2 q-mb-md">Please sign in to change your password.</div>
        <q-btn color="primary" label="Go to Sign In" to="/login" />
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useQuasar } from 'quasar';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();
const $q = useQuasar();
const currentPassword = ref('');
const newPassword = ref('');
const confirmNewPassword = ref('');
const submittingPasswordChange = ref(false);

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
</script>

<style scoped>
.password-card {
  width: 100%;
  max-width: 520px;
  padding: 20px;
}
</style>
