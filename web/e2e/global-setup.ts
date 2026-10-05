import type { FullConfig } from '@playwright/test'

const TIMEOUT_MS = 180_000

/** Waits until the application reports UP, so tests never race the container start. */
export default async function globalSetup(config: FullConfig): Promise<void> {
  const baseURL = config.projects[0]?.use.baseURL ?? 'http://localhost:8080'
  const deadline = Date.now() + TIMEOUT_MS
  let last = 'no response'
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${baseURL}/actuator/health`)
      const body = response.ok ? ((await response.json()) as { status?: string }) : undefined
      if (body?.status === 'UP') return
      last = `HTTP ${response.status}`
    } catch (error) {
      last = String(error)
    }
    await new Promise((resolve) => setTimeout(resolve, 2_000))
  }
  throw new Error(`${baseURL} not healthy after ${TIMEOUT_MS / 1000}s (last: ${last})`)
}
