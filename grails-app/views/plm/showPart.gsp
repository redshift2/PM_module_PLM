<div class="pm-shell" data-pm-theme="dark">
<g:render template="/shared/pmShellStyles"/>
<g:render template="/shared/pmShellSidebar"/>
<div class="pm-main">
<g:render template="/shared/pmShellHeader" model="[moduleTitle:'PLM Part Details']"/>
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

<g:set var="plmPart" value="${part}"/>
<div class="mat-page plm-detail-page">

<g:if test="${flash.message}">
<div class="mat-success">${flash.message.encodeAsHTML()}</div>
</g:if>
<div class="mat-actions plm-detail-actions">
<g:link action="parts" class="mat-btn">← Parts</g:link>
<g:link action="editPartMetadata" id="${plmPart.id}" class="mat-btn mat-btn-primary">Edit Metadata</g:link>
<g:link action="editPartCategory" id="${plmPart.id}" class="mat-btn">Edit Category</g:link>
<g:link action="addComment" id="${plmPart.id}" class="mat-btn">Edit Comment</g:link>
<div class="plm-workspace-picker" id="plmWorkspacePicker">
  <button type="button" class="mat-btn mat-btn-primary" id="plmWorkspaceToggle" aria-expanded="false" aria-controls="plmWorkspaceMenu">Add to Workspace ▾</button>
  <div id="plmWorkspaceMenu" class="plm-workspace-menu" hidden>
    <form id="plmWorkspaceForm" method="post" action="/workspace/addPart">
      <input type="hidden" name="partId" value="${plmPart.id}"/>
      <input type="hidden" name="inventoryRevision" id="plmWorkspaceRevision"/>
      <label for="plmWorkspaceSelect">Workspace</label>
      <select id="plmWorkspaceSelect" name="id" required><option value="">Loading workspaces…</option></select>
      <button class="mat-btn mat-btn-primary" type="submit" id="plmWorkspaceSubmit">Add</button>
      <p id="plmWorkspaceMessage" role="status" aria-live="polite"></p>
    </form>
  </div>
</div>
<g:link action="downloadBinPart" id="${plmPart.id}" class="mat-btn">Download CAD ZIP</g:link>
</div>
<section class="mat-card">
<div class="mat-card-head">
<h2>Part Summary</h2>
</div>
<div class="mat-card-body plm-grid">
<div>
<img style="max-width:100%;max-height:260px" src="${createLink(controller:'plm',action:'previewPart',id:plmPart.id)}" alt="CAD preview"/>
</div>
<div>
<p>
<strong>Part:</strong> ${(plmPart.originalName ?: '—').encodeAsHTML()}</p>
<p>
<strong>CAD Version:</strong> ${(plmPart.majorVersion == null ? 1 : plmPart.majorVersion)}.${(plmPart.minorVersion == null ? 0 : plmPart.minorVersion)}</p>
<p>
<strong>Lifecycle:</strong> <span class="plm-badge">${plmPart.lifecycleState?.toString()?.encodeAsHTML() ?: 'WIP'}</span>
</p>
<p>
<strong>Checked out by:</strong> ${plmPart.checkedOutBy?.username?.encodeAsHTML() ?: '—'}</p>
<p>
<strong>Tags:</strong> ${plmPart.documentCategory?.tags*.name?.join(', ')?.encodeAsHTML() ?: '—'}</p>
</div>
</div>
</section>
<section class="mat-card">
<div class="mat-card-head">
<h2>Lifecycle</h2>
</div>
<div class="mat-card-body mat-actions">
<g:if test="${canCheckOut}">
<g:form uri="/plm/savePart/${plmPart.id}" method="POST" class="plm-inline">
<input type="hidden" name="lifecycleState" value="CHECKED_OUT"/>
<button class="mat-btn mat-btn-primary">${plmPart.lifecycleState?.toString() == 'RELEASED' ? 'Create Revision & Check Out' : 'Check Out'}</button>
</g:form>
</g:if>
<g:if test="${canCancel}">
<g:form uri="/plm/savePart/${plmPart.id}" method="POST" class="plm-inline">
<input type="hidden" name="lifecycleState" value="WIP"/>
<button class="mat-btn">Cancel Check Out</button>
</g:form>
</g:if>
<g:if test="${canRelease}">
<g:form uri="/plm/releasePart/${plmPart.id}" method="POST" class="plm-inline">
<button class="mat-btn mat-btn-primary">Release</button>
</g:form>
</g:if>
</div>
</section>
<section class="mat-card">
<div class="mat-card-head">
<h2>Metadata</h2>
</div>
<div class="mat-card-body plm-grid">
<div>
<p>
<strong>Description 1:</strong> ${plmPart.description1?.encodeAsHTML() ?: '—'}</p>
<p>
<strong>Description 2:</strong> ${plmPart.description2?.encodeAsHTML() ?: '—'}</p>
<p>
<strong>Description 3:</strong> ${plmPart.description3?.encodeAsHTML() ?: '—'}</p>
<p>
<strong>Material:</strong> ${plmPart.material?.encodeAsHTML() ?: '—'}</p>
<p>
<strong>Supplier:</strong> ${plmPart.supplier?.encodeAsHTML() ?: '—'}</p>
</div>
<div>
<p>
<strong>Cost:</strong> ${plmPart.cost ?: '—'}</p>
<p>
<strong>Weight:</strong> ${plmPart.weight ?: '—'}</p>
<p>
<strong>Surface Treatment:</strong> ${plmPart.surfaceTreatment?.encodeAsHTML() ?: '—'}</p>
<p>
<strong>LOM:</strong> ${plmPart.lom ?: '—'}</p>
<p>
<strong>DCN:</strong> ${plmPart.dcn?.encodeAsHTML() ?: '—'}</p>
</div>
</div>
</section>
<section class="mat-card">
<div class="mat-card-head">
<h2>Comments</h2>
</div>
<div class="mat-card-body plm-pre">${plmPart.comment?.encodeAsHTML() ?: 'No comments.'}</div>
</section>
<nav class="plm-detail-tabs" role="tablist" aria-label="Part information">
<button type="button" class="mat-btn mat-btn-primary" data-plm-tab="bom" aria-selected="true">BOM</button>
<button type="button" class="mat-btn" data-plm-tab="attachments" aria-selected="false">Attachments</button>
<button type="button" class="mat-btn" data-plm-tab="history" aria-selected="false">CAD History</button>
<button type="button" class="mat-btn" data-plm-tab="preview" aria-selected="false">3D Preview</button>
</nav>
<div id="plm-tab-preview" class="plm-tab-panel" hidden>
<section class="mat-card">
<div class="mat-card-head"><h2>3D Preview — CAD Version ${plmPart.majorVersion == null ? 1 : plmPart.majorVersion}.${plmPart.minorVersion == null ? 0 : plmPart.minorVersion}</h2></div>
<div class="mat-card-body">
<g:if test="${previewAvailable}">
  <div class="plm-preview-formats" style="margin-bottom:10px">
    <g:if test="${previewGlbAvailable}"><button type="button" class="mat-btn" data-preview-format="glb">GLB</button></g:if>
    <g:if test="${previewX3dAvailable}"><button type="button" class="mat-btn" data-preview-format="x3d">X3D</button></g:if>
  </div>
  <div id="plmModelHost"
       data-glb="${previewGlbAvailable ? createLink(controller:'plm', action:'previewGlb', id:plmPart.id) : ''}"
       data-x3d="${previewX3dAvailable ? createLink(controller:'plm', action:'previewX3d', id:plmPart.id) : ''}">
    <div id="plmGlbToolbar" class="plm-glb-toolbar" style="display:none;margin-bottom:8px;gap:6px;flex-wrap:wrap">
      <button type="button" class="mat-btn plm-glb-icon-btn" data-glb-view="fit" title="Fit model" aria-label="Fit model"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#22c55e" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="6" fill="#22c55e" fill-opacity="0.14" stroke="none"/><path d="M8 3H3v5M16 3h5v5M3 16v5h5M21 16v5h-5"/><path d="M9 9h6v6H9z"/></svg></button>
      <button type="button" class="mat-btn plm-glb-icon-btn" data-glb-view="iso" title="Isometric" aria-label="Isometric"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#2563eb" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polygon points="12,2 21,7 12,12 3,7" fill="#60a5fa" fill-opacity="0.50" stroke="none"/><polygon points="3,7 12,12 12,22 3,17" fill="#34d399" fill-opacity="0.35" stroke="none"/><polygon points="21,7 12,12 12,22 21,17" fill="#fbbf24" fill-opacity="0.40" stroke="none"/><path d="m12 2 9 5-9 5-9-5 9-5ZM3 7v10l9 5 9-5V7M12 12v10"/></svg></button>
      <button type="button" class="mat-btn plm-glb-icon-btn" data-glb-view="front" title="Front" aria-label="Front"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#06b6d4" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="5" y="5" width="14" height="14" rx="2" fill="#06b6d4" fill-opacity="0.16" stroke="none"/><rect x="4" y="4" width="16" height="16" rx="1"/><path d="M12 7v10M7 12h10"/></svg></button>
      <button type="button" class="mat-btn plm-glb-icon-btn" data-glb-view="rear" title="Rear" aria-label="Rear"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#f97316" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="5" y="5" width="14" height="14" rx="2" fill="#f97316" fill-opacity="0.16" stroke="none"/><rect x="4" y="4" width="16" height="16" rx="1"/><path d="m9 9 6 6m0-6-6 6"/></svg></button>
      <button type="button" class="mat-btn plm-glb-icon-btn" data-glb-view="top" title="Top" aria-label="Top"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#8b5cf6" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polygon points="12,2 21,7 12,12 3,7" fill="#60a5fa" fill-opacity="0.50" stroke="none"/><polygon points="3,7 12,12 12,22 3,17" fill="#34d399" fill-opacity="0.35" stroke="none"/><polygon points="21,7 12,12 12,22 21,17" fill="#fbbf24" fill-opacity="0.40" stroke="none"/><path d="m12 3 9 5-9 5-9-5 9-5ZM3 8v8l9 5 9-5V8"/><path d="m8 8 4-2 4 2-4 2-4-2Z"/></svg></button>
      <button type="button" class="mat-btn plm-glb-icon-btn" data-glb-view="bottom" title="Bottom" aria-label="Bottom"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#eab308" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polygon points="12,2 21,7 12,12 3,7" fill="#60a5fa" fill-opacity="0.50" stroke="none"/><polygon points="3,7 12,12 12,22 3,17" fill="#34d399" fill-opacity="0.35" stroke="none"/><polygon points="21,7 12,12 12,22 21,17" fill="#fbbf24" fill-opacity="0.40" stroke="none"/><path d="m12 3 9 5-9 5-9-5 9-5ZM3 8v8l9 5 9-5V8"/><path d="M8 16h8m-4-3v6"/></svg></button>
      <button type="button" class="mat-btn plm-glb-icon-btn" data-glb-view="left" title="Left" aria-label="Left"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#ec4899" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="5" y="5" width="14" height="14" rx="2" fill="#ec4899" fill-opacity="0.16" stroke="none"/><path d="M7 4h12v16H7zM3 8h4M3 16h4"/><path d="m11 12 4-4m-4 4 4 4"/></svg></button>
      <button type="button" class="mat-btn plm-glb-icon-btn" data-glb-view="right" title="Right" aria-label="Right"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#14b8a6" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="5" y="5" width="14" height="14" rx="2" fill="#14b8a6" fill-opacity="0.16" stroke="none"/><path d="M5 4h12v16H5zM17 8h4M17 16h4"/><path d="m13 12-4-4m4 4-4 4"/></svg></button>
      <button type="button" class="mat-btn plm-glb-icon-btn" id="plmGlbProjection" title="Switch to orthographic" aria-label="Switch to orthographic" aria-pressed="false"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#6366f1" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polygon points="12,2 21,7 12,12 3,7" fill="#60a5fa" fill-opacity="0.50" stroke="none"/><polygon points="3,7 12,12 12,22 3,17" fill="#34d399" fill-opacity="0.35" stroke="none"/><polygon points="21,7 12,12 12,22 21,17" fill="#fbbf24" fill-opacity="0.40" stroke="none"/><path d="m4 8 8-5 8 5-8 5-8-5ZM4 8v9l8 4 8-4V8M12 13v8"/></svg></button>
      <button type="button" class="mat-btn plm-glb-icon-btn" id="plmGlbWireframe" title="Show wireframe" aria-label="Show wireframe" aria-pressed="false"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#ef4444" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polygon points="12,2 21,7 12,12 3,7" fill="#60a5fa" fill-opacity="0.50" stroke="none"/><polygon points="3,7 12,12 12,22 3,17" fill="#34d399" fill-opacity="0.35" stroke="none"/><polygon points="21,7 12,12 12,22 21,17" fill="#fbbf24" fill-opacity="0.40" stroke="none"/><path d="m12 2 9 5-9 5-9-5 9-5ZM3 7v10l9 5 9-5V7M12 12v10M3 7l18 10M21 7 3 17"/></svg></button>
      <label style="display:inline-flex;align-items:center;gap:5px">Background <input type="color" id="plmGlbBackground" value="#1b232c"/></label>
    </div>
    <div id="plmModelViewer" style="display:none;width:100%;height:480px;background:#1b232c;border-radius:8px;position:relative;overflow:hidden" role="img" aria-label="Interactive 3D GLB preview"></div>
    <div id="plmX3dViewer" style="display:none;width:100%;height:480px;background:#1b232c;border-radius:8px"></div>
    <button type="button" id="plmX3dFit" class="mat-btn" style="display:none;margin-top:8px">Fit X3D Model</button>
  </div>
  <p>
    <g:if test="${previewGlbAvailable}"><g:link controller="plm" action="downloadPreviewGlb" id="${plmPart.id}" class="mat-btn">Download GLB</g:link></g:if>
    <g:if test="${previewX3dAvailable}"><g:link controller="plm" action="downloadPreviewX3d" id="${plmPart.id}" class="mat-btn">Download X3D</g:link></g:if>
  </p>
  <p id="plmPreviewStatus" role="status">Select the 3D Preview tab to load the model.</p>
</g:if>
<g:else><p>No 3D preview is available for this CAD version yet.</p></g:else>
<sec:ifAnyGranted roles="ROLE_ADMIN">
  <form action="${createLink(controller:'plm',action:'uploadPreviewGlb',id:plmPart.id)}" method="post" enctype="multipart/form-data">
    <label for="plmPreviewUpload">Upload GLB (administrator)</label>
    <input id="plmPreviewUpload" name="model" type="file" accept=".glb,model/gltf-binary" required/>
    <button type="submit" class="mat-btn">Upload Preview</button>
  </form>
</sec:ifAnyGranted>
  <form action="${createLink(controller:'plm',action:'uploadPreviewX3d',id:plmPart.id)}" method="post" enctype="multipart/form-data">
    <label for="plmPreviewX3dUpload">Upload X3D (administrator)</label>
    <input id="plmPreviewX3dUpload" name="model" type="file" accept=".x3d,model/x3d+xml" required/>
    <button type="submit" class="mat-btn">Upload X3D</button>
  </form>
</div>
</section>
</div>
<div id="plm-tab-attachments" class="plm-tab-panel" hidden>
<section class="mat-card">
<div class="mat-card-head"><h2>Attachments</h2></div>
<div class="mat-table-wrap">
<g:if test="${attachments}">
<table><thead><tr><th>File</th><th>Action</th></tr></thead><tbody>
<g:each in="${attachments}" var="attachmentRow">
<tr>
<td>${attachmentRow.name?.encodeAsHTML()}</td>
<td><g:link controller="plm" action="downloadBinCommentVersionFiles" id="${plmPart.id}" params="${[path:attachmentRow.name]}" class="mat-btn">Download</g:link></td>
</tr>
</g:each>
</tbody></table>
</g:if>
<g:else><div class="mat-card-body">No attachments.</div></g:else>
</div>
</section>
</div>
<div id="plm-tab-bom" class="plm-tab-panel">
<section class="mat-card">
<div class="mat-card-head">
<h2>BOM — Assembly Components</h2>
</div>
<div class="mat-table-wrap">
<table>
<thead>
<tr>
<th>Part</th>
<th>Link Version</th>
<th>Action</th>
</tr>
</thead>
<tbody>
<g:each in="${components}" var="componentRow">
<tr>
<td>${componentRow.label?.encodeAsHTML()}</td>
<td>${componentRow.linkVersion == null ? '—' : componentRow.linkVersion}</td>
<td><g:link controller="plm" action="showPart" id="${componentRow.id}" class="mat-btn">Open</g:link></td>
</tr>
</g:each>
</tbody>
</table>
</div>
</section>
</div>
<div id="plm-tab-history" class="plm-tab-panel" hidden>
<section class="mat-card">
<div class="mat-card-head">
<h2>CAD History</h2>
</div>
<div class="mat-table-wrap">
<table>
<thead>
<tr>
<th>Preview</th>
<th>CAD Version</th>
<th>Lifecycle</th>
<th>Updated</th>
<th>Download</th>
</tr>
</thead>
<tbody>
<g:each in="${cadHistory}" var="historyRow">
<tr>
<td><img style="max-width:125px;max-height:100px" alt="Historical CAD preview" src="${createLink(controller:'plm',action:'previewPart',id:plmPart.id,params:[partVersion:historyRow.index,timestamp:historyRow.previewTimestamp])}"/></td>
<td>${historyRow.version?.encodeAsHTML()}</td>
<td>${historyRow.lifecycle?.encodeAsHTML()}</td>
<td>${historyRow.updated ?: '—'}</td>
<td><g:link controller="plm" action="downloadBinPart" id="${plmPart.id}" params="${[partVersion:historyRow.index]}" class="mat-btn">Download ZIP</g:link></td>
</tr>
</g:each>
</tbody>
</table>
</div>
</section>
</div>
</div>
<script>
(function () {
  var buttons = document.querySelectorAll('[data-plm-tab]');
  function selectTab(name) {
    buttons.forEach(function (button) {
      var selected = button.getAttribute('data-plm-tab') === name;
      button.setAttribute('aria-selected', String(selected));
      button.classList.toggle('mat-btn-primary', selected);
    });
    document.querySelectorAll('.plm-tab-panel').forEach(function (panel) {
      panel.hidden = panel.id !== 'plm-tab-' + name;
    });
  }
  buttons.forEach(function (button) {
    button.addEventListener('click', function () { selectTab(button.getAttribute('data-plm-tab')); });
  });
  var previewLoaded = {};
  function loadPreview(format) {
    var host = document.getElementById('plmModelHost');
    if (!host) return;
    format = format || (host.getAttribute('data-glb') ? 'glb' : 'x3d');
    var url = host.getAttribute('data-' + format);
    if (!url) return;
    var viewer = document.getElementById('plmModelViewer');
    var x3dHost = document.getElementById('plmX3dViewer');
    var status = document.getElementById('plmPreviewStatus');
    var fitButton = document.getElementById('plmX3dFit');
    fitButton.style.display = format === 'x3d' ? 'inline-block' : 'none';
    viewer.style.display = format === 'glb' ? 'block' : 'none';
    document.getElementById('plmGlbToolbar').style.display = format === 'glb' ? 'flex' : 'none';
    x3dHost.style.display = format === 'x3d' ? 'block' : 'none';
    if (previewLoaded[format]) return;
    previewLoaded[format] = true;
    status.textContent = 'Loading ' + format.toUpperCase() + ' model…';
    if (format === 'glb') {
      if (window.plmLoadGlb) window.plmLoadGlb(url);
      else window.addEventListener('plm-glb-ready', function once() {
        window.removeEventListener('plm-glb-ready', once);
        window.plmLoadGlb(url);
      });
    } else {
      // X_ITE supports XML X3D directly, without GLB conversion.
      var canvas = document.createElement('x3d-canvas');
      canvas.setAttribute('src', url);
      canvas.setAttribute('contentScale', 'auto');
      canvas.setAttribute('splashScreen', 'false');
      canvas.style.cssText = 'display:block;width:100%;height:100%';
      x3dHost.appendChild(canvas);
      // X3D geometry is often in millimetres and far outside the default camera.
      // Fit the scene after it loads and after the canvas has a visible size.
      function fitScene() {
        try {
          var browser = canvas.browser || (typeof canvas.getBrowser === 'function' && canvas.getBrowser());
          if (browser && typeof browser.viewAll === 'function') {
            browser.viewAll();
            status.textContent = 'X3D model loaded. Camera fitted to model.';
          } else {
            status.textContent = 'X3D viewer loaded. Use Fit X3D Model to frame the geometry.';
          }
        } catch (error) {
          status.textContent = 'X3D camera fit unavailable: ' + error.message;
        }
      }
      function scheduleFit() {
        requestAnimationFrame(function () {
          requestAnimationFrame(fitScene);
        });
      }
      fitButton.onclick = scheduleFit;
      canvas.addEventListener('load', scheduleFit);
      function ready() {
        status.textContent = 'X3D viewer initialized. Loading scene…';
        // Some X_ITE versions fire load before the script onload callback.
        setTimeout(scheduleFit, 350);
      }
      if (window.X3D) { ready(); return; }
      var script = document.createElement('script');
      script.src = 'https://cdn.jsdelivr.net/npm/x_ite/dist/x_ite.min.js';
      script.onload = ready;
      script.onerror = function () { status.textContent = 'X3D viewer library could not be loaded.'; previewLoaded.x3d = false; };
      document.head.appendChild(script);
    }
  }
  document.querySelectorAll('[data-preview-format]').forEach(function(button) {
    button.addEventListener('click', function () { loadPreview(button.getAttribute('data-preview-format')); });
  });
  buttons.forEach(function(button) {
    button.addEventListener('click', function () { if (button.getAttribute('data-plm-tab') === 'preview') loadPreview(); });
  });
  if (window.location.hash === '#plm-tab-preview') { selectTab('preview'); loadPreview(); }
  else selectTab('bom');
})();
</script>
<style>
.plm-glb-icon-btn{display:inline-flex;align-items:center;justify-content:center;min-width:38px;height:38px;padding:7px;vertical-align:middle;cursor:pointer}
.plm-glb-icon-btn svg{display:block;flex:none;pointer-events:none}
.plm-glb-icon-btn{background:linear-gradient(180deg,#ffffff,#eef3fa);border:1px solid #b8c7d9;border-radius:7px;box-shadow:0 1px 2px #0002}
.plm-glb-icon-btn:hover{background:#e0edff;border-color:#7ca9e8}
.plm-glb-icon-btn[aria-pressed="true"]{background:#dbeafe;border-color:#3b82f6;box-shadow:inset 0 0 0 1px #3b82f6}
.plm-glb-icon-btn[aria-pressed="true"]{outline:2px solid currentColor;outline-offset:-3px}
.plm-glb-icon-btn:focus-visible{outline:2px solid currentColor;outline-offset:2px}
.plm-workspace-picker{position:relative;display:inline-block}
.plm-workspace-menu{position:absolute;top:calc(100% + 6px);left:0;z-index:1000;min-width:280px;padding:14px;background:#202c3b;color:#fff;border:1px solid #526478;border-radius:6px;box-shadow:0 6px 22px #0006}
.plm-workspace-menu[hidden]{display:none}
.plm-workspace-menu label{display:block;margin-bottom:6px}
.plm-workspace-menu select{display:block;width:100%;margin-bottom:10px;padding:8px;background:#152130;color:#fff;border:1px solid #526478;border-radius:4px}
#plmWorkspaceMessage{margin:8px 0 0;white-space:normal;font-size:0.9em}
</style>
<script>
(function(){
  var picker=document.getElementById('plmWorkspacePicker');
  var toggle=document.getElementById('plmWorkspaceToggle');
  var menu=document.getElementById('plmWorkspaceMenu');
  var form=document.getElementById('plmWorkspaceForm');
  var select=document.getElementById('plmWorkspaceSelect');
  var revision=document.getElementById('plmWorkspaceRevision');
  var message=document.getElementById('plmWorkspaceMessage');
  var submit=document.getElementById('plmWorkspaceSubmit');
  var loaded=false;
  function syncRevision(){var opt=select.options[select.selectedIndex];revision.value=opt ? opt.getAttribute('data-revision')||'' : '';}
  select.addEventListener('change',syncRevision);
  toggle.addEventListener('click',async function(){
    var opening=menu.hidden;
    menu.hidden=!opening;toggle.setAttribute('aria-expanded',String(opening));
    if(!opening||loaded)return;
    message.textContent='';
    try{
      var response=await fetch('/workspace/editableWorkspaces',{credentials:'same-origin',headers:{'Accept':'application/json'}});
      if(!response.ok)throw new Error('Unable to load workspaces (HTTP '+response.status+')');
      var workspaces=await response.json();
      select.replaceChildren();
      workspaces.forEach(function(w){var opt=document.createElement('option');opt.value=w.id;opt.textContent=w.name;opt.setAttribute('data-revision',w.inventoryRevision==null?'':w.inventoryRevision);select.appendChild(opt);});
      if(!workspaces.length){message.textContent='No editable workspaces are available.';submit.disabled=true;}
      else{submit.disabled=false;syncRevision();}
      loaded=true;
    }catch(e){message.textContent=e.message;select.replaceChildren();}
  });
  document.addEventListener('click',function(e){if(!picker.contains(e.target)){menu.hidden=true;toggle.setAttribute('aria-expanded','false');}});
  form.addEventListener('submit',async function(e){
    e.preventDefault();submit.disabled=true;message.textContent='Adding part…';
    try{
      var response=await fetch(form.action,{method:'POST',body:new FormData(form),credentials:'same-origin',headers:{'Accept':'application/json','X-Requested-With':'XMLHttpRequest'}});
      var result=await response.json();
      if(!response.ok||!result.success)throw new Error(result.message||'Unable to add part');
      message.textContent='Part added to '+result.workspaceName+'.';
    }catch(err){message.textContent=err.message;}
    finally{submit.disabled=false;}
  });
})();
</script>

<script type="importmap">
{"imports":{"three":"https://cdn.jsdelivr.net/npm/three@0.170.0/build/three.module.js","three/addons/":"https://cdn.jsdelivr.net/npm/three@0.170.0/examples/jsm/"}}
</script>
<script type="module">
import * as THREE from 'three';
import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js';
import {OrbitControls} from 'three/addons/controls/OrbitControls.js';

const host = document.getElementById('plmModelViewer');
const status = document.getElementById('plmPreviewStatus');
if (host) {
  const scene = new THREE.Scene();
  const renderer = new THREE.WebGLRenderer({antialias:true, alpha:false});
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.25;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  host.appendChild(renderer.domElement);
  scene.background = new THREE.Color('#1b232c');
  scene.add(new THREE.HemisphereLight(0xffffff, 0x667788, 2.3));
  const key = new THREE.DirectionalLight(0xffffff, 3.0);
  key.position.set(3, 5, 7); key.castShadow = true; scene.add(key);
  const fill = new THREE.DirectionalLight(0xa9c9ff, 1.5);
  fill.position.set(-5, 2, -4); scene.add(fill);
  const rim = new THREE.DirectionalLight(0xffffff, 1.1);
  rim.position.set(1, -3, -5); scene.add(rim);
  let camera = new THREE.PerspectiveCamera(45, 1, 0.01, 1000000);
  const controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true; controls.dampingFactor = 0.08;
  controls.screenSpacePanning = true;
  let model = null, center = new THREE.Vector3(), radius = 1, wireframe = false, ortho = false;
  const viewDirs = {
    iso:[1, -1, 1], front:[0, -1, 0], rear:[0, 1, 0],
    top:[0, 0, 1], bottom:[0, 0, -1], left:[-1, 0, 0], right:[1, 0, 0]
  };
  // FreeCAD uses Z-up; keep all named views in FreeCAD coordinates.
  camera.up.set(0, 0, 1);
  function resize() {
    const w = host.clientWidth || 800, h = host.clientHeight || 480;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    if (camera.isOrthographicCamera) {
      const half = radius * 1.9;
      camera.left = -half * w / h; camera.right = half * w / h;
      camera.top = half; camera.bottom = -half;
    }
    camera.updateProjectionMatrix();
  }
  function setView(name) {
    if (!model) return;
    const d = new THREE.Vector3(...(viewDirs[name] || viewDirs.iso)).normalize();
    const distance = Math.max(radius * 3.4, 0.01);
    camera.position.copy(center).addScaledVector(d, distance);
    camera.up.set(0, 0, 1);
    if (name === 'top' || name === 'bottom') camera.up.set(0, 1, 0);
    controls.target.copy(center); controls.update();
    camera.near = Math.max(radius / 10000, 0.00001);
    camera.far = Math.max(radius * 1000, 100);
    resize();
  }
  function toggleProjection() {
    if (!model) return;
    const position = camera.position.clone(), target = controls.target.clone();
    const w = host.clientWidth || 800, h = host.clientHeight || 480;
    ortho = !ortho;
    camera = ortho ? new THREE.OrthographicCamera(-radius*1.9*w/h, radius*1.9*w/h, radius*1.9, -radius*1.9, 0.00001, radius*1000)
                   : new THREE.PerspectiveCamera(45, w/h, 0.00001, radius*1000);
    camera.position.copy(position); camera.up.set(0,0,1);
    controls.object = camera; controls.target.copy(target); controls.update();
    const projectionButton = document.getElementById('plmGlbProjection');
    const projectionLabel = ortho ? 'Switch to perspective' : 'Switch to orthographic';
    projectionButton.title = projectionLabel; projectionButton.setAttribute('aria-label', projectionLabel);
    projectionButton.setAttribute('aria-pressed', String(ortho));
    resize();
  }
  document.querySelectorAll('[data-glb-view]').forEach(b => b.addEventListener('click', () => setView(b.dataset.glbView === 'fit' ? 'iso' : b.dataset.glbView)));
  document.getElementById('plmGlbProjection').addEventListener('click', toggleProjection);
  document.getElementById('plmGlbWireframe').addEventListener('click', function () {
    wireframe = !wireframe;
    if (model) model.traverse(o => {
      if (!o.isMesh) return;
      (Array.isArray(o.material) ? o.material : [o.material]).forEach(m => { if(m) {m.wireframe = wireframe; m.needsUpdate = true;} });
    });
    const wireLabel = wireframe ? 'Show solid surfaces' : 'Show wireframe';
    this.title = wireLabel; this.setAttribute('aria-label', wireLabel);
    this.setAttribute('aria-pressed', String(wireframe));
  });
  document.getElementById('plmGlbBackground').addEventListener('input', e => {
    scene.background = new THREE.Color(e.target.value);
  });
  window.plmLoadGlb = function(url) {
    new GLTFLoader().load(url, gltf => {
      if (model) scene.remove(model);
      model = gltf.scene; scene.add(model);
      model.traverse(o => { if(o.isMesh) { o.castShadow = true; o.receiveShadow = true; } });
      const box = new THREE.Box3().setFromObject(model);
      if (box.isEmpty()) { status.textContent = 'GLB contains no visible geometry.'; return; }
      box.getCenter(center); radius = Math.max(box.getSize(new THREE.Vector3()).length() * 0.5, 0.001);
      setView('iso');
      status.textContent = 'GLB model loaded. Drag to orbit; scroll to zoom; right-drag to pan.';
    }, undefined, err => { status.textContent = 'GLB load failed: ' + (err.message || err); });
  };
  const observer = new ResizeObserver(resize); observer.observe(host);
  function render() { requestAnimationFrame(render); if (!host.offsetParent) return; controls.update(); renderer.render(scene, camera); }
  resize(); render();
  window.dispatchEvent(new Event('plm-glb-ready'));
}
</script>

</main>
</div>
<g:render template="/shared/pmShellScript"/>
</div>
