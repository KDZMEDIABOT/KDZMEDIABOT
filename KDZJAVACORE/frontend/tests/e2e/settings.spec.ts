import { test, expect } from '@playwright/test';

type EndpointItem = {
  id: number;
  endpointDisplayName: string;
  llmApiType: 'OpenAICompatible' | 'AnthropicCompatible';
  baseURL: string;
  maskedApiKey: string;
  modelName: string | null;
  current: boolean;
};

test.describe('Settings LLM endpoints', () => {
  test('creates and edits endpoint with model name', async ({ page }) => {
    const endpoints: EndpointItem[] = [];
    let capturedModelName: string | null = null;
    let capturedUpdatedModelName: string | null = null;
    let capturedUpdatedDisplayName: string | null = null;
    let capturedUpdatedBaseURL: string | null = null;

    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: '2',
          name: 'Maintainer User',
          email: 'maintainer@example.com',
          role: 'maintainer',
        }),
      });
    });

    await page.route('**/api/auth/token', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ accessToken: 'test-token' }),
      });
    });

    await page.route('**/api/llm-endpoints?userId=*', async (route) => {
      const request = route.request();
      if (request.method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            items: endpoints,
            currentEndpointId: null,
          }),
        });
        return;
      }

      if (request.method() === 'POST') {
        const payload = request.postDataJSON() as {
          endpointDisplayName: string;
          llmApiType: 'OpenAICompatible' | 'AnthropicCompatible';
          baseURL: string;
          apiKey: string;
          modelName?: string | null;
        };

        capturedModelName = payload.modelName ?? null;
        const item: EndpointItem = {
          id: endpoints.length + 1,
          endpointDisplayName: payload.endpointDisplayName,
          llmApiType: payload.llmApiType,
          baseURL: payload.baseURL,
          maskedApiKey: '****' + payload.apiKey.slice(-4),
          modelName: payload.modelName ?? null,
          current: false,
        };
        endpoints.push(item);
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(item),
        });
        return;
      }

      if (request.method() === 'PUT') {
        const endpointId = Number((new URL(request.url())).pathname.split('/').pop());
        const payload = request.postDataJSON() as {
          endpointDisplayName: string;
          llmApiType: 'OpenAICompatible' | 'AnthropicCompatible';
          baseURL: string;
          apiKey?: string | null;
          modelName?: string | null;
        };
        const existing = endpoints.find((entry) => entry.id === endpointId);
        if (!existing) {
          await route.fulfill({
            status: 404,
            contentType: 'application/json',
            body: JSON.stringify({ error: 'Not found' }),
          });
          return;
        }
        capturedUpdatedModelName = payload.modelName ?? null;
        capturedUpdatedDisplayName = payload.endpointDisplayName;
        capturedUpdatedBaseURL = payload.baseURL;
        existing.endpointDisplayName = payload.endpointDisplayName;
        existing.llmApiType = payload.llmApiType;
        existing.baseURL = payload.baseURL;
        existing.modelName = payload.modelName ?? null;
        if (payload.apiKey && payload.apiKey.trim().length > 0) {
          existing.maskedApiKey = '****' + payload.apiKey.slice(-4);
        }
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(existing),
        });
        return;
      }

      await route.fallback();
    });

    await page.goto('/login?logged_in=dev');
    await expect(page).toHaveURL('/');

    await page.goto('/settings');
    await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();

    await page.getByLabel('Display name').fill('nvidia-dev');
    await page.getByLabel('Base URL').fill('https://integrate.api.nvidia.com');
    await page.getByLabel('API key').fill('nvapi-test-key-123456');
    await page.getByLabel('Model name').fill('nvidia/llama-3.1-nemotron-70b-instruct');
    await page.getByRole('button', { name: 'Add endpoint' }).click();

    await expect.poll(() => capturedModelName).toBe('nvidia/llama-3.1-nemotron-70b-instruct');
    await expect(page.getByText('nvidia/llama-3.1-nemotron-70b-instruct')).toBeVisible();

    await page.getByRole('button', { name: 'Edit' }).first().click();

    const editDialog = page.locator('.q-dialog').last();
    await expect(editDialog).toBeVisible();
    await editDialog.getByLabel('Display name').fill('nvidia-prod');
    await editDialog.getByLabel('Base URL').fill('https://integrate.api.nvidia.com/v2');
    await editDialog.getByLabel('Model name').fill('nvidia/llama-3.3-70b-instruct');
    await editDialog.getByLabel('API key (leave empty to keep unchanged)').fill('nvapi-updated-key-7890');
    await editDialog.getByRole('button', { name: 'Save' }).click();

    await expect.poll(() => capturedUpdatedDisplayName).toBe('nvidia-prod');
    await expect.poll(() => capturedUpdatedBaseURL).toBe('https://integrate.api.nvidia.com/v2');
    await expect.poll(() => capturedUpdatedModelName).toBe('nvidia/llama-3.3-70b-instruct');
    await expect(page.getByText('nvidia-prod')).toBeVisible();
    await expect(page.getByText('https://integrate.api.nvidia.com/v2')).toBeVisible();
    await expect(page.getByText('nvidia/llama-3.3-70b-instruct')).toBeVisible();
  });
});
