<div class="pm-shell" data-pm-theme="dark">
<g:render template="/shared/pmShellStyles"/>
<g:render template="/shared/pmShellSidebar"/>
<div class="pm-main">
<g:render template="/shared/pmShellHeader" model="[moduleTitle:'Parts Manager PLM']"/>
<main class="pm-content">
<g:render template="theme"/>
<style>
/* PLM content inherits the central shell theme. */
.pm-shell .mat-page { max-width:none!important; margin:0!important; padding:12px 10px 26px!important; background:transparent!important; color:var(--pm-text)!important; font-family:system-ui,-apple-system,"Segoe UI",sans-serif; }
.pm-shell .mat-page * {box-sizing:border-box;}
.pm-shell .mat-page h1,.pm-shell .mat-page h2,.pm-shell .mat-page h3,.pm-shell .mat-page label {color:var(--pm-text)!important;}
.pm-shell .mat-page a {color:#84c3ff;}
.pm-shell[data-pm-theme="light"] .mat-page a {color:#155e9c;}
.pm-shell .mat-page .mat-card {background:var(--pm-surface)!important; color:var(--pm-text)!important; border:1px solid var(--pm-border)!important; box-shadow:none!important;}
.pm-shell .mat-page .mat-card-head {background:var(--pm-sidebar)!important;color:var(--pm-text)!important;border-color:var(--pm-border)!important;}
.pm-shell .mat-page .mat-card-body {background:var(--pm-surface)!important;color:var(--pm-text)!important;}
.pm-shell .mat-page .mat-btn {background:var(--pm-surface)!important;color:var(--pm-text)!important;border:1px solid var(--pm-border)!important;}
.pm-shell .mat-page .mat-btn-primary {background:var(--pm-blue)!important;color:white!important;border-color:var(--pm-blue)!important;}
.pm-shell .mat-page input,.pm-shell .mat-page select,.pm-shell .mat-page textarea {background:var(--pm-bg)!important;color:var(--pm-text)!important;border:1px solid var(--pm-border)!important;}
.pm-shell .mat-page table th {background:var(--pm-sidebar)!important;color:var(--pm-text)!important;border-color:var(--pm-border)!important;}
.pm-shell .mat-page table td {color:var(--pm-text)!important;border-color:var(--pm-border)!important;}
.pm-shell[data-pm-theme="light"] .mat-page table tbody tr:nth-child(even) {background:#e9eff5!important;}
.pm-shell[data-pm-theme="light"] .mat-page table tbody tr:nth-child(odd) {background:#fff!important;}
.pm-shell[data-pm-theme="light"] .mat-page table tbody tr td,.pm-shell[data-pm-theme="light"] .mat-page table tbody tr td * {color:#253547!important;}
.pm-shell[data-pm-theme="light"] .mat-page table tbody tr td a {color:#155e9c!important;}
.pm-shell .mat-page .mat-muted {color:var(--pm-muted)!important;}
.pm-shell .mat-page .mat-actions {display:flex;flex-wrap:wrap;gap:8px;}
.pm-shell .mat-page .mat-top {position:static!important;background:transparent!important;}
.pm-shell .mat-page .plm-detail-actions {position:static!important;right:auto!important;top:auto!important;}
.pm-shell .mat-page .plm-catalogue {overflow-x:auto;}
@media(max-width:700px){.pm-shell .mat-page{padding:8px 0 20px!important;}}
</style>
<style>
/* Shared-shell PLM catalogue: restore normal document flow. */
.pm-shell .pm-main > .pm-content .mat-page:has(.plm-catalogue) {
  padding:0 !important; margin:0 !important; max-width:none !important;
}
.pm-shell .pm-main > .pm-content .plm-catalogue {
  padding:0 !important; margin:0 !important;
  position:static !important; overflow:visible !important;
}
.pm-shell .pm-main > .pm-content .plm-catalogue > .mat-card-head,
.pm-shell .pm-main > .pm-content .plm-catalogue > .mat-card-body {
  position:static !important; inset:auto !important;
  top:auto !important; left:auto !important; right:auto !important;
  width:auto !important; max-width:none !important;
  margin:0 !important; transform:none !important;
}
.pm-shell .pm-main > .pm-content .plm-catalogue .mat-table-wrap {overflow-x:auto !important;}
.pm-shell .pm-main > .pm-content .plm-catalogue thead th {
  position:static !important; top:auto !important;
}
.pm-shell .pm-main > .pm-content .plm-catalogue .mat-search {
  display:flex; align-items:center; gap:8px; flex-wrap:wrap;
}
.pm-shell .pm-main > .pm-content .plm-catalogue .mat-search input[type="search"] {
  flex:1 1 320px; min-width:0; width:auto;
}
.pm-shell[data-pm-theme="light"] .plm-catalogue .plm-badge {
  background:#d9e5f1 !important; color:#18344e !important;
  border:1px solid #91a8bc !important; font-weight:650;
}
.pm-shell[data-pm-theme="light"] .plm-catalogue a.mat-btn-primary,
.pm-shell[data-pm-theme="light"] .plm-catalogue a.mat-btn-primary *,
.pm-shell[data-pm-theme="light"] .plm-catalogue button.mat-btn-primary {
  color:#fff !important; background:#1764a5 !important;
  border-color:#1764a5 !important;
}
.pm-shell[data-pm-theme="light"] .plm-catalogue a.mat-btn-primary:hover {
  background:#104e83 !important;
}
</style>
<div class="mat-page">
<g:if test="${flash.message}"><div class="mat-success">${flash.message.encodeAsHTML()}</div></g:if>
<section class="mat-card plm-catalogue"><div class="mat-card-head"><h2>Parts Catalogue <span class="mat-count">${total}</span></h2></div><div class="mat-card-body"><g:form controller="plm" action="${mode == 'obsolete' ? 'obsoleteParts' : mode == 'parents' ? 'parentParts' : 'parts'}" method="GET" class="mat-search"><input type="search" name="q" value="${query?.encodeAsHTML()}" placeholder="Search name, description, material, supplier, DCN…"/><button class="mat-btn mat-btn-primary">Search</button><g:link action="parts" class="mat-btn">Clear</g:link></g:form></div>
<div class="mat-table-wrap"><table><thead><tr><th>Preview</th><th>Part</th><th>CAD Version</th><th>Lifecycle</th><th>Description</th><th>Material</th><th>Tags</th><th>Action</th></tr></thead><tbody>
<g:each in="${parts}" var="part"><tr><td><g:link action="showPart" id="${part.id}"><img class="plm-preview" src="${createLink(controller:'plm',action:'previewPart',id:part.id)}" alt="Preview"/></g:link></td><td>${(part.label ?: part.originalName ?: 'Unnamed').encodeAsHTML()}<div class="mat-muted">${part.originalName?.encodeAsHTML()}</div></td><td>${part.displayVersion.encodeAsHTML()}</td><td><span class="plm-badge">${part.lifecycleState?.toString()?.encodeAsHTML() ?: 'WIP'}</span></td><td>${part.description1?.encodeAsHTML() ?: '—'}</td><td>${part.material?.encodeAsHTML() ?: '—'}</td><td>${part.documentCategory?.tags*.name?.join(', ')?.encodeAsHTML() ?: '—'}</td><td><g:link action="showPart" id="${part.id}" class="mat-btn mat-btn-primary">Open</g:link></td></tr></g:each>
<g:if test="${!parts}"><tr><td colspan="8" class="mat-muted">No matching parts.</td></tr></g:if></tbody></table></div></section>
<div class="mat-pager"><g:if test="${offset > 0}"><g:link action="${mode == 'obsolete' ? 'obsoleteParts' : mode == 'parents' ? 'parentParts' : 'parts'}" params="${[q:query,max:max,offset:Math.max(0,offset-max)]}" class="mat-btn">Previous</g:link></g:if><span>${total ? offset+1 : 0}–${Math.min(offset+max,total)} of ${total}</span><g:if test="${offset+max < total}"><g:link action="${mode == 'obsolete' ? 'obsoleteParts' : mode == 'parents' ? 'parentParts' : 'parts'}" params="${[q:query,max:max,offset:offset+max]}" class="mat-btn">Next</g:link></g:if></div></div>


</main>
</div>
<g:render template="/shared/pmShellScript"/>
</div>
