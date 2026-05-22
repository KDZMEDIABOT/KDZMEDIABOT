import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { useAuthStore } from './auth';

const API_BASE = '';

export interface DialogThread {
    id: number;
    userId: number;
    title: string;
    status: string;
    modelName: string | null;
    systemPrompt: string | null;
    createdAt: string;
    updatedAt: string | null;
    lastMessageAt: string | null;
}

export interface DialogMessage {
    id: number;
    threadId: number;
    role: 'user' | 'assistant' | 'system' | 'tool';
    content: string;
    toolName: string | null;
    toolResult: string | null;
    tokensUsed: number | null;
    createdAt: string;
}

export const useDialogStore = defineStore('dialog', () => {
    const threads = ref<DialogThread[]>([]);
    const currentThread = ref<DialogThread | null>(null);
    const messages = ref<DialogMessage[]>([]);
    const isLoading = ref(false);
    const isSending = ref(false);

    const sortedThreads = computed(() => {
        return [...threads.value].sort((a, b) => {
            const aTime = a.lastMessageAt ? new Date(a.lastMessageAt).getTime() : new Date(a.createdAt).getTime();
            const bTime = b.lastMessageAt ? new Date(b.lastMessageAt).getTime() : new Date(b.createdAt).getTime();
            return bTime - aTime;
        });
    });

    // Pagination state
    const threadsPerPage = ref(10);
    const threadsPage = ref(1);
    const paginatedThreads = computed(() => {
        const start = (threadsPage.value - 1) * threadsPerPage.value;
        const end = start + threadsPerPage.value;
        return sortedThreads.value.slice(start, end);
    });
    const totalThreadPages = computed(() => Math.ceil(sortedThreads.value.length / threadsPerPage.value) || 1);

    function getToken(): string {
        const auth = useAuthStore();
        const token = auth.accessToken || sessionStorage.getItem('aisystem_bearer_token') || '';
        console.log('[DIALOG] getToken():', token ? 'token-present' : 'no-token');
        return token;
    }

    function handleAuthError(response: Response) {
        console.log('[DIALOG] handleAuthError status:', response.status, 'url:', response.url);
        if (response.status === 401 || response.status === 403) {
            console.log('[DIALOG] Redirecting to /login due to', response.status, response.body);
            const auth = useAuthStore();
            auth.clearAuth();
            //window.location.href = '/login';
        }
    }

    async function fetchThreads() {
        isLoading.value = true;
        try {
            const headers: Record<string, string> = {};
            const token = getToken();
            if (token) {
                headers['Authorization'] = `Bearer ${token}`;
            }
            const response = await fetch(`${API_BASE}/api/dialogs`, {
                credentials: 'include',
                headers,
            });
            if (response.ok) {
                const fetched: DialogThread[] = await response.json();
                threads.value = fetched;
                // Update currentThread reference if it exists in the new array
                if (currentThread.value) {
                    const found = threads.value.find(t => t.id === currentThread.value!.id) || null;
                    currentThread.value = found;
                }
            } else {
                handleAuthError(response);
            }
        } catch (e) {
            console.error('Failed to fetch threads:', e);
        } finally {
            isLoading.value = false;
        }
    }

    async function createThread(title: string, systemPrompt?: string) {
        console.log('[DIALOG] createThread() called with title:', title);
        try {
            const headers: Record<string, string> = {
                'Content-Type': 'application/json',
            };
            const token = getToken();
            if (token) {
                headers['Authorization'] = `Bearer ${token}`;
            }
            const response = await fetch(`${API_BASE}/api/dialogs`, {
                method: 'POST',
                credentials: 'include',
                headers,
                body: JSON.stringify({ title, systemPrompt }),
            });
            console.log('[DIALOG] createThread() response status:', response.status, response.ok);
            if (response.ok) {
                const thread: DialogThread = await response.json();
                console.log('[DIALOG] createThread() success, thread:', thread);
                threads.value.push(thread);
                threadsPage.value = 1;
                return thread;
            } else {
                handleAuthError(response);
            }
        } catch (e) {
            console.error('[DIALOG] createThread() catch:', e);
        }
        return null;
    }

    async function deleteThread(id: number) {
        try {
            const headers: Record<string, string> = {};
            const token = getToken();
            if (token) {
                headers['Authorization'] = `Bearer ${token}`;
            }
            const response = await fetch(`${API_BASE}/api/dialogs/${id}`, {
                method: 'DELETE',
                credentials: 'include',
                headers,
            });
            if (response.ok) {
                threads.value = threads.value.filter(t => t.id !== id);
                if (currentThread.value?.id === id) {
                    currentThread.value = null;
                    messages.value = [];
                }
            } else {
                handleAuthError(response);
            }
        } catch (e) {
            console.error('Failed to delete thread:', e);
        }
    }

    async function selectThread(id: number) {
        let thread = threads.value.find(t => t.id === id) || null;
        if (!thread) {
            // Deep-link: fetch thread from server if not loaded locally
            try {
                const headers: Record<string, string> = {};
                const token = getToken();
                if (token) {
                    headers['Authorization'] = `Bearer ${token}`;
                }
                const response = await fetch(`${API_BASE}/api/dialogs/${id}`, {
                    credentials: 'include',
                    headers,
                });
                if (response.ok) {
                    thread = await response.json();
                    if (thread && !threads.value.some(t => t.id === (thread as DialogThread).id)) {
                        threads.value.push(thread);
                    }
                } else {
                    handleAuthError(response);
                }
            } catch (e) {
                console.error('Failed to fetch thread:', e);
            }
        }
        currentThread.value = thread;
        if (thread) {
            messages.value = [];
            await fetchMessages(id);
        }
    }

    async function fetchMessages(threadId: number) {
        isLoading.value = true;
        try {
            const headers: Record<string, string> = {};
            const token = getToken();
            if (token) {
                headers['Authorization'] = `Bearer ${token}`;
            }
            const response = await fetch(`${API_BASE}/api/dialogs/${threadId}/messages`, {
                credentials: 'include',
                headers,
            });
            if (response.ok) {
                messages.value = await response.json();
            } else {
                handleAuthError(response);
            }
        } catch (e) {
            console.error('Failed to fetch messages:', e);
        } finally {
            isLoading.value = false;
        }
    }

    async function sendMessage(content: string) {
        if (!currentThread.value) return;
        isSending.value = true;
        const threadId = currentThread.value.id;

        // Optimistically add user message
        const userMessage: DialogMessage = {
            id: Date.now(),
            threadId,
            role: 'user',
            content,
            toolName: null,
            toolResult: null,
            tokensUsed: null,
            createdAt: new Date().toISOString(),
        };
        messages.value.push(userMessage);

        try {
            const headers: Record<string, string> = {
                'Content-Type': 'application/json',
            };
            const token = getToken();
            if (token) {
                headers['Authorization'] = `Bearer ${token}`;
            }
            const response = await fetch(`${API_BASE}/api/dialogs/${threadId}/chat`, {
                method: 'POST',
                credentials: 'include',
                headers,
                body: JSON.stringify({ content }),
            });
            if (response.ok) {
                const assistantMsg: DialogMessage = await response.json();
                messages.value.push(assistantMsg);
                // Update thread lastMessageAt locally for sidebar ordering
                if (currentThread.value) {
                    currentThread.value.lastMessageAt = new Date().toISOString();
                }
            } else {
                handleAuthError(response);
                const errorText = await response.text().catch(() => 'Unknown error');
                console.error('Chat request failed:', errorText);
                // Show error as system message
                messages.value.push({
                    id: Date.now() + 1,
                    threadId,
                    role: 'system',
                    content: `Chat error: ${errorText}`,
                    toolName: null,
                    toolResult: null,
                    tokensUsed: null,
                    createdAt: new Date().toISOString(),
                });
            }
        } catch (e) {
            console.error('Failed to send message:', e);
            messages.value.push({
                id: Date.now() + 1,
                threadId,
                role: 'system',
                content: 'Failed to send message. Please try again.',
                toolName: null,
                toolResult: null,
                tokensUsed: null,
                createdAt: new Date().toISOString(),
            });
        } finally {
            isSending.value = false;
        }
    }

    function clearCurrentThread() {
        currentThread.value = null;
        messages.value = [];
    }

    async function renameThread(id: number, title: string) {
        try {
            const headers: Record<string, string> = {
                'Content-Type': 'application/json',
            };
            const token = getToken();
            if (token) {
                headers['Authorization'] = `Bearer ${token}`;
            }
            const response = await fetch(`${API_BASE}/api/dialogs/${id}`, {
                method: 'PUT',
                credentials: 'include',
                headers,
                body: JSON.stringify({ title }),
            });
            if (response.ok) {
                const updated: DialogThread = await response.json();
                const idx = threads.value.findIndex(t => t.id === id);
                if (idx !== -1) {
                    threads.value[idx] = updated;
                }
                if (currentThread.value?.id === id) {
                    currentThread.value = updated;
                }
            } else {
                handleAuthError(response);
            }
        } catch (e) {
            console.error('Failed to rename thread:', e);
        }
    }

    return {
        threads,
        currentThread,
        messages,
        isLoading,
        isSending,
        sortedThreads,
        paginatedThreads,
        threadsPage,
        totalThreadPages,
        fetchThreads,
        createThread,
        deleteThread,
        selectThread,
        fetchMessages,
        sendMessage,
        clearCurrentThread,
        renameThread,
    };
});
