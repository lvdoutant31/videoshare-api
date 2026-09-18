package com.yingliu.api.admin.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Redis session compatibility types retained while sessions created by older releases expire.
 *
 * <p>Do not use this class for new authorization decisions. Older admin sessions persisted an
 * {@code Authorization} value in Sa-Token's Jackson-serialized session map. Removing the class
 * made those sessions impossible to deserialize, which also prevented their owners from logging
 * in again. Keeping an inert type with the same binary name lets Sa-Token read the session; all
 * authorization data is now loaded from the database-backed permission cache.
 */
@Deprecated(forRemoval = true)
public final class AdminSessionContext {
    private AdminSessionContext() {
    }

    /** Legacy Jackson target; deliberately carries no authorization state. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Authorization {
        private boolean legacyCompatibilityValue = true;

        public Authorization() {
        }

        public boolean isLegacyCompatibilityValue() {
            return legacyCompatibilityValue;
        }

        public void setLegacyCompatibilityValue(boolean legacyCompatibilityValue) {
            this.legacyCompatibilityValue = legacyCompatibilityValue;
        }
    }
}
