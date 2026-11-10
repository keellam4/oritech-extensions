// Loads the 3D viewers (and three.js) only on pages that contain one. Re-runs on Material's instant navigation.
//   .paint-viewer -> paint-viewer-core.js (rotate blocks, try paints)
//   .door-viewer  -> door-viewer-core.js  (animated hangar door with extensions)
// Same ?v= cache-buster as this file got from the page, so a deploy never mixes old and new viewer code.
const version = new URL(import.meta.url).search;
function init() {
  const paint = document.querySelectorAll('.paint-viewer:not([data-ready])');
  if (paint.length) import(`./paint-viewer-core.js${version}`).then(m => paint.forEach(m.setup));
  const door = document.querySelectorAll('.door-viewer:not([data-ready])');
  if (door.length) import(`./door-viewer-core.js${version}`).then(m => door.forEach(m.setup));
}
if (window.document$) window.document$.subscribe(init); else init();
