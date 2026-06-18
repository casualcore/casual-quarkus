/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import se.laz.casual.network.messages.domain.TransactionType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static se.laz.casual.network.messages.domain.TransactionType.ATOMIC;
import static se.laz.casual.network.messages.domain.TransactionType.AUTOMATIC;
import static se.laz.casual.network.messages.domain.TransactionType.JOIN;
import static se.laz.casual.network.messages.domain.TransactionType.NONE;

/**
 * Maps Jakarta {@code @Transactional} TxType values to Casual {@link TransactionType}.
 */
public final class TransactionTypeMapper
{
    private TransactionTypeMapper()
    {}
    private static final Map<String, TransactionType> mappings = new HashMap<>();
    static
    {
        mappings.put( "MANDATORY",        JOIN );
        mappings.put( "NEVER",            NONE );
        mappings.put( "NOT_SUPPORTED",    NONE );
        mappings.put( "REQUIRED",         AUTOMATIC );
        mappings.put( "REQUIRES_NEW",     ATOMIC );
        mappings.put( "SUPPORTS",         JOIN );
    }

    public static TransactionType map(final String transactionType)
    {
        // means that there is no transactional annotation
        if(null == transactionType)
        {
            return NONE;
        }
        var type = mappings.get(transactionType);
        if(null == type)
        {
            throw new CasualTransactionTypeMappingException("unknown transaction type: " + transactionType);
        }
        return type;
    }

}
