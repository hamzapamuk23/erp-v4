#!/usr/bin/env node
// Checks docs/guides/gelistirici-kilavuzu.md against the DocBuilder rules
// and the repository, so that the guide update does not rely on an LLM
// re-reading 2000+ lines by eye.
//
// Usage (from the repo root):
//   mise x -- node scripts/check-guide.mjs [guide.md] [--no-mermaid]
//
// Exit code 1 if any error is found. Warnings never fail the run.
// Intentionally missing paths (e.g. files created by "kendin dene"
// exercises) are listed in scripts/check-guide.allow, one per line.

import { execFileSync, spawnSync } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const args = process.argv.slice(2)
const guide = args.find((a) => !a.startsWith('--')) ?? 'docs/guides/gelistirici-kilavuzu.md'
const runMermaid = !args.includes('--no-mermaid')

const MAX_CODE_LINE = 80
const MAX_TABLE_COLUMNS = 5
const MERMAID_CLI = '@mermaid-js/mermaid-cli@9.1.7'
const ALLOWED_EMOJI = new Set(['✅', '❌', '⚠'])
// Path-like inline code that is not a repository path.
const NOT_A_PATH = /^(feat|fix|chore|docs|refactor|test|origin)\/|^(application|text|image)\//
const FILE_EXT =
  /\.(java|ts|mts|vue|xml|ya?ml|json|sh|mjs|cjs|js|md|sql|toml|properties|conf|html|txt)$/

const errors = []
const warnings = []
const error = (line, rule, msg) => errors.push({ line, rule, msg })
const warn = (line, rule, msg) => warnings.push({ line, rule, msg })

const lines = readFileSync(guide, 'utf8').split('\n')
const allow = new Set(
  existsSync('scripts/check-guide.allow')
    ? readFileSync('scripts/check-guide.allow', 'utf8')
        .split('\n')
        .map((l) => l.trim())
        .filter((l) => l && !l.startsWith('#'))
    : [],
)
const trackedFiles = execFileSync('git', ['ls-files'], { encoding: 'utf8' })
  .split('\n')
  .filter(Boolean)

// --- Pass 1: structure, code blocks, tables, prose -----------------------

const mermaidBlocks = []
const inlineCode = [] // { line, text }
let fence = null // { line, lang, body: [] }
let h1Count = 0

if (lines[0]?.trim() === '---') error(1, 'frontmatter', 'YAML frontmatter var')

lines.forEach((raw, i) => {
  const n = i + 1
  const fenceMatch = raw.match(/^(\s*)```(.*)$/)

  if (fenceMatch) {
    if (!fence) {
      const lang = fenceMatch[2].trim()
      if (fenceMatch[1]) error(n, 'fence-indent', 'Kod bloğu girintili')
      if (!lang) error(n, 'fence-lang', 'Dil etiketsiz kod bloğu')
      fence = { line: n, lang, body: [] }
    } else {
      if (fence.lang === 'mermaid') mermaidBlocks.push(fence)
      fence = null
    }
    return
  }

  if (fence) {
    fence.body.push(raw)
    const len = [...raw].length
    if (len > MAX_CODE_LINE) {
      error(n, 'code-width', `Kod satırı ${len} karakter (en fazla 80)`)
    }
    return
  }

  // Prose (outside code blocks).
  const heading = raw.match(/^(#+)\s/)
  if (heading) {
    const level = heading[1].length
    if (level === 1) h1Count++
    if (level > 4) error(n, 'heading-depth', `H${level} başlık (en fazla H4)`)
    if (/içindekiler/i.test(raw)) error(n, 'manual-toc', 'Elle İçindekiler')
  }

  const withoutInline = raw.replace(/`[^`]*`/g, '')
  const html = withoutInline.match(/<\/?[A-Za-z][^>]*>/)
  if (html) error(n, 'raw-html', `Kod dışında HTML: ${html[0]}`)

  for (const ch of withoutInline.match(/\p{Extended_Pictographic}/gu) ?? []) {
    if (!ALLOWED_EMOJI.has(ch)) error(n, 'emoji', `İzin verilmeyen emoji: ${ch}`)
  }

  if (/^\|/.test(raw) && /^\|[\s:|-]+\|\s*$/.test(lines[i + 1] ?? '')) {
    const columns = raw.replace(/\\\|/g, '').split('|').length - 2
    if (columns > MAX_TABLE_COLUMNS) {
      error(n, 'table-columns', `Tablo ${columns} sütun (en fazla 5)`)
    }
  }

  for (const m of raw.matchAll(/`([^`]+)`/g)) inlineCode.push({ line: n, text: m[1] })
})

if (fence) error(fence.line, 'fence-open', 'Kapanmamış kod bloğu')
if (h1Count !== 1) error(1, 'h1', `Tam olarak bir H1 olmalı, ${h1Count} var`)

// --- Pass 2: repository paths and identifiers ----------------------------

function looksLikePath(t) {
  if (/[\s<>*$@:~…]|\.\.\./.test(t) || /^[-/]|^https?:/.test(t)) return false
  if (NOT_A_PATH.test(t)) return false
  return t.includes('/') || FILE_EXT.test(t)
}

function isGitIgnored(p) {
  return spawnSync('git', ['check-ignore', '-q', p]).status === 0
}

function pathExists(p) {
  const clean = p.replace(/\/$/, '')
  if (existsSync(clean) || existsSync(join('web', clean))) return true
  // Bare names (`ErpShell.vue`) and paths written relative to a module
  // (`db/platform`, `src/test/java`) resolve anywhere in the repo.
  return trackedFiles.some(
    (f) =>
      f === clean ||
      f.endsWith(`/${clean}`) ||
      f.includes(`/${clean}/`) ||
      f.startsWith(`${clean}/`),
  )
}

const seenPaths = new Set()
const seenIdents = new Set()
for (const { line, text } of inlineCode) {
  const t = text.trim()
  if (looksLikePath(t)) {
    if (seenPaths.has(t) || allow.has(t)) continue
    seenPaths.add(t)
    if (!pathExists(t) && !isGitIgnored(t) && !isGitIgnored(join('web', t))) {
      error(line, 'missing-path', `Depoda yok: ${t}`)
    }
  } else if (/^[A-Z][a-z]+[A-Z][A-Za-z0-9]*$/.test(t)) {
    // CamelCase identifier (class, component, type): warn only.
    if (seenIdents.has(t) || allow.has(t)) continue
    seenIdents.add(t)
    const found = spawnSync('git', ['grep', '-q', '-w', '-F', t]).status === 0
    if (!found) warn(line, 'missing-identifier', `Kodda bulunamadı: ${t}`)
  }
}

// --- Pass 3: Mermaid ------------------------------------------------------

const diagramLimits = [
  { type: 'sequenceDiagram', re: /^\s*(participant|actor)\s/, max: 6, what: 'katılımcı' },
  { type: 'classDiagram', re: /^\s*class\s+\w+/, max: 8, what: 'sınıf' },
  { type: 'erDiagram', re: /^\s*[A-Za-z_]\w*\s*\{\s*$/, max: 6, what: 'varlık' },
]

mermaidBlocks.forEach((block, idx) => {
  const kind = block.body
    .find((l) => l.trim())
    ?.trim()
    .split(/\s/)[0]
  if (/^(namespace|note)\b/m.test(block.body.join('\n')) && kind === 'classDiagram') {
    error(block.line, 'mermaid-class', 'classDiagram içinde namespace/note')
  }
  // mmdc 9.1.7 renders non-ASCII names, but the DocBuilder engine does not.
  // Labels (after ':') and quoted text may be Turkish.
  if (kind === 'classDiagram' || kind === 'erDiagram') {
    const names = block.body.map((l) => l.replace(/"[^"]*"/g, '').replace(/:.*$/, '')).join('\n')
    if (/[^\x00-\x7F]/.test(names)) {
      error(block.line, 'mermaid-ascii', `${kind} adlarında ASCII dışı karakter`)
    }
  }
  for (const lim of diagramLimits) {
    if (kind !== lim.type) continue
    const count = block.body.filter((l) => lim.re.test(l)).length
    if (count > lim.max) {
      error(block.line, 'mermaid-size', `${count} ${lim.what} (en fazla ${lim.max})`)
    }
  }
})

if (runMermaid && mermaidBlocks.length) {
  const dir = mkdtempSync(join(tmpdir(), 'check-guide-'))
  mermaidBlocks.forEach((block, idx) => {
    const input = join(dir, `d${idx + 1}.mmd`)
    writeFileSync(input, block.body.join('\n'))
    const r = spawnSync(
      'npx',
      ['--yes', MERMAID_CLI, '-q', '-i', input, '-o', join(dir, `d${idx + 1}.svg`)],
      { encoding: 'utf8' },
    )
    if (r.status !== 0) {
      const reason = (r.stderr || r.stdout).split('\n').find((l) => l.trim()) ?? ''
      error(block.line, 'mermaid-render', `Şema ${idx + 1} çizilemedi: ${reason}`)
    }
  })
}

// --- Report ---------------------------------------------------------------

const print = (label, items) =>
  items
    .sort((a, b) => a.line - b.line)
    .forEach((x) => console.log(`${label} ${guide}:${x.line} [${x.rule}] ${x.msg}`))

print('HATA ', errors)
print('UYARI', warnings)
const mermaidNote = runMermaid ? 'çizildi' : 'atlandı (--no-mermaid)'
console.log(
  `\n${errors.length} hata, ${warnings.length} uyarı; ` +
    `${mermaidBlocks.length} Mermaid şeması ${mermaidNote}.`,
)
process.exit(errors.length ? 1 : 0)
