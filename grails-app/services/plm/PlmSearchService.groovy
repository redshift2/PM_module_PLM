package plm

/** Search is now handled by the GORM-backed PlmController parts action. */
class PlmSearchService {
    PlmFreeCadSecurityService plmFreeCadSecurityService
    List<PlmFreeCadPart> search(String q) {
        if (!q?.trim()) return []
        String pattern = '%' + q.trim().toLowerCase(java.util.Locale.ROOT) + '%'
        List<PlmFreeCadPart> matches = PlmFreeCadPart.executeQuery('''
            FROM PlmFreeCadPart p WHERE p.active = true AND p.nextVersion IS NULL
              AND p.status <> :obsolete AND (
              LOWER(COALESCE(p.originalName,'')) LIKE :pattern OR LOWER(COALESCE(p.label,'')) LIKE :pattern
              OR LOWER(COALESCE(p.description1,'')) LIKE :pattern OR LOWER(COALESCE(p.description2,'')) LIKE :pattern
              OR LOWER(COALESCE(p.description3,'')) LIKE :pattern OR LOWER(COALESCE(p.material,'')) LIKE :pattern
              OR LOWER(COALESCE(p.supplier,'')) LIKE :pattern OR LOWER(COALESCE(p.dcn,'')) LIKE :pattern)
            ORDER BY p.label ASC''',[obsolete:PlmFreeCadPartStatus.OBSOLETE,pattern:pattern],[max:200])
        matches.findAll { plmFreeCadSecurityService.canDownloadFile(it) }
    }
}