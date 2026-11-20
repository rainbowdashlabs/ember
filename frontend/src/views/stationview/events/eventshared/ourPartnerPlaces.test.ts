/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {ourPartnerPlaces} from './ourPartnerPlaces'

const t = (key: string, values?: Record<string, unknown>) => values ? `${key}:${JSON.stringify(values)}` : key

describe('ourPartnerPlaces', () => {
  it('says nothing and offers no choosing where the holder decides as always', () => {
    expect(ourPartnerPlaces(null, true, t)).toEqual({summary: '', weDecide: false})
  })

  it('says the station chooses without a cap', () => {
    expect(ourPartnerPlaces({slotBudget: null, decidesItself: true}, true, t))
        .toEqual({summary: 'eventsUpcoming.placesUncapped', weDecide: true})
  })

  it('names the places given, in the words the owner asks for', () => {
    expect(ourPartnerPlaces({slotBudget: 3, decidesItself: true}, true, t).summary)
        .toBe('eventsUpcoming.placesGiven:{"total":3}')
    expect(ourPartnerPlaces({slotBudget: 3, decidesItself: true}, true, t, 'eventGroups.placesGiven').summary)
        .toBe('eventGroups.placesGiven:{"total":3}')
  })

  it('offers the choosing only to a reader who keeps the station\'s sign-ups', () => {
    expect(ourPartnerPlaces({slotBudget: 3, decidesItself: true}, false, t).weDecide).toBe(false)
  })

  it('offers no choosing where the holder did not hand it over', () => {
    expect(ourPartnerPlaces({slotBudget: null, decidesItself: false}, true, t).weDecide).toBe(false)
  })
})
