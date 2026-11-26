package com.smartchat.smartchat;

import org.junit.jupiter.api.Test;

/**
 * Starting the context runs every Flyway migration against MySQL and then Hibernate's
 * schema validation, so this test fails if the migrations and the entities ever drift apart.
 */
class SmartchatApplicationTests extends IntegrationTestBase {

    @Test
    void contextLoadsAndSchemaMatchesEntities() {
    }
}
