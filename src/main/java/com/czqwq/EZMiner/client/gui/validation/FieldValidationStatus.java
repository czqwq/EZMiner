package com.czqwq.EZMiner.client.gui.validation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Outcome of validating one config input field, used by the hover tooltip and the
 * red outline drawn by {@code EZMinerConfigGui}.
 */
@SideOnly(Side.CLIENT)
public enum FieldValidationStatus {

    /** The text parses and the value is inside every limit — it will be applied as typed. */
    VALID,

    /**
     * The text does not parse, or the value is outside the field's hard range, so the
     * Apply path would replace it (fallback value / lower bound).
     */
    INVALID,

    /**
     * The text parses and is inside the hard range, but the value is above the
     * server-side cap and would therefore be silently clamped on apply / sync.
     */
    EXCEEDS_SERVER_CAP;

    /** True for {@link #VALID}; the tooltip mark is {@code ✓} only in that case. */
    public boolean isValid() {
        return this == VALID;
    }
}
