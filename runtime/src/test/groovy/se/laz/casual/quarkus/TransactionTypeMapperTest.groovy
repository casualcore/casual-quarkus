/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus

import se.laz.casual.network.messages.domain.TransactionType
import spock.lang.Specification

class TransactionTypeMapperTest extends Specification
{
    def "unmarshall returns #expected for Jakarta TxType '#input'"()
    {
        expect:
        TransactionTypeMapper.unmarshall(input) == expected

        where:
        input           | expected
        "MANDATORY"     | TransactionType.JOIN
        "NEVER"         | TransactionType.NONE
        "NOT_SUPPORTED" | TransactionType.NONE
        "REQUIRED"      | TransactionType.AUTOMATIC
        "REQUIRES_NEW"  | TransactionType.ATOMIC
        "SUPPORTS"      | TransactionType.JOIN
    }

    def "unmarshall returns NONE when transactionType is null"()
    {
        expect:
        TransactionTypeMapper.unmarshall(null) == TransactionType.NONE
    }

    def "unmarshall throws CasualTransactionTypeMappingException for unknown type"()
    {
        when:
        TransactionTypeMapper.unmarshall("UNKNOWN")

        then:
        def e = thrown(CasualTransactionTypeMappingException)
        e.message.contains("UNKNOWN")
    }
}
