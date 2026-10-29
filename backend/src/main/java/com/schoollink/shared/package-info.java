/**
 * Cross-cutting types every module may use: error model, current-user principal, ids, clock.
 * Open module: its types are visible to all other modules.
 */
@ApplicationModule(displayName = "Shared", type = ApplicationModule.Type.OPEN)
package com.schoollink.shared;

import org.springframework.modulith.ApplicationModule;
