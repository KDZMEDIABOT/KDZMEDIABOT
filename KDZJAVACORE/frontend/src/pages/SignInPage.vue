<template>
  <q-page class="flex flex-center">
    <q-card class="signin-card">
      <q-card-section>
        <div class="text-h5 text-center">Sign In</div>
      </q-card-section>

      <q-card-section>
        <q-form @submit="onSubmit" class="q-gutter-md">
          <q-input
            v-model="username"
            label="Username"
            outlined
            :rules="[val => !!val || 'Username is required']"
          />
          <q-input
            v-model="password"
            label="Password"
            type="password"
            outlined
            :rules="[val => !!val || 'Password is required']"
          />
          <q-checkbox v-model="rememberMe" label="Remember me" />
          <q-btn
            type="submit"
            color="primary"
            label="Sign In"
            class="full-width"
            :loading="loading"
          />
        </q-form>
      </q-card-section>

    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useQuasar } from 'quasar';
import { useAuthStore } from '../stores/auth';
import { useDialogStore } from '../stores/dialog';

const $q = useQuasar();
const router = useRouter();
const route = useRoute();
const auth = useAuthStore();

const username = ref('');
const password = ref('');
const rememberMe = ref(false);
const loading = ref(false);

onMounted(async () => {
  const loggedIn = route.query.logged_in;
  const error = route.query.error;
  if (error) {
    const errorCode = String(error);
    $q.notify({ type: 'negative', message: mapSsoErrorToMessage(errorCode) });
    return;
  }
  if (loggedIn === '1' || loggedIn === 'dev') {
    loading.value = true;
    const ok = await auth.initFromSSOCallback();
    loading.value = false;
    if (ok) {
      $q.notify({ type: 'positive', message: 'Signed in with SSO' });
      router.replace({ path: '/', query: {} });
    }
  }
});

async function onSubmit() {
  loading.value = true;
  console.log('[SIGNIN] onSubmit login called');
  const result = await auth.login(username.value, password.value, rememberMe.value);
  console.log('[SIGNIN] onSubmit login result:', result, 'auth.isLoggedIn now:', auth.isLoggedIn, 'auth.user:', auth.user);
  loading.value = false;
  if (!result.ok) {
    $q.notify({
      type: 'negative',
      message: result.error || 'Login failed with an unknown authentication error.',
      timeout: 8000,
    });
    return;
  }
  $q.notify({ type: 'positive', message: 'Sign in successful' });
  const dialogStore = useDialogStore();
  await dialogStore.fetchThreads();
  router.push('/');
}

function mapSsoErrorToMessage(errorCode: string): string {
  if (errorCode === 'config') {
    return 'SSO login failed: identity provider client configuration is invalid or incomplete.';
  }
  if (errorCode === 'no_sso_config') {
    return 'SSO login is not configured. Set issuer URI, client ID, and client secret in auth service configuration.';
  }
  if (errorCode === 'session_restart') {
    return 'SSO login session expired before callback. Start sign-in again and complete it without refreshing the callback URL.';
  }
  if (errorCode === 'auth_failed') {
    return 'SSO authentication failed at callback stage (token exchange or user info retrieval). Check auth service logs for provider details.';
  }
  return `SSO login failed with error code: ${errorCode}`;
}

</script>

<style scoped>
.signin-card {
  width: 100%;
  max-width: 400px;
  padding: 20px;
}
</style>
