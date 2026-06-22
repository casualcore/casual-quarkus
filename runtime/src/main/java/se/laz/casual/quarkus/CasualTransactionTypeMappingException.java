/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import se.laz.casual.api.CasualRuntimeException;

/**
 * Thrown when a Jakarta {@code @Transactional} type cannot be mapped to a Casual transaction type.
 */
public class CasualTransactionTypeMappingException extends CasualRuntimeException
{
    private static final long serialVersionUID = 1L;

    public CasualTransactionTypeMappingException(String message)
    {
        super(message);
    }
}
