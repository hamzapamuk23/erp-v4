import { describe, expect, it } from 'vitest'
import type { FetchFn } from './http'
import { getSystemInfo } from './system'

describe('getSystemInfo', () => {
  it('reads /api/v1/system/info', async () => {
    let requested = ''
    const fetchFn: FetchFn = async (input) => {
      requested = input
      return new Response('{"version":"1.2.3","deploymentMode":"onprem"}', {
        status: 200,
        headers: { 'content-type': 'application/json' },
      })
    }
    await expect(getSystemInfo(fetchFn)).resolves.toEqual({
      version: '1.2.3',
      deploymentMode: 'onprem',
    })
    expect(requested).toBe('/api/v1/system/info')
  })
})
