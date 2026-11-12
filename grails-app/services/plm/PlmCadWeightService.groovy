package plm

import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource
import java.math.RoundingMode
import groovy.json.JsonSlurper

/** Calculate mass in kg from FreeCAD volume (mm3) and Materials Library density (kg/m3). */
class PlmCadWeightService {
    DataSource dataSource

    /** Parse FreeCAD density strings without silently assuming an unknown unit. */
    private static BigDecimal parseDensity(String value) {
        String s = value?.trim()?.replace('³', '^3')?.replace(' ', '')
        def m = (s =~ /(?i)^([+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:e[+-]?[0-9]+)?)(kg\/m\^?3|g\/cm\^?3|kg\/mm\^?3)?$/)
        if (!m.matches()) return null
        BigDecimal number = new BigDecimal(m.group(1))
        String unit = (m.group(2) ?: 'kg/m^3').toLowerCase()
        if (unit.startsWith('g/cm')) return number.multiply(new BigDecimal('1000'))
        if (unit.startsWith('kg/mm')) return number.multiply(new BigDecimal('1000000000'))
        number
    }

    BigDecimal calculate(BigDecimal volumeMm3, String materialName) {
        if (volumeMm3 == null || volumeMm3 < 0 || !materialName?.trim()) return null
        JdbcTemplate jdbc = new JdbcTemplate(dataSource)
        try {
            // Select mapped columns without assuming Grails/Hibernate's physical
            // naming convention (densityKgM3 may map to density_kgm3).
            List<Map<String, Object>> rows = jdbc.queryForList(
                'SELECT * FROM pm_material WHERE LOWER(name) = LOWER(?)',
                materialName.trim())
            if (rows.size() != 1) {
                log.warn('CAD weight: material lookup returned ' + rows.size() + ' matches for ' + materialName)
                return null
            }
            Map row = rows[0]
            // JDBC column labels differ across databases. Normalize their names.
            Map normalized = [:]
            row.each { key, value ->
                normalized[key.toString().toLowerCase(java.util.Locale.ROOT).replaceAll(/[^a-z0-9]/, '')] = value
            }
            if (normalized['active'] != null && !Boolean.valueOf(normalized['active'].toString())) {
                log.warn('CAD weight: material inactive: ' + materialName)
                return null
            }
            BigDecimal density = null
            Object rawProperties = normalized['propertiesjson']
            try {
                Map properties = rawProperties ? (Map) new JsonSlurper().parseText(rawProperties.toString()) : [:]
                Object raw = properties['Models.LinearElastic.Density']
                if (raw != null) density = parseDensity(raw.toString())
            } catch (Exception ex) {
                log.warn('CAD weight: invalid material properties for ' + materialName + ': ' + ex.message)
            }
            if (density == null && normalized['densitykgm3'] != null) {
                try { density = new BigDecimal(normalized['densitykgm3'].toString()) }
                catch (NumberFormatException ignored) { }
            }
            if (density == null || density <= 0) {
                log.warn('CAD weight: no positive density for material ' + materialName)
                return null
            }
            return volumeMm3.multiply(density)
                .divide(new BigDecimal('1000000000'), 4, RoundingMode.HALF_UP)
        } catch (org.springframework.jdbc.BadSqlGrammarException ignored) {
            log.warn('CAD weight: material SQL lookup failed: ' + ignored.message)
            return null
        }
    }
}
