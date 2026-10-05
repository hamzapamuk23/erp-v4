import { describe, expect, it } from 'vitest'
import { ApiError, getJson, type FetchFn } from './http'

const respond =
  (body: string, status: number, contentType: string): FetchFn =>
  async () =>
    new Response(body, { status, headers: { 'content-type': contentType } })

async function failure(promise: Promise<unknown>): Promise<ApiError> {
  const error = await promise.then(
    () => undefined,
    (e: unknown) => e,
  )
  expect(error).toBeInstanceOf(ApiError)
  return error as ApiError
}

describe('getJson', () => {
  it('returns the parsed body on success', async () => {
    await expect(getJson('/x', respond('{"a":1}', 200, 'application/json'))).resolves.toEqual({
      a: 1,
    })
  })

  it('exposes RFC 9457 problem details on business errors', async () => {
    const problem = { title: 'Dönem kapalı', status: 422, code: 'documents.period-closed' }
    const error = await failure(
      getJson('/x', respond(JSON.stringify(problem), 422, 'application/problem+json')),
    )
    expect(error.status).toBe(422)
    expect(error.problem).toEqual(problem)
    expect(error.message).toBe('Dönem kapalı')
  })

  it('tolerates non-JSON error pages such as a proxy 502', async () => {
    const error = await failure(
      getJson('/x', respond('<html>Bad Gateway</html>', 502, 'text/html')),
    )
    expect(error.status).toBe(502)
    expect(error.problem).toBeUndefined()
  })

  it('tolerates a JSON content type with a broken body', async () => {
    const error = await failure(getJson('/x', respond('{not json', 500, 'application/json')))
    expect(error.status).toBe(500)
    expect(error.problem).toBeUndefined()
  })

  it('reports status 0 when the server cannot be reached', async () => {
    const offline: FetchFn = async () => {
      throw new TypeError('Failed to fetch')
    }
    const error = await failure(getJson('/x', offline))
    expect(error.status).toBe(0)
    expect(error.cause).toBeInstanceOf(TypeError)
  })

  it('asks for JSON with same-origin credentials', async () => {
    let seen: RequestInit | undefined
    const spy: FetchFn = async (_input, init) => {
      seen = init
      return new Response('{}', { status: 200, headers: { 'content-type': 'application/json' } })
    }
    await getJson('/x', spy)
    expect(seen?.credentials).toBe('same-origin')
    expect(new Headers(seen?.headers).get('accept')).toBe('application/json')
  })
})
