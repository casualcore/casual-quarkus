/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/** Logs the name of each method invocation bound to {@link ShouldLog}. */
@ShouldLog
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class ServiceLoggingInterceptor
{
    private static final System.Logger LOG = System.getLogger(ServiceLoggingInterceptor.class.getName());

    @AroundInvoke
    Object logInvocation(InvocationContext context) throws Exception
    {
        LOG.log(System.Logger.Level.INFO, () -> "Invoking service method " + context.getMethod().getName());
        return context.proceed();
    }
}
