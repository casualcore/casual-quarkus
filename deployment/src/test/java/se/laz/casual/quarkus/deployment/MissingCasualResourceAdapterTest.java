/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus.deployment;

import io.quarkus.test.QuarkusUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import se.laz.casual.quarkus.validation.CasualResourceAdapterNameException;

class MissingCasualResourceAdapterTest
{
    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withEmptyApplication()
            .setExpectedException(CasualResourceAdapterNameException.class);

    @Test
    void startupRequiresAnAdapterNamedCasual()
    {
        // QuarkusUnitTest verifies the startup failure before this method runs.
    }
}
