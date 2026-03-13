<template>
  <q-layout view="hHh lpR fFf">
    <q-header elevated>
      <q-toolbar>
        <q-btn flat dense round icon="menu" aria-label="Menu" @click="toggleLeftDrawer" />
        <q-toolbar-title>
          AI Content Generator
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
  </q-layout>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();
const leftDrawerOpen = ref(false);
const canManageContent = computed(() => auth.isAdmin || auth.isMaintainer);

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
</script>
