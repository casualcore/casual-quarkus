/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example.user.handlers;

import jakarta.enterprise.context.RequestScoped;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Holds metadata associated with one inbound Casual service call.
 */
@RequestScoped
public class CallMetadata
{
    /** Header that carries the breadcrumb between Casual domains. */
    public static final String BREADCRUMB_HEADER = "breadcrumb";

    private UUID breadcrumb;

    /**
     * Returns the breadcrumb supplied by the caller.
     *
     * @return the breadcrumb, or an empty value when the caller did not supply one
     */
    public Optional<UUID> getBreadcrumb()
    {
        return Optional.ofNullable(breadcrumb);
    }

    /**
     * Sets the breadcrumb for the current inbound call.
     *
     * @param breadcrumb the caller's breadcrumb
     * @throws NullPointerException if the breadcrumb is null
     */
    public void setBreadcrumb(UUID breadcrumb)
    {
        this.breadcrumb = Objects.requireNonNull(breadcrumb, "breadcrumb can not be null");
    }
}
