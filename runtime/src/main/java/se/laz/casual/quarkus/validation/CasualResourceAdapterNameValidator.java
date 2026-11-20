/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus.validation;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import se.laz.casual.jca.CasualConnectionFactory;

@ApplicationScoped
class CasualResourceAdapterNameValidator
{
    void validate(@Observes StartupEvent event, @Identifier("casual") Instance<CasualConnectionFactory> factory)
    {
        if (!factory.isResolvable())
        {
            throw new CasualResourceAdapterNameException("Exactly one Casual resource adapter must be named 'casual'. "
                    + "Configure quarkus.ironjacamar.casual.ra.kind=casual.");
        }
    }
}
