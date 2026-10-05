import assert from 'node:assert/strict'
import { test } from 'node:test'
import { isAllowedExpression } from './license-expression.mjs'

const ALLOWED = new Set(['MIT', 'Apache-2.0', 'ISC'])
const check = (expression) => isAllowedExpression(expression, ALLOWED)

test('allows a single allowed license', () => {
  assert.equal(check('MIT'), true)
})

test('allows an OR expression when any alternative is allowed', () => {
  assert.equal(check('(MIT OR Apache-2.0)'), true)
  assert.equal(check('(GPL-3.0 OR MIT)'), true)
})

test('allows an AND expression when every conjunct is allowed', () => {
  assert.equal(check('(MIT OR GPL-3.0) AND Apache-2.0'), true)
  assert.equal(check('MIT AND ISC'), true)
})

test('rejects an AND expression when any conjunct is disallowed', () => {
  assert.equal(check('(MIT OR X) AND GPL-3.0'), false)
  assert.equal(check('MIT AND GPL-3.0'), false)
})

test('rejects a disallowed license', () => {
  assert.equal(check('GPL-3.0'), false)
  assert.equal(check('(GPL-3.0 OR AGPL-3.0)'), false)
})

test('AND binds tighter than OR, as in SPDX', () => {
  assert.equal(check('MIT OR GPL-3.0 AND X'), true)
  assert.equal(check('GPL-3.0 AND MIT OR X'), false)
})

test('rejects empty or malformed expressions', () => {
  assert.equal(check(''), false)
  assert.equal(check('(MIT'), false)
  assert.equal(check('MIT AND'), false)
  assert.equal(check('MIT MIT'), false)
})
