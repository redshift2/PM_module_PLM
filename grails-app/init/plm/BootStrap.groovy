package plm

/**
 * Runs PLM's versioned migrations at application startup.
 * If your application already has plm.BootStrap, merge the init call.
 */
class BootStrap {
    PlmDatabaseMigrationService plmDatabaseMigrationService

    def init = { servletContext ->
        plmDatabaseMigrationService.migrate()
    }

    def destroy = { }
}
