<template>
  <q-page class="flex flex-center">
    <q-card class="signup-card">
      <q-card-section>
        <div class="text-h5 text-center">Sign Up</div>
      </q-card-section>

      <q-card-section>
        <q-form @submit="onSubmit" class="q-gutter-md">
          <q-input
            v-model="name"
            label="Full Name"
            outlined
            :rules="[val => !!val || 'Name is required']"
          />
          <q-input
            v-model="email"
            label="Email"
            type="email"
            outlined
            :rules="[val => !!val || 'Email is required']"
          />
          <q-input
            v-model="password"
            label="Password"
            type="password"
            outlined
            :rules="[val => val.length >= 8 || 'Password must be at least 8 characters']"
          />
          <q-input
            v-model="confirmPassword"
            label="Confirm Password"
            type="password"
            outlined
            :rules="[val => val === password || 'Passwords do not match']"
          />
          <q-btn
            type="submit"
            color="primary"
            label="Sign Up"
            class="full-width"
            :loading="loading"
          />
        </q-form>
      </q-card-section>

      <q-card-section class="text-center">
        <div class="q-mt-sm">
          Already have an account?
          <router-link to="/login" class="text-primary">Sign In</router-link>
        </div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useQuasar } from 'quasar';

const $q = useQuasar();
const router = useRouter();

const name = ref('');
const email = ref('');
const password = ref('');
const confirmPassword = ref('');
const loading = ref(false);

async function onSubmit() {
  if (password.value !== confirmPassword.value) {
    $q.notify({
      type: 'negative',
      message: 'Passwords do not match'
    });
    return;
  }

  loading.value = true;
  // TODO: Implement actual registration
  setTimeout(() => {
    loading.value = false;
    $q.notify({
      type: 'positive',
      message: 'Account created successfully (placeholder)'
    });
    router.push('/login');
  }, 1000);
}

</script>

<style scoped>
.signup-card {
  width: 100%;
  max-width: 400px;
  padding: 20px;
}
</style>
