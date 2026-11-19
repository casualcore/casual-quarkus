/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.example;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import se.laz.casual.example.user.handlers.CallMetadata;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@QuarkusTestResource(
        value = SelfConnectedCasualTestResource.class,
        restrictToAnnotatedClass = true)
class CallMetadataEndToEndTest
{
    @Test
    void breadcrumbHeaderIsAvailableToInboundService()
    {
        UUID breadcrumb = UUID.randomUUID();

        given()
                .header(CallMetadata.BREADCRUMB_HEADER, breadcrumb.toString())
                .when()
                .get("/casualcallersync/call-metadata")
                .then()
                .statusCode(200)
                .body(equalTo(breadcrumb.toString()));
    }
}
