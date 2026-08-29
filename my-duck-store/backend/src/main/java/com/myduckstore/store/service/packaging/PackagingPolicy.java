package com.myduckstore.store.service.packaging;

import com.myduckstore.store.domain.PackageType;
import com.myduckstore.store.domain.ProtectionMaterial;
import com.myduckstore.store.domain.ShippingMode;
import com.myduckstore.warehouse.domain.Size;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Section 3b of the exercise: which box the ducks travel in, and what is stuffed in with them.
 *
 * <p>This is deliberately <em>not</em> a hierarchy of strategy objects. The mapping is total and
 * closed - five sizes by three shipping modes, all fifteen cases fixed by the specification - so an
 * exhaustive {@code switch} expression already buys the one guarantee a hierarchy would: adding a
 * {@code Size} or a {@code ShippingMode} becomes a <strong>compile error</strong> until every branch
 * is handled, rather than a silent fall-through. A class per case would add indirection and nothing
 * else. See the README for the full rationale, including why Decorator was rejected.
 *
 * <p>What <em>was</em> wrong was location: these rules used to live inside the pricing service. As
 * their own collaborator they have their own seam, and pricing no longer has an opinion about boxes.
 */
@Component
public class PackagingPolicy {

    /** XLarge/Large -> Wood; Medium -> Cardboard; Small/XSmall -> Plastic (rules 1-3). */
    public PackageType packageFor(Size size) {
        return switch (size) {
            case XLARGE, LARGE -> PackageType.WOOD;
            case MEDIUM        -> PackageType.CARDBOARD;
            case SMALL, XSMALL -> PackageType.PLASTIC;
        };
    }

    /**
     * Protection materials (rules 4-7).
     *
     * <ul>
     *   <li>Air: polystyrene balls for wood/cardboard; bubble-wrap bags for plastic.
     *   <li>Land: polystyrene balls, whatever the package.
     *   <li>Sea: moisture-absorbing beads + bubble-wrap bags, whatever the package.
     * </ul>
     *
     * <p>Air is the only mode where the package type changes the answer.
     */
    public List<ProtectionMaterial> protectionFor(PackageType packageType, ShippingMode mode) {
        return switch (mode) {
            case AIR  -> packageType == PackageType.PLASTIC
                    ? List.of(ProtectionMaterial.BUBBLE_WRAP_BAGS)
                    : List.of(ProtectionMaterial.POLYSTYRENE_BALLS);
            case LAND -> List.of(ProtectionMaterial.POLYSTYRENE_BALLS);
            case SEA  -> List.of(ProtectionMaterial.MOISTURE_ABSORBING_BEADS, ProtectionMaterial.BUBBLE_WRAP_BAGS);
        };
    }
}
