<div class="pm-shell" data-pm-theme="dark">
<g:render template="/shared/pmShellStyles"/>
<g:render template="/shared/pmShellSidebar"/>
<div class="pm-main">
<g:render template="/shared/pmShellHeader" model="[moduleTitle:'Part Comments']"/>
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
<div class="mat-page"><section class="mat-card"><div class="mat-card-body"><g:form action="saveComment" id="${part.id}" method="POST"><div class="mat-field"><label>Comment</label><textarea name="comment" rows="12">${part.comment?.encodeAsHTML()}</textarea></div><button class="mat-btn mat-btn-primary">Save</button> <g:link action="showPart" id="${part.id}" class="mat-btn">Cancel</g:link></g:form></div></section></div>
</main>
</div>
<g:render template="/shared/pmShellScript"/>
</div>
