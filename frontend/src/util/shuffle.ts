/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Returns a copy of the given array in random order, leaving the input
 * untouched.
 */
export function shuffle<T>(array: T[]): T[] {
  const result = [...array]
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    const swapped = result[i] as T
    result[i] = result[j] as T
    result[j] = swapped
  }
  return result
}
