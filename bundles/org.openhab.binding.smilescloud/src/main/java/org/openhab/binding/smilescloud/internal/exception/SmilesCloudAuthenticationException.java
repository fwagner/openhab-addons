/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.smilescloud.internal.exception;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Exception thrown when authentication with the S-Miles Cloud fails.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
public class SmilesCloudAuthenticationException extends SmilesCloudApiException {

    private static final long serialVersionUID = 1L;

    public SmilesCloudAuthenticationException(String message) {
        super(message);
    }

    public SmilesCloudAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
