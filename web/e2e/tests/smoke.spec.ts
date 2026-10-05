import { expect, test } from '@playwright/test'

test('health reports UP with the database connected', async ({ request }) => {
  const response = await request.get('/actuator/health')
  expect(response.status()).toBe(200)
  expect(((await response.json()) as { status: string }).status).toBe('UP')
})

test('the image serves the SPA, which shows the backend version', async ({ page, request }) => {
  const info = (await (await request.get('/api/v1/system/info')).json()) as { version: string }

  await page.goto('/')

  await expect(page.getByTestId('shell-title')).toHaveText('ERP')
  // Turkish characters survive the whole build → jar → image → browser path.
  await expect(page.getByText('Sürüm')).toBeVisible()
  await expect(page.getByTestId('system-version')).toHaveText(info.version)
  await expect(page.getByTestId('backend-unavailable')).toHaveCount(0)
})
