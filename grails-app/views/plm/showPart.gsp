<!doctype html>
<html>
<head>
<title>PLM Part</title>
<g:render template="theme"/>
</head>
<body>
<g:set var="plmPart" value="${part}"/>
<div class="mat-page plm-detail-page">
<g:render template="header" model="[pageTitle:plmPart.label ?: plmPart.originalName ?: 'PLM Part']"/>
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
  <div id="plmModelHost" data-src="${createLink(controller:'plm', action:'previewGlb', id:plmPart.id)}">
    <model-viewer id="plmModelViewer" camera-controls auto-rotate shadow-intensity="1" exposure="1" style="display:block;width:100%;height:480px;background:#1b232c;border-radius:8px" alt="3D preview of this part"></model-viewer>
  </div>
  <p><g:link controller="plm" action="downloadPreviewGlb" id="${plmPart.id}" class="mat-btn">Download GLB</g:link></p>
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
  var previewLoaded = false;
  function loadPreview() {
    if (previewLoaded) return;
    var host = document.getElementById('plmModelHost');
    if (!host) return;
    previewLoaded = true;
    var viewer = document.getElementById('plmModelViewer');
    var status = document.getElementById('plmPreviewStatus');
    var script = document.createElement('script');
    script.type = 'module';
    script.src = 'https://ajax.googleapis.com/ajax/libs/model-viewer/4.0.0/model-viewer.min.js';
    script.onload = function () { viewer.src = host.getAttribute('data-src'); status.textContent = 'Loading model…'; };
    script.onerror = function () { status.textContent = '3D viewer library could not be loaded.'; };
    document.head.appendChild(script);
    viewer.addEventListener('load', function () { status.textContent = 'Model loaded.'; });
    viewer.addEventListener('error', function () { status.textContent = 'Unable to display this GLB model.'; });
  }
  buttons.forEach(function(button) {
    button.addEventListener('click', function () { if (button.getAttribute('data-plm-tab') === 'preview') loadPreview(); });
  });
  if (window.location.hash === '#plm-tab-preview') { selectTab('preview'); loadPreview(); }
  else selectTab('bom');
})();
</script>
<style>
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
</body>
</html>