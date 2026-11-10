// Loads the 3D paint viewer (and three.js) only on pages that contain one. Re-runs on Material's instant navigation.
// Same ?v= cache-buster as this file got from the page, so a deploy never mixes old and new viewer code.
const version = new URL(import.meta.url).search;
function init() {
  const viewers = document.querySelectorAll('.paint-viewer:not([data-ready])');
  if (viewers.length) import(`./paint-viewer-core.js${version}`).then(m => viewers.forEach(m.setup));
}
if (window.document$) window.document$.subscribe(init); else init();
