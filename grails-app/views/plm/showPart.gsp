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
<g:form action="savePart" id="${plmPart.id}" method="POST" class="plm-inline">
<input type="hidden" name="lifecycleState" value="CHECKED_OUT"/>
<button class="mat-btn mat-btn-primary">${plmPart.lifecycleState?.toString() == 'RELEASED' ? 'Create Revision &amp; Check Out' : 'Check Out'}</button>
</g:form>
</g:if>
<g:if test="${canCancel}">
<g:form action="savePart" id="${plmPart.id}" method="POST" class="plm-inline">
<input type="hidden" name="lifecycleState" value="WIP"/>
<button class="mat-btn">Cancel Check Out</button>
</g:form>
</g:if>
<g:if test="${canRelease}">
<g:form action="releasePart" id="${plmPart.id}" method="POST" class="plm-inline">
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
<section class="mat-card">
<div class="mat-card-head">
<h2>Assembly Components</h2>
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
</body>
</html>