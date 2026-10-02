import { spawn, spawnSync } from 'node:child_process'
import { existsSync, rmSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * Full frontend verification, in fail-fast order: linters, then type-check, then the
 * production build. Each stage is cheaper than the one after it, so a failure surfaces
 * as early as possible.
 *
 * The linters are the registry in `linters.mjs`, run by `lint.mjs`, which is also what
 * `npm run lint` and CI run. `--skip-lint` leaves them out for a caller that has just run
 * them as a step of its own.
 */
function stage(args) {
  const result = spawnSync(process.execPath, args, { stdio: 'inherit' })
  if (result.status !== 0) {
    process.exit(result.status ?? 1)
  }
}

if (!process.argv.includes('--skip-lint')) {
  stage([resolve('scripts/lint.mjs')])
}

const nuxi = resolve('node_modules/.bin/nuxi')

/**
 * `nuxi build` does not type-check, so vue-tsc has to run as its own stage. Without this
 * the build only proves the bundle compiles, not that the types hold. The stories have a
 * tsconfig of their own, which the application's never included.
 */
stage([nuxi, 'typecheck'])
stage([resolve('node_modules/typescript/bin/tsc'), '-p', 'tsconfig.e2e.json'])

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
    setTimeout(() => killGroup(child.pid), 2000)
  }
}, 1000)

/**
 * Kills the build's process group. A group that has already exited on its own is what the kill
 * was for, so the "no such process" it answers with is not a failure.
 */
function killGroup(pid) {
  try {
    process.kill(-pid, 'SIGKILL')
  } catch (error) {
    if (error.code !== 'ESRCH') throw error
  }
}

child.on('exit', () => {
  clearInterval(interval)
  process.exit(done ? 0 : 1)
})
