/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import se.laz.casual.api.CasualRuntimeException;

public class CasualServiceRegistrationException extends CasualRuntimeException
{
    private static final long serialVersionUID = 1L;

    public CasualServiceRegistrationException(String message)
    {
        super(message);
    }

    public CasualServiceRegistrationException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
