<style>

.mat-page{font-family:system-ui,-apple-system,"Segoe UI",sans-serif;max-width:1450px;margin:0 auto;padding:24px;color:#263443;font-size:14px;line-height:1.5}.mat-page *{box-sizing:border-box}.mat-page a{color:#1768a8;text-decoration:none}.mat-page a:hover{text-decoration:underline}.mat-top{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:18px}.mat-page h1{font-size:27px;font-weight:650;margin:0}.mat-page h2{font-size:17px;margin:0;font-weight:650}.mat-actions{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.mat-card{background:#fff;border:1px solid #d4dce5;border-radius:7px;margin:16px 0;overflow:hidden;box-shadow:0 1px 3px #0000000c}.mat-card-head{background:#eef2f6;border-bottom:1px solid #d4dce5;padding:12px 16px}.mat-card-body{padding:16px}.mat-table-wrap{overflow-x:auto}.mat-page table{border-collapse:collapse;width:100%;min-width:760px;text-align:left}.mat-page th{background:#eef2f6;color:#34465b;font-weight:650;border-bottom:2px solid #ccd5df;padding:12px 14px;white-space:nowrap}.mat-page td{padding:12px 14px;border-bottom:1px solid #e2e8f0;vertical-align:middle}.mat-page tbody tr:nth-child(even){background:#f7f9fc}.mat-page tbody tr:hover{background:#eaf2f9}.mat-btn{display:inline-block;cursor:pointer;border:1px solid #aebdcc;background:#f7f9fb;color:#25405d!important;border-radius:5px;padding:7px 12px;font:inherit;text-decoration:none!important;white-space:nowrap}.mat-btn:hover{background:#e7eef5}.mat-btn-primary{background:#236fa6;border-color:#236fa6;color:#fff!important}.mat-btn-primary:hover{background:#165a8c;color:#fff!important}.mat-btn-danger{color:#ae2d36!important;border-color:#d9a1a5;background:#fff}.mat-page input,.mat-page select,.mat-page textarea{border:1px solid #b6c4d2;border-radius:4px;padding:8px 10px;font:inherit;max-width:100%;background:#fff;color:#24384d}.mat-page label{display:block;font-weight:600;margin-bottom:5px}.mat-field{margin:12px 0}.mat-field input,.mat-field select,.mat-field textarea{width:100%}.mat-muted{color:#687b8e}.mat-error{padding:12px;background:#ffeded;color:#a0202b;border-radius:4px;margin:12px 0}.mat-success{padding:12px;background:#e8f7ed;color:#176b3a;border-radius:4px;margin:12px 0}.mat-tabs{display:flex;flex-wrap:wrap;gap:6px;margin:18px 0 0}.mat-tabs a{padding:9px 14px;background:#eef2f6;border:1px solid #d4dce5;border-radius:6px 6px 0 0}.mat-tabs a.active{background:#236fa6;color:#fff}.mat-search{display:flex;gap:8px;flex-wrap:wrap;align-items:center}.mat-search input{min-width:220px;flex:1}.mat-subcats{display:flex;gap:8px;flex-wrap:wrap;margin:10px 0}.mat-pager{display:flex;gap:12px;align-items:center}.mat-description{max-width:500px;white-space:normal}.mat-property{font-family:ui-monospace,monospace;font-size:13px}.mat-row-actions{display:flex;gap:6px}.mat-count{padding:5px 10px;background:#edf3f8;border-radius:4px;color:#34516b}


/* Global Workspace dark theme — standalone, independent of Taack UI */
:root{color-scheme:dark}
html,body{margin:0!important;padding:0!important;background:#22272e!important;color:#f0f3f7!important;font-family:Arial,Helvetica,sans-serif!important;font-size:14px}
.mat-page{max-width:none!important;margin:0!important;padding:0 22px 30px!important;background:#22272e!important;color:#f0f3f7!important;font-family:Arial,Helvetica,sans-serif!important;font-size:14px!important;min-height:calc(100vh - 24px)}
.mat-page a{color:#74c1ff!important}.mat-page a:hover{color:#b8e2ff!important}
.mat-page h1{color:#fff!important;font-size:24px!important;font-weight:650!important}
.mat-page h2,.mat-page label{color:#f4f6fa!important}
.mat-top{background:#195d8f!important;margin:0 -22px 18px!important;padding:16px 22px!important;min-height:68px!important;align-items:center!important}
.mat-card-head .mat-top{background:transparent!important;margin:0!important;padding:0!important;min-height:0!important}
.mat-card{background:#2b3037!important;border:1px solid #48535f!important;border-radius:5px!important;box-shadow:none!important;margin:14px 0!important}
.mat-card-head{background:#363d46!important;border-bottom:1px solid #4b5662!important;padding:13px 15px!important}
.mat-card-body{background:#2b3037!important}
.mat-page table{background:#2b3037!important;color:#f0f3f7!important}
.mat-page th{background:#3b4551!important;color:#fff!important;border-bottom:2px solid #596779!important;padding:12px 13px!important}
.mat-page td{border-bottom:1px solid #48535f!important;color:#edf1f6!important;padding:12px 13px!important}
.mat-page tbody tr:nth-child(even){background:#313944!important}
.mat-page tbody tr:hover{background:#3b4857!important}
.mat-btn{background:#414c59!important;border:1px solid #657283!important;color:#fff!important;border-radius:4px!important;padding:8px 12px!important;box-shadow:none!important}
.mat-btn:hover{background:#526376!important;color:#fff!important}
.mat-btn-primary{background:#1d73ad!important;border-color:#2584c3!important;color:#fff!important}
.mat-btn-primary:hover{background:#2585c6!important}
.mat-btn-danger{background:#742b35!important;border-color:#a14852!important;color:#fff!important}
.mat-btn-danger:hover{background:#933540!important}
.mat-page input,.mat-page select,.mat-page textarea{background:#242b33!important;border:1px solid #637184!important;color:#f5f7fa!important;border-radius:4px!important}
.mat-page input::placeholder,.mat-page textarea::placeholder{color:#aab6c4!important}
.mat-page input:focus,.mat-page select:focus,.mat-page textarea:focus{outline:2px solid #3589c9!important;outline-offset:0}
.mat-tabs{margin:16px 0!important;gap:7px!important}
.mat-tabs a{background:#343e49!important;border:1px solid #596677!important;border-radius:4px!important;color:#e5ecf5!important;padding:10px 15px!important}
.mat-tabs a.active{background:#1e6da6!important;border-color:#3489c2!important;color:#fff!important}
.mat-tabs a:hover{background:#455467!important;color:#fff!important}
.mat-muted{color:#bdc8d4!important}.mat-count{background:#465463!important;color:#e8f3ff!important}
.mat-description{color:#e5eaf1!important}.mat-property{color:#e5edf7!important}
.mat-error{background:#632b34!important;color:#fff!important}.mat-success{background:#245a3b!important;color:#fff!important}
.mat-subcats{padding:8px 0!important}
.mat-row-actions{gap:7px!important}
@media(max-width:700px){.mat-page{padding:0 10px 20px!important}.mat-top{margin:0 -10px 12px!important;padding:12px 10px!important}.mat-page h1{font-size:20px!important}.mat-tabs{overflow-x:auto;flex-wrap:nowrap!important}.mat-tabs a{white-space:nowrap}}


/* Keep the blue header above any title emitted by the host Grails layout. */
.mat-page > .mat-top{position:fixed!important;top:0!important;left:0!important;right:0!important;margin:0!important;z-index:1000!important;min-height:68px!important}
.mat-page{padding-top:68px!important}
/* Sticky catalogue controls: header, category tabs, catalogue name, search and table headings. */
.mat-page{overflow:visible!important}
.mat-top{position:sticky!important;top:0;z-index:60}
.mat-tabs{position:sticky!important;top:var(--mat-header-bottom,68px);z-index:59;background:#22272e!important;padding:12px 0!important;margin:0!important}
.mat-card{overflow:visible!important}
.mat-card-head{position:sticky!important;top:var(--mat-tabs-bottom,130px);z-index:58}
.mat-card>.mat-card-body{position:sticky!important;top:var(--mat-card-head-bottom,188px);z-index:57;background:#2b3037!important;border-bottom:1px solid #48535f}
.mat-table-wrap{overflow:visible!important}
.mat-page thead th{position:sticky!important;top:var(--mat-search-bottom,260px);z-index:56}
/* The title inside the catalogue heading is not a separate sticky blue bar. */
.mat-card-head .mat-top{position:static!important}

.plm-badge{padding:4px 8px;border-radius:4px;background:#465463;color:#fff}.plm-preview{max-width:76px;max-height:66px;object-fit:contain}.plm-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px}.plm-grid p{margin:8px 0}.plm-inline{display:inline-block;margin-right:8px}.plm-pre{white-space:pre-wrap;overflow-wrap:anywhere}

/* Catalogue-only sticky stack: no hard-coded offsets or transparent gaps. */
.mat-page:has(.plm-catalogue){padding-top:var(--plm-nav-height,76px)!important}
.plm-catalogue > .mat-card-head{
  top:var(--plm-nav-height,76px)!important;
  z-index:103!important;
  background:#363d46!important;
  margin:0!important;
}
.plm-catalogue > .mat-card-body{
  top:var(--plm-heading-bottom,129px)!important;
  z-index:102!important;
  background:#2b3037!important;
  margin:0!important;
}
.plm-catalogue .mat-table-wrap thead th{
  top:var(--plm-search-bottom,205px)!important;
  z-index:101!important;
  background:#3b4551!important;
}

/* Part detail: keep action buttons immediately under the fixed blue navigation. */
.plm-detail-page > .plm-detail-actions {
  position: sticky !important;
  top: var(--plm-detail-nav-height, 68px) !important;
  z-index: 990 !important;
  background: #22272e !important;
  border-bottom: 1px solid #48535f !important;
  padding: 12px 0 !important;
  margin: 0 0 14px !important;
  min-height: 58px;
}
/* The catalogue's global sticky card rules must not affect detail sections. */
.plm-detail-page .mat-card > .mat-card-head {
  position: static !important;
}
.plm-detail-page .mat-card > .mat-card-body {
  position: static !important;
}
.plm-detail-page .mat-table-wrap thead th {
  position: static !important;
}

/* Catalogue: fixed, opaque control stack. Prevent table rows from showing above the title. */
.mat-page:has(.plm-catalogue) { padding-top:var(--plm-nav-height,76px)!important; }
.plm-catalogue { margin-top:0!important; padding-top:var(--plm-controls-height,132px)!important; }
.plm-catalogue > .mat-card-head {
  position:fixed!important; top:var(--plm-nav-height,76px)!important;
  left:22px!important; right:22px!important; width:auto!important;
  z-index:998!important; background:#363d46!important;
}
.plm-catalogue > .mat-card-body {
  position:fixed!important; top:var(--plm-heading-bottom,129px)!important;
  left:22px!important; right:22px!important; width:auto!important;
  z-index:997!important; background:#2b3037!important;
}
.plm-catalogue .mat-table-wrap thead th {
  position:sticky!important; top:var(--plm-search-bottom,205px)!important;
  z-index:996!important; background:#3b4551!important;
}
@media(max-width:700px){
  .plm-catalogue > .mat-card-head,.plm-catalogue > .mat-card-body{left:10px!important;right:10px!important}
}
</style>
<script>
(function () {
  function positionDetailActions() {
    var page = document.querySelector('.plm-detail-page');
    if (!page) return;
    var nav = page.querySelector(':scope > .mat-top');
    if (!nav) return;
    page.style.setProperty('--plm-detail-nav-height', Math.ceil(nav.getBoundingClientRect().height) + 'px');
  }
  document.addEventListener('DOMContentLoaded', positionDetailActions);
  window.addEventListener('resize', positionDetailActions);
})();
</script>