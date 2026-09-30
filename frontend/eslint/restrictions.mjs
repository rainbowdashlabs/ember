/**
 * Shapes of code the frontend refuses, as `no-restricted-syntax` selectors with the message each one
 * reports. The config switches them on per area; the tests beside this file hold them to what they
 * must catch and what they must leave alone.
 */

/** A state type written inline, which wants a name of its own. */
export const INLINE_STATE_TYPE = {
    selector: "CallExpression[callee.name='ref']:matches([typeArguments.params.0.type='TSTypeLiteral'], [typeArguments.params.0.types.0.type='TSTypeLiteral'])",
    message: 'Inline object type in ref<>: give the type a name.',
}

/**
 * The generic "that did not work", which says neither what failed nor what to do about it.
 * `describeFailure` from `util/failure.ts` does, and `FailureAlert` renders what it says.
 */
export const GENERIC_FAILURE = {
    selector: "CallExpression:matches([callee.name=/^\\$?t$/], [callee.property.name='t'])[arguments.length=1][arguments.0.value='common.error']",
    message: 'Answers a failure with "something went wrong". Describe it: describeFailure(e, t) from util/failure.ts, rendered with FailureAlert.',
}

/**
 * A date formatted for the locale in place. `toLocaleString` also formats numbers, which pass the
 * locale alone, while a date always passes options after it.
 */
export const INLINE_DATE_FORMAT = {
    selector: "CallExpression:matches([callee.property.name=/^toLocale(Date|Time)String$/], [callee.property.name='toLocaleString'][arguments.length>1])",
    message: 'Inline toLocale date formatting: use the helpers in util/format.ts.',
}

/**
 * A story working out who to act as. Who a story acts as is settled once, at global setup, and
 * written down by id; a story asking the instance itself gets the first account matching a
 * description, which is the same person in every worker.
 */
export const STORY_CASTING = {
    selector: 'CallExpression:matches([callee.name=/^(accountWith|accountWithout|stationPeers|instanceAdmin)$/], [callee.property.name=/^(accountWith|accountWithout|stationPeers|instanceAdmin)$/])',
    message: 'A story works out who to act as. The cast settles that at global setup: read it from fixtures/cast.ts instead.',
}

const IDENTITY_FIELDS = '/^(email|lastName|firstName|username)$/'

/** An identity compared by address or name, both of which the stories rewrite. */
export const MUTABLE_IDENTITY = {
    selector: `BinaryExpression[operator=/^[!=]==$/]:matches([left.name=${IDENTITY_FIELDS}], [left.property.name=${IDENTITY_FIELDS}], [right.property.name=${IDENTITY_FIELDS}])`,
    message: 'An identity is compared by address or name, which the stories rewrite. Compare the id.',
}

/**
 * The same syntax restriction for the script and for the template of a component.
 *
 * @param restrictions the selectors with their messages
 * @returns the rules entry for both
 */
export function restrictSyntax(...restrictions) {
    return {
        'no-restricted-syntax': ['error', ...restrictions],
        'vue/no-restricted-syntax': ['error', ...restrictions],
    }
}
