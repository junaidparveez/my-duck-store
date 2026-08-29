package com.myduckstore.store.service.pricing;

import com.myduckstore.store.web.dto.BreakdownLine;

import java.util.Optional;

/**
 * One pricing rule from section 3c of the exercise.
 *
 * <p>This is the Strategy interface. Every implementation is applied to every quote, in the order
 * {@link PricingEngine} declares, and each contributes <strong>at most one</strong> breakdown line -
 * which is what makes the itemized breakdown the API must return (section 3d) a property of the
 * design rather than a list someone maintains by hand.
 *
 * <p>Two contracts an implementation must honour:
 * <ul>
 *   <li>Return {@link Optional#empty()} when the rule does not apply, rather than a zero line. An
 *       order of exactly 100 units has no bulk-discount line at all, not a line worth $0.00.
 *   <li>Return the amount <strong>unrounded</strong>. {@link PricingEngine} rounds every line to
 *       cents in one place, so the invariant "the total is the exact sum of the rounded lines"
 *       cannot be broken by a rule that forgets.
 * </ul>
 *
 * <p>Amounts are signed: a discount returns a negative amount, so the total is always a plain sum.
 */
@FunctionalInterface
public interface PricingRule {

    Optional<BreakdownLine> apply(PricingContext context);
}
