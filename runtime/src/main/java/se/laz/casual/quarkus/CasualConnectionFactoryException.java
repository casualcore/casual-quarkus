/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import se.laz.casual.api.CasualRuntimeException;

/**
 * Thrown when a connection factory configuration error is detected.
 */
public class CasualConnectionFactoryException extends CasualRuntimeException
{
    private static final long serialVersionUID = 1L;

    public CasualConnectionFactoryException(String message)
    {
        super(message);
    }
}
