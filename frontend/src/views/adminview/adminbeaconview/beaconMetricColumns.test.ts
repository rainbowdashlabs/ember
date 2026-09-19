/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {bucketLowerBound} from './beaconMetricColumns'

/** Buckets sort by the size they stand for, not by how they are spelled. */
describe('bucketLowerBound', () => {
    it('orders every bucket the beacon reports by size', () => {
        const buckets = ['1000+', '10-50', '0', '200-1000', '<10', '50-200']

        const sorted = buckets.toSorted((a, b) => bucketLowerBound(a)! - bucketLowerBound(b)!)

        expect(sorted).toEqual(['0', '<10', '10-50', '50-200', '200-1000', '1000+'])
    })

    it('has nothing to sort by where nothing was reported', () => {
        expect(bucketLowerBound(null)).toBeNull()
        expect(bucketLowerBound('')).toBeNull()
        expect(bucketLowerBound('many')).toBeNull()
    })
})
