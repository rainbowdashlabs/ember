import { spawn, spawnSync } from 'node:child_process'
import { existsSync, rmSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * Full frontend verification, in fail-fast order: linters, then type-check, then the
 * production build. Each stage is cheaper than the one after it, so a failure surfaces
 * as early as possible.
 *
 * Linters distinguish errors from warnings — only errors set a non-zero exit code, so the
 * warning backlog prints without blocking. Use `npm run lint:audit` to survey warnings
 * without gating.
 */
const lintScripts = [
  'lint-icons.mjs',
  'lint-conventions.mjs',
  'lint-helpcenter.mjs',
  'lint-helpcenter-i18n.mjs',
  'lint-locales.mjs',
  'lint-imports.mjs',
  'lint-style.mjs',
  'lint-i18n-keys.mjs',
  'lint-component-size.mjs',
  'lint-duplication.mjs',
  'lint-page-titles.mjs',
  'lint-browser-storage.mjs',
  'lint-standalone.mjs',
]

for (const script of lintScripts) {
  const result = spawnSync(process.execPath, [resolve('scripts', script)], { stdio: 'inherit' })
  if (result.status !== 0) {
    process.exit(result.status ?? 1)
  }
}

const nuxi = resolve('node_modules/.bin/nuxi')

/**
 * `nuxi build` does not type-check, so vue-tsc has to run as its own stage. Without this
 * the build only proves the bundle compiles, not that the types hold.
 */
const typecheck = spawnSync(process.execPath, [nuxi, 'typecheck'], { stdio: 'inherit' })
if (typecheck.status !== 0) {
  process.exit(typecheck.status ?? 1)
}

if (existsSync('.output')) {
  rmSync('.output', { recursive: true, force: true })
}

/**
 * nuxi build does not always exit on its own once the bundle is written, so it runs
 * detached and is killed as soon as the server entrypoint appears.
 */
const child = spawn(process.execPath, [nuxi, 'build'], {
  stdio: ['ignore', 'inherit', 'inherit'],
  detached: true,
})
child.unref()

let done = false

const interval = setInterval(() => {
  if (existsSync('.output/server/index.mjs')) {
    clearInterval(interval)
    done = true
    setTimeout(() => {
      try { process.kill(-child.pid, 'SIGKILL') } catch {}
    }, 2000)
  }
}, 1000)

child.on('exit', () => {
  clearInterval(interval)
  process.exit(done ? 0 : 1)
})
