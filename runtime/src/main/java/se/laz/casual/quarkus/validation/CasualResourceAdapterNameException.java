/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus.validation;

import io.quarkus.runtime.configuration.ConfigurationException;
import java.io.Serial;

/** Indicates that startup could not resolve exactly one Casual resource adapter named {@code casual}. */
public final class CasualResourceAdapterNameException extends ConfigurationException
{
    @Serial
    private static final long serialVersionUID = 1L;

    CasualResourceAdapterNameException(String message)
    {
        super(message);
    }
}
