// Loads the 3D paint viewer (and three.js) only on pages that contain one. Re-runs on Material's instant navigation.
function init() {
  const viewers = document.querySelectorAll('.paint-viewer:not([data-ready])');
  if (viewers.length) import('./paint-viewer-core.js').then(m => viewers.forEach(m.setup));
}
if (window.document$) window.document$.subscribe(init); else init();
