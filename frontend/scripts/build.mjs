import { spawn } from 'node:child_process'
import { existsSync, rmSync } from 'node:fs'
import { resolve } from 'node:path'

// Remove previous build output to detect completion
if (existsSync('.output')) {
  rmSync('.output', { recursive: true, force: true })
}

const nuxi = resolve('node_modules/.bin/nuxi')
const child = spawn(process.execPath, [nuxi, 'build'], {
  stdio: 'inherit',
  detached: true,
})

// Poll for build output
const interval = setInterval(() => {
  if (existsSync('.output/server/index.mjs')) {
    clearInterval(interval)
    setTimeout(() => {
      try { process.kill(-child.pid, 'SIGKILL') } catch {}
      process.exit(0)
    }, 2000)
  }
}, 1000)

child.on('exit', (code) => {
  clearInterval(interval)
  process.exit(code || 0)
})
