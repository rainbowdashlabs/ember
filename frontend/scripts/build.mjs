import { spawn, execSync } from 'node:child_process'
import { existsSync, rmSync } from 'node:fs'
import { resolve } from 'node:path'

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
