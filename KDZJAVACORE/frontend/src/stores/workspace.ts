import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { useAuthStore } from './auth';

const API_BASE = '';

// ---- Workspace types definitions ----
export interface WorkspaceFile {
  id: number;
  workspaceId: number;
  fileName: string;
  mimeType: string;
  fileSize: number;
  indexedAt: string | null;
  createdAt: string;
}

export interface Workspace {
  id: number;
  userId: number;
  name: string;
  createdAt: string;
  updatedAt: string | null;
}

export const useWorkspaceStore = defineStore('workspace', () => {
  const workspaces = ref<Workspace[]>([]);
  const currentWorkspace = ref<Workspace | null>(null);
  const files = ref<WorkspaceFile[]>([]);
  const isLoading = ref(false);

  const auth = useAuthStore();

  function getToken(): string {
    return auth.accessToken || sessionStorage.getItem('aisystem_bearer_token') || '';
  }

  function getHeaders(withContentType = true): Record<string, string> {
    const headers: Record<string, string> = {};
    const token = getToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    if (withContentType) {
      headers['Content-Type'] = 'application/json';
    }
    return headers;
  }

  async function fetchWorkspaces() {
    isLoading.value = true;
    try {
      const headers = getHeaders(false);
      const response = await fetch(`${API_BASE}/api/workspaces`, {
        credentials: 'include',
        headers,
      });
      if (response.ok) {
        workspaces.value = await response.json();
      } else {
        console.error('Failed to fetch workspaces:', response.status);
      }
    } catch (e) {
      console.error('Failed to fetch workspaces:', e);
    } finally {
      isLoading.value = false;
    }
  }

  async function createWorkspace(name: string) {
    try {
      const headers = getHeaders();
      const response = await fetch(`${API_BASE}/api/workspaces`, {
        method: 'POST',
        credentials: 'include',
        headers,
        body: JSON.stringify({ name }),
      });
      if (response.ok) {
        const ws: Workspace = await response.json();
        workspaces.value.push(ws);
        return ws;
      }
    } catch (e) {
      console.error('Failed to create workspace:', e);
    }
    return null;
  }

  async function renameWorkspace(id: number, newName: string) {
    try {
      const headers = getHeaders();
      const response = await fetch(`${API_BASE}/api/workspaces/${id}`, {
        method: 'PUT',
        credentials: 'include',
        headers,
        body: JSON.stringify({ name: newName }),
      });
      if (response.ok) {
        const updated: Workspace = await response.json();
        const idx = workspaces.value.findIndex((w) => w.id === id);
        if (idx !== -1) {
          workspaces.value[idx] = updated;
        }
        if (currentWorkspace.value?.id === id) {
          currentWorkspace.value = updated;
        }
        return updated;
      }
    } catch (e) {
      console.error('Failed to rename workspace:', e);
    }
    return null;
  }

  async function deleteWorkspace(id: number) {
    try {
      const headers = getHeaders(false);
      const response = await fetch(`${API_BASE}/api/workspaces/${id}`, {
        method: 'DELETE',
        credentials: 'include',
        headers,
      });
      if (response.ok) {
        workspaces.value = workspaces.value.filter((w) => w.id !== id);
        if (currentWorkspace.value?.id === id) {
          currentWorkspace.value = null;
        }
      }
    } catch (e) {
      console.error('Failed to delete workspace:', e);
    }
  }

  async function selectWorkspace(id: number) {
    const ws = workspaces.value.find((w) => w.id === id) || null;
    currentWorkspace.value = ws;
    files.value = [];
    if (ws) {
      await fetchFiles(id);
    }
  }

  async function fetchFiles(workspaceId: number) {
    isLoading.value = true;
    try {
      const headers = getHeaders(false);
      const response = await fetch(`${API_BASE}/api/workspaces/${workspaceId}/files`, {
        credentials: 'include',
        headers,
      });
      if (response.ok) {
        files.value = await response.json();
      } else {
        console.error('Failed to fetch files:', response.status);
      }
    } catch (e) {
      console.error('Failed to fetch files:', e);
    } finally {
      isLoading.value = false;
    }
  }

  async function uploadFile(workspaceId: number, file: File) {
    try {
      const formData = new FormData();
      formData.append('file', file);

      const headers: Record<string, string> = {};
      const token = getToken();
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const response = await fetch(`${API_BASE}/api/workspaces/${workspaceId}/files`, {
        method: 'POST',
        credentials: 'include',
        headers,
        body: formData,
      });
      if (response.ok) {
        const f: WorkspaceFile = await response.json();
        files.value.unshift(f);
        return f;
      } else {
        console.error('Failed to upload file:', response.status);
      }
    } catch (e) {
      console.error('Failed to upload file:', e);
    }
    return null;
  }

  async function deleteFile(workspaceId: number, fileId: number) {
    try {
      const headers = getHeaders(false);
      const response = await fetch(`${API_BASE}/api/workspaces/${workspaceId}/files/${fileId}`, {
        method: 'DELETE',
        credentials: 'include',
        headers,
      });
      if (response.ok) {
        files.value = files.value.filter((f) => f.id !== fileId);
      }
    } catch (e) {
      console.error('Failed to delete file:', e);
    }
  }

  return {
    workspaces,
    currentWorkspace,
    files,
    isLoading,
    fetchWorkspaces,
    createWorkspace,
    renameWorkspace,
    deleteWorkspace,
    selectWorkspace,
    fetchFiles,
    uploadFile,
    deleteFile,
  };
});
