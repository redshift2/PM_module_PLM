package plm

/** Optional integration with the Server Administration event manager. */
class PlmEventBridgeService {
    def grailsApplication

    void report(String type, PlmFreeCadPart part, String actor, Map payload = [:]) {
        if (!part) return
        try {
            def context = grailsApplication.mainContext
            if (!context.containsBean('serverEventService')) {
                log.debug('Server Administration event service is not installed')
                return
            }
            context.getBean('serverEventService').record(
                type, 'plm', 'PlmFreeCadPart', part.id,
                actor, "${type}: ${part.label ?: part.originalName ?: part.id}", payload)
        } catch (Exception ex) {
            log.error("Unable to record PLM event ${type} for part ${part.id}", ex)
        }
    }
}
