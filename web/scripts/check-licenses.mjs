// Fails when a production dependency of any workspace package uses a license outside the allowlist
// (doc §3.1.6, §12.3). New license families need an ADR, not an edit here.
import { execFileSync } from 'node:child_process'
import { isAllowedExpression } from './license-expression.mjs'

const ALLOWED = new Set([
  'MIT',
  'Apache-2.0',
  'BSD-2-Clause',
  'BSD-3-Clause',
  'ISC',
  '0BSD',
  'BlueOak-1.0.0',
  'CC0-1.0',
])

const report = JSON.parse(
  execFileSync('pnpm', ['licenses', 'list', '--recursive', '--prod', '--json'], {
    encoding: 'utf8',
  }),
)

// SPDX expressions: "(MIT OR Apache-2.0)" passes when any alternative is allowed, an AND expression
// only when every conjunct is (see license-expression.mjs).
const isAllowed = (expression) => isAllowedExpression(expression, ALLOWED)

const packages = Object.values(report).flat()
const violations = Object.entries(report)
  .filter(([license]) => !isAllowed(license))
  .flatMap(([license, entries]) =>
    entries.map((p) => `${p.name}@${(p.versions ?? [p.version]).join(',')} (${license})`),
  )

if (violations.length > 0) {
  console.error(`Disallowed licenses in production dependencies:\n  ${violations.join('\n  ')}`)
  process.exit(1)
}
if (!packages.some((p) => p.name === 'vuetify')) {
  console.error(
    'License report looks incomplete: vuetify is missing. Check the pnpm licenses flags.',
  )
  process.exit(1)
}
console.log(`License check passed: ${packages.length} production packages.`)
