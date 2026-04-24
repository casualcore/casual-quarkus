/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import se.laz.casual.api.service.CasualService;

/**
 * Fielded service implementation that echoes back the received fielded buffer
 */
@ApplicationScoped
public class FieldedServiceImpl implements FieldedService
{
    @CasualService(name = "echoFielded", category = "example")
    @Transactional(Transactional.TxType.REQUIRED)
    @Override
    public SimpleObject echoFielded(SimpleObject data)
    {
        return data;
    }
}
