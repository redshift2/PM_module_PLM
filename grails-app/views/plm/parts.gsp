<!doctype html><html><head><title>Parts Manager PLM</title><g:render template="theme"/></head><body><div class="mat-page"><g:render template="header" model="[pageTitle:'Parts Manager PLM']"/>
<g:if test="${flash.message}"><div class="mat-success">${flash.message.encodeAsHTML()}</div></g:if>
<section class="mat-card plm-catalogue"><div class="mat-card-head"><h2>Parts Catalogue <span class="mat-count">${total}</span></h2></div><div class="mat-card-body"><g:form controller="plm" action="${mode == 'obsolete' ? 'obsoleteParts' : mode == 'parents' ? 'parentParts' : 'parts'}" method="GET" class="mat-search"><input type="search" name="q" value="${query?.encodeAsHTML()}" placeholder="Search name, description, material, supplier, DCN…"/><button class="mat-btn mat-btn-primary">Search</button><g:link action="parts" class="mat-btn">Clear</g:link></g:form></div>
<div class="mat-table-wrap"><table><thead><tr><th>Preview</th><th>Part</th><th>CAD Version</th><th>Lifecycle</th><th>Description</th><th>Material</th><th>Tags</th><th>Action</th></tr></thead><tbody>
<g:each in="${parts}" var="part"><tr><td><g:link action="showPart" id="${part.id}"><img class="plm-preview" src="${createLink(controller:'plm',action:'previewPart',id:part.id)}" alt="Preview"/></g:link></td><td>${(part.label ?: part.originalName ?: 'Unnamed').encodeAsHTML()}<div class="mat-muted">${part.originalName?.encodeAsHTML()}</div></td><td>${part.displayVersion.encodeAsHTML()}</td><td><span class="plm-badge">${part.lifecycleState?.toString()?.encodeAsHTML() ?: 'WIP'}</span></td><td>${part.description1?.encodeAsHTML() ?: '—'}</td><td>${part.material?.encodeAsHTML() ?: '—'}</td><td>${part.documentCategory?.tags*.name?.join(', ')?.encodeAsHTML() ?: '—'}</td><td><g:link action="showPart" id="${part.id}" class="mat-btn mat-btn-primary">Open</g:link></td></tr></g:each>
<g:if test="${!parts}"><tr><td colspan="8" class="mat-muted">No matching parts.</td></tr></g:if></tbody></table></div></section>
<div class="mat-pager"><g:if test="${offset > 0}"><g:link action="${mode == 'obsolete' ? 'obsoleteParts' : mode == 'parents' ? 'parentParts' : 'parts'}" params="${[q:query,max:max,offset:Math.max(0,offset-max)]}" class="mat-btn">Previous</g:link></g:if><span>${total ? offset+1 : 0}–${Math.min(offset+max,total)} of ${total}</span><g:if test="${offset+max < total}"><g:link action="${mode == 'obsolete' ? 'obsoleteParts' : mode == 'parents' ? 'parentParts' : 'parts'}" params="${[q:query,max:max,offset:offset+max]}" class="mat-btn">Next</g:link></g:if></div></div>
<script>
(function () {
    function alignCatalogue() {
        var page = document.querySelector('.mat-page');
        var nav = page && page.querySelector(':scope > .mat-top');
        var catalogue = page && page.querySelector('.plm-catalogue');
        if (!nav || !catalogue) return;
        var heading = catalogue.querySelector('.mat-card-head');
        var search = catalogue.querySelector('.mat-card-body');
        if (!heading || !search) return;
        var navHeight = Math.ceil(nav.getBoundingClientRect().height);
        var headingHeight = Math.ceil(heading.getBoundingClientRect().height);
        var searchHeight = Math.ceil(search.getBoundingClientRect().height);
        page.style.setProperty('--plm-nav-height', navHeight + 'px');
        page.style.setProperty('--plm-heading-bottom', (navHeight + headingHeight) + 'px');
        page.style.setProperty('--plm-search-bottom', (navHeight + headingHeight + searchHeight) + 'px');
        page.style.setProperty('--plm-controls-height', (headingHeight + searchHeight) + 'px');
    }
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', alignCatalogue);
    } else { alignCatalogue(); }
    window.addEventListener('resize', alignCatalogue);
    if (window.ResizeObserver) {
        var observer = new ResizeObserver(alignCatalogue);
        var nav = document.querySelector('.mat-page > .mat-top');
        var heading = document.querySelector('.plm-catalogue > .mat-card-head');
        var search = document.querySelector('.plm-catalogue > .mat-card-body');
        [nav, heading, search].forEach(function (element) { if (element) observer.observe(element); });
    }
})();
</script>
</body></html>