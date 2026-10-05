import { useQuery } from '@tanstack/vue-query'
import { getJson, type FetchFn } from './http'

export interface SystemInfo {
  version: string
  deploymentMode: 'saas' | 'onprem'
}

export function getSystemInfo(fetchFn?: FetchFn): Promise<SystemInfo> {
  return getJson<SystemInfo>('/api/v1/system/info', fetchFn)
}

/** System info never changes during a session; retry policy comes from the app's QueryClient. */
export function useSystemInfo() {
  return useQuery({
    queryKey: ['system', 'info'],
    queryFn: () => getSystemInfo(),
    staleTime: Infinity,
  })
}
