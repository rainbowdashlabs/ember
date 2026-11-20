/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {
    ExpiryState,
    FieldOrigin,
    FieldType,
    RuleNeed,
    RuleNodeKind,
    RuleOperator,
    RuleSubject,
    RuleUnit,
    type RuleField,
    type RuleOperatorOption,
    type RuleValue,
} from '@/api/generated/schema'
import {
    countEmptyBlocks,
    complete,
    countChecks,
    countsFromReference,
    depthOf,
    emptyBlock,
    emptyCheck,
    fieldOf,
    fieldsFor,
    needOf,
    incompleteChecks,
    operatorsFor,
    readsMemberData,
    satisfies,
    withField,
    withOperator,
    withSubject,
    withValue,
    emptyValue,
} from './ruleDraft'

const OPTIONS: RuleOperatorOption[] = [
    {subject: RuleSubject.GROUP, fieldType: null, operator: RuleOperator.IN, need: RuleNeed.NOTHING},
    {subject: RuleSubject.GROUP, fieldType: null, operator: RuleOperator.NOT_IN, need: RuleNeed.NOTHING},
    {subject: RuleSubject.FIELD, fieldType: FieldType.BOOLEAN, operator: RuleOperator.IS_NO, need: RuleNeed.NOTHING},
    {subject: RuleSubject.FIELD, fieldType: FieldType.CHOICE, operator: RuleOperator.ONE_OF, need: RuleNeed.ANSWERS},
    {subject: RuleSubject.FIELD, fieldType: FieldType.CHOICE, operator: RuleOperator.NONE_OF, need: RuleNeed.ANSWERS},
    {subject: RuleSubject.FIELD, fieldType: FieldType.CHOICE, operator: RuleOperator.EMPTY, need: RuleNeed.NOTHING},
    {subject: RuleSubject.CLUSTER_FIELD, fieldType: FieldType.BOOLEAN, operator: RuleOperator.IS_NO, need: RuleNeed.NOTHING},
    {subject: RuleSubject.AGE, fieldType: FieldType.BIRTH_DATE, operator: RuleOperator.AT_LEAST, need: RuleNeed.YEARS},
]

const FIELDS: RuleField[] = [
    {origin: FieldOrigin.STATION, id: 1, name: 'JuLeiCa', fieldType: FieldType.BOOLEAN, options: []},
    {origin: FieldOrigin.STATION, id: 2, name: 'Jugendflamme', fieldType: FieldType.CHOICE, options: ['Stufe 1']},
    {origin: FieldOrigin.STATION, id: 3, name: 'Geburtsdatum', fieldType: FieldType.BIRTH_DATE, options: []},
    {origin: FieldOrigin.STATION, id: 4, name: 'Notiz', fieldType: FieldType.TEXT, options: []},
    {origin: FieldOrigin.CLUSTER, id: 1, name: 'Verband', fieldType: FieldType.BOOLEAN, options: []},
]

describe('ruleDraft', () => {
    it('starts a check on a group and a block with nothing in it', () => {
        expect(emptyCheck()).toMatchObject({kind: RuleNodeKind.CHECK, subject: RuleSubject.GROUP, operator: RuleOperator.IN})
        expect(emptyBlock(RuleNodeKind.ANY)).toMatchObject({kind: RuleNodeKind.ANY, children: []})
    })

    it('counts every block that holds nothing, nested ones too', () => {
        expect(countEmptyBlocks(emptyBlock())).toBe(1)
        expect(countEmptyBlocks({...emptyBlock(), children: [emptyCheck()]})).toBe(0)
        expect(countEmptyBlocks({...emptyBlock(), children: [emptyCheck(), emptyBlock(), emptyBlock()]})).toBe(2)
    })

    it('offers a field check only the operators of its field type', () => {
        expect(operatorsFor(OPTIONS, RuleSubject.FIELD, FieldType.CHOICE).map(o => o.operator))
            .toEqual([RuleOperator.ONE_OF, RuleOperator.NONE_OF, RuleOperator.EMPTY])
        expect(operatorsFor(OPTIONS, RuleSubject.FIELD, null)).toEqual([])
        expect(operatorsFor(OPTIONS, RuleSubject.GROUP, FieldType.TEXT).map(o => o.operator))
            .toEqual([RuleOperator.IN, RuleOperator.NOT_IN])
    })

    it('lists for each subject only the fields it can read', () => {
        expect(fieldsFor(RuleSubject.FIELD, FIELDS, OPTIONS).map(f => f.name)).toEqual(['JuLeiCa', 'Jugendflamme'])
        expect(fieldsFor(RuleSubject.CLUSTER_FIELD, FIELDS, OPTIONS).map(f => f.name)).toEqual(['Verband'])
        expect(fieldsFor(RuleSubject.AGE, FIELDS, OPTIONS).map(f => f.name)).toEqual(['Geburtsdatum'])
    })

    it('finds the field a check reads by its origin', () => {
        const station = withField(withSubject(emptyCheck(), RuleSubject.FIELD, OPTIONS), FIELDS[0]!, OPTIONS)
        const cluster = withField(withSubject(emptyCheck(), RuleSubject.CLUSTER_FIELD, OPTIONS), FIELDS[4]!, OPTIONS)
        expect(fieldOf(station, FIELDS)?.name).toBe('JuLeiCa')
        expect(fieldOf(cluster, FIELDS)?.name).toBe('Verband')
        expect(fieldOf(emptyCheck(), FIELDS)).toBeUndefined()
        expect(fieldOf({...station, fieldId: 99}, FIELDS)).toBeUndefined()
    })

    it('starts over when the subject changes', () => {
        const check = {...emptyCheck(), groupId: 5}
        const field = withSubject(check, RuleSubject.FIELD, OPTIONS)
        expect(field).toMatchObject({subject: RuleSubject.FIELD, operator: null, groupId: null})
        expect(withSubject(field, RuleSubject.GROUP, OPTIONS).operator).toBe(RuleOperator.IN)
    })

    it('takes the first operator of a picked field and says what it needs', () => {
        const check = withField(withSubject(emptyCheck(), RuleSubject.FIELD, OPTIONS), FIELDS[1]!, OPTIONS)
        expect(check).toMatchObject({fieldId: 2, clusterFieldId: null, operator: RuleOperator.ONE_OF})
        expect(needOf(check, OPTIONS, FIELDS)).toBe(RuleNeed.ANSWERS)
        expect(needOf({...check, subject: null}, OPTIONS, FIELDS)).toBeUndefined()
    })

    it('keeps the value across operators that need the same, and drops it otherwise', () => {
        const check = withValue(
            withField(withSubject(emptyCheck(), RuleSubject.FIELD, OPTIONS), FIELDS[1]!, OPTIONS),
            {answers: ['Stufe 1']},
        )
        expect(withOperator(check, RuleOperator.NONE_OF, OPTIONS, FIELDS).value?.answers).toEqual(['Stufe 1'])
        expect(withOperator(check, RuleOperator.EMPTY, OPTIONS, FIELDS).value).toBeNull()
    })

    it('counts from a reference day for ages, membership lengths and the date windows', () => {
        expect(countsFromReference({...emptyCheck(), subject: RuleSubject.AGE})).toBe(true)
        expect(countsFromReference({...emptyCheck(), subject: RuleSubject.MEMBER_SINCE})).toBe(true)
        expect(countsFromReference({...emptyCheck(), subject: RuleSubject.FIELD, operator: RuleOperator.EXPIRED})).toBe(true)
        expect(countsFromReference(emptyCheck())).toBe(false)
        const dated = {...emptyCheck(), subject: RuleSubject.FIELD, operator: RuleOperator.EXPIRED, referenceDate: '2027-01-01'}
        expect(withOperator(dated, RuleOperator.IN, OPTIONS, FIELDS).referenceDate).toBeNull()
    })

    it('counts the checks and the levels of a tree', () => {
        const inner = {...emptyBlock(RuleNodeKind.ANY), children: [emptyCheck(), emptyCheck()]}
        const root = {...emptyBlock(), children: [emptyCheck(), inner]}
        expect(countChecks(root)).toBe(3)
        expect(depthOf(root)).toBe(3)
        expect(depthOf(emptyBlock())).toBe(1)
    })

    it('measures values the way the server does', () => {
        const value = (part: Partial<RuleValue>) => ({...emptyValue(), ...part})
        expect(satisfies(null, RuleNeed.NOTHING)).toBe(true)
        expect(satisfies(value({answers: [' ']}), RuleNeed.ANSWERS)).toBe(false)
        expect(satisfies(value({answers: ['Stufe 1']}), RuleNeed.ANSWERS)).toBe(true)
        expect(satisfies(value({userTypes: []}), RuleNeed.USER_TYPES)).toBe(false)
        expect(satisfies(value({states: [ExpiryState.EXPIRED]}), RuleNeed.STATES)).toBe(true)
        expect(satisfies(value({number: 0}), RuleNeed.NUMBER)).toBe(true)
        expect(satisfies(value({number: 5, numberTo: 1}), RuleNeed.NUMBER_RANGE)).toBe(false)
        expect(satisfies(value({text: '  '}), RuleNeed.TEXT)).toBe(false)
        expect(satisfies(value({date: '2027-01-01'}), RuleNeed.DATE)).toBe(true)
        expect(satisfies(value({amount: 3}), RuleNeed.DURATION)).toBe(false)
        expect(satisfies(value({amount: 3, unit: RuleUnit.DAYS}), RuleNeed.DURATION)).toBe(true)
        expect(satisfies(value({amount: -1}), RuleNeed.YEARS)).toBe(false)
        expect(satisfies(value({amount: 10, amountTo: 14}), RuleNeed.YEARS_RANGE)).toBe(true)
        expect(satisfies(value({amount: 14, amountTo: 10}), RuleNeed.YEARS_RANGE)).toBe(false)
    })

    it('calls a check complete once it names its target, an operator and the value it needs', () => {
        expect(complete(emptyCheck(), OPTIONS, FIELDS)).toBe(false)
        expect(complete({...emptyCheck(), groupId: 4}, OPTIONS, FIELDS)).toBe(true)
        const choice = withField(withSubject(emptyCheck(), RuleSubject.FIELD, OPTIONS), FIELDS[1]!, OPTIONS)
        expect(complete(choice, OPTIONS, FIELDS)).toBe(false)
        expect(complete(withValue(choice, {answers: ['Stufe 1']}), OPTIONS, FIELDS)).toBe(true)
        expect(complete(withSubject(emptyCheck(), RuleSubject.FIELD, OPTIONS), OPTIONS, FIELDS)).toBe(false)
        expect(complete({...emptyCheck(), subject: RuleSubject.TAG, tagId: null}, OPTIONS, FIELDS)).toBe(false)
        const tree = {...emptyBlock(), children: [emptyCheck(), {...emptyCheck(), groupId: 1}, choice]}
        expect(incompleteChecks(tree, OPTIONS, FIELDS)).toBe(2)
    })

    it('knows which checks read member data', () => {
        expect(readsMemberData(emptyCheck())).toBe(false)
        expect(readsMemberData({...emptyCheck(), subject: RuleSubject.MEMBER_SINCE})).toBe(true)
        expect(readsMemberData({...emptyCheck(), subject: RuleSubject.AGE})).toBe(true)
        expect(readsMemberData({...emptyCheck(), subject: null})).toBe(false)
    })
})
