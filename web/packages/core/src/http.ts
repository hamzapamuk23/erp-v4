/** RFC 9457 problem details as returned by the backend (doc §8.3). */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  code?: string
  traceId?: string
  errors?: Array<{ field: string; code: string; message: string }>
}

/** Thrown for every failed API call. `status` is 0 when the server could not be reached at all. */
export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail | undefined

  constructor(status: number, problem?: ProblemDetail, options?: ErrorOptions) {
    super(problem?.title ?? (status === 0 ? 'Network error' : `HTTP ${status}`), options)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

export type FetchFn = (input: string, init?: RequestInit) => Promise<Response>

const browserFetch: FetchFn = (input, init) => globalThis.fetch(input, init)

export async function getJson<T>(path: string, fetchFn: FetchFn = browserFetch): Promise<T> {
  let response: Response
  try {
    response = await fetchFn(path, {
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    })
  } catch (cause) {
    throw new ApiError(0, undefined, { cause })
  }
  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response))
  }
  return (await response.json()) as T
}

async function readProblem(response: Response): Promise<ProblemDetail | undefined> {
  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('json')) return undefined
  try {
    return (await response.json()) as ProblemDetail
  } catch {
    return undefined
  }
}
