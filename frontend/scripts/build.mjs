import { spawn, spawnSync } from 'node:child_process'
import { existsSync, rmSync } from 'node:fs'
import { resolve } from 'node:path'

// Run the same lint suite as build:spa so `npm run build` fails fast on convention,
// helpcenter, icon, or locale violations instead of producing a green build with hidden
// linter errors. Each script propagates its own exit code; we surface non-zero status.
const lintScripts = ['lint-icons.mjs', 'lint-conventions.mjs', 'lint-helpcenter.mjs', 'lint-locales.mjs']
for (const script of lintScripts) {
  const result = spawnSync(process.execPath, [resolve('scripts', script)], { stdio: 'inherit' })
  if (result.status !== 0) {
    process.exit(result.status ?? 1)
  }
}

// Remove previous build output to detect completion
if (existsSync('.output')) {
  rmSync('.output', { recursive: true, force: true })
}

const nuxi = resolve('node_modules/.bin/nuxi')
const child = spawn(process.execPath, [nuxi, 'build'], {
  stdio: ['ignore', 'inherit', 'inherit'],
  detached: true,
})
child.unref()

let done = false

// Poll for build output
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
