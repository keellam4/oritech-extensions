// 3D paint viewer for the docs site (loaded by paint-viewer.js only on pages that have one). tools/wiki/build_site.py writes <div class="paint-viewer"> for pages with
// `paint_viewer:` frontmatter, plus the models/textures under assets/viewer/ (tools/wiki/viewer.py).
// Draws Minecraft block models (cuboid elements) with three.js: drag to rotate, pick a block, pick a paint.
import * as THREE from './vendor/three.module.min.js';

const FACE_CORNERS = {
  // Corners in texture order: top-left, top-right, bottom-right, bottom-left (as seen from outside).
  north: (a, b) => [[b[0], b[1], a[2]], [a[0], b[1], a[2]], [a[0], a[1], a[2]], [b[0], a[1], a[2]]],
  south: (a, b) => [[a[0], b[1], b[2]], [b[0], b[1], b[2]], [b[0], a[1], b[2]], [a[0], a[1], b[2]]],
  west: (a, b) => [[a[0], b[1], a[2]], [a[0], b[1], b[2]], [a[0], a[1], b[2]], [a[0], a[1], a[2]]],
  east: (a, b) => [[b[0], b[1], b[2]], [b[0], b[1], a[2]], [b[0], a[1], a[2]], [b[0], a[1], b[2]]],
  up: (a, b) => [[a[0], b[1], a[2]], [b[0], b[1], a[2]], [b[0], b[1], b[2]], [a[0], b[1], b[2]]],
  down: (a, b) => [[a[0], a[1], b[2]], [b[0], a[1], b[2]], [b[0], a[1], a[2]], [a[0], a[1], a[2]]],
};
// Vanilla's default UVs when a face doesn't give one.
const DEFAULT_UV = {
  down: (a, b) => [a[0], 16 - b[2], b[0], 16 - a[2]],
  up: (a, b) => [a[0], a[2], b[0], b[2]],
  north: (a, b) => [16 - b[0], 16 - b[1], 16 - a[0], 16 - a[1]],
  south: (a, b) => [a[0], 16 - b[1], b[0], 16 - a[1]],
  west: (a, b) => [a[2], 16 - b[1], b[2], 16 - a[1]],
  east: (a, b) => [16 - b[2], 16 - b[1], 16 - a[2], 16 - a[1]],
};
const AXES = { x: new THREE.Vector3(1, 0, 0), y: new THREE.Vector3(0, 1, 0), z: new THREE.Vector3(0, 0, 1) };
// Fallback names; the page passes Oritech's own names in data-names (read from its lang file at build time).
const PAINT_NAMES = {
  orange: 'Unpainted', camo: 'Camo', diamond: 'Adamant', fluxite: 'Fluxite', white: 'White',
  industrial: 'Industrial', netherite: 'Netherite', redstone: 'Redstone', sculk: 'Sculk',
};

const loader = new THREE.TextureLoader();
const textureCache = new Map();
function loadTexture(url) {
  if (!textureCache.has(url)) {
    const tex = loader.load(url);
    tex.magFilter = THREE.NearestFilter;
    tex.minFilter = THREE.NearestFilter;
    tex.generateMipmaps = false;
    tex.flipY = false;
    tex.colorSpace = THREE.SRGBColorSpace;
    textureCache.set(url, tex);
  }
  return textureCache.get(url);
}

function textureUrl(root, id, paint, painted) {
  const [ns, path] = id.split(':');
  const suffix = paint !== 'orange' && painted.has(id) ? `_paint_${paint}` : '';
  return `${root}assets/viewer/tex/${ns}/${path}${suffix}.png`;
}

// Shade like Minecraft: top brightest, bottom darkest, north/south brighter than east/west.
function shadeFor(n) {
  const y = n.y > 0 ? n.y * n.y : n.y * n.y * 0.5;
  return n.x * n.x * 0.6 + n.z * n.z * 0.8 + y;
}

function buildBlock(model, root, paint) {
  const group = new THREE.Group();
  const painted = new Set(model.painted || []);
  const blockRotation = new THREE.Euler(
    THREE.MathUtils.degToRad(-(model.x || 0)), THREE.MathUtils.degToRad(-(model.y || 0)), 0, 'YXZ');
  for (const el of model.elements) {
    const a = el.from, b = el.to;
    const rot = el.rotation;
    for (const [face, data] of Object.entries(el.faces)) {
      const corners = FACE_CORNERS[face](a, b).map(c => new THREE.Vector3(...c));
      if (rot && rot.angle) {
        const origin = new THREE.Vector3(...rot.origin);
        const angle = THREE.MathUtils.degToRad(rot.angle);
        const scale = rot.rescale ? 1 / Math.cos(Math.abs(angle)) : 1;
        for (const v of corners) {
          v.sub(origin).applyAxisAngle(AXES[rot.axis], angle);
          if (scale !== 1) for (const ax of 'xyz') if (ax !== rot.axis) v[ax] *= scale;
          v.add(origin);
        }
      }
      for (const v of corners) v.subScalar(8).applyEuler(blockRotation);

      const uv = data.uv || DEFAULT_UV[face](a, b);
      let uvs = [[uv[0], uv[1]], [uv[2], uv[1]], [uv[2], uv[3]], [uv[0], uv[3]]];
      const turns = ((data.rotation || 0) / 90) % 4;
      for (let i = 0; i < turns; i++) uvs = [uvs[3], uvs[0], uvs[1], uvs[2]];

      const normal = new THREE.Vector3().subVectors(corners[3], corners[0])
        .cross(new THREE.Vector3().subVectors(corners[1], corners[0])).normalize();
      const light = el.shade === false ? 1 : shadeFor(normal);

      const geometry = new THREE.BufferGeometry();
      const order = [0, 3, 2, 0, 2, 1];
      geometry.setAttribute('position', new THREE.Float32BufferAttribute(order.flatMap(i => corners[i].toArray()), 3));
      geometry.setAttribute('uv', new THREE.Float32BufferAttribute(order.flatMap(i => [uvs[i][0] / 16, uvs[i][1] / 16]), 2));
      geometry.setAttribute('color', new THREE.Float32BufferAttribute(order.flatMap(() => [light, light, light]), 3));
      const material = new THREE.MeshBasicMaterial({
        map: loadTexture(textureUrl(root, data.texture, paint, painted)),
        vertexColors: true, transparent: true, alphaTest: 0.05, side: THREE.DoubleSide,
      });
      const mesh = new THREE.Mesh(geometry, material);
      mesh.userData.texture = data.texture;
      group.add(mesh);
    }
  }
  group.userData.painted = painted;
  return group;
}

function repaint(group, root, paint) {
  for (const mesh of group.children) {
    mesh.material.map = loadTexture(textureUrl(root, mesh.userData.texture, paint, group.userData.painted));
    mesh.material.needsUpdate = true;
  }
}

/**
 * Sizes the stage and canvas with inline styles so the layout never depends on the stylesheet. Without this, a
 * stale cached oritech.css (no viewer rules) let the canvas size the stage, the resize handler grow the canvas
 * to match, and so on forever: an endless page that could crash the browser.
 */
export function lockLayout(stage, canvas, height) {
  Object.assign(stage.style, { position: 'relative', overflow: 'hidden', height: `${height}px` });
  Object.assign(canvas.style, { position: 'absolute', inset: '0', width: '100%', height: '100%', display: 'block' });
}

export function setup(container) {
  if (container.dataset.ready) return;
  container.dataset.ready = '1';
  const root = container.dataset.root || '';
  const blocks = JSON.parse(container.dataset.blocks);
  const swatches = JSON.parse(container.dataset.swatches);
  const names = { ...PAINT_NAMES, ...JSON.parse(container.dataset.names || '{}') };

  container.innerHTML = `
    <div class="pv-stage"><span class="pv-hint">Drag to rotate</span></div>
    <div class="pv-controls">
      <div class="pv-blocks" role="tablist" aria-label="Block"></div>
      <div class="pv-paints" role="radiogroup" aria-label="Paint"></div>
    </div>`;
  const stage = container.querySelector('.pv-stage');
  const blockBar = container.querySelector('.pv-blocks');
  const paintBar = container.querySelector('.pv-paints');

  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  stage.prepend(renderer.domElement);
  lockLayout(stage, renderer.domElement, 320);
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(30, 1, 1, 200);
  const pivot = new THREE.Group();
  scene.add(pivot);

  let current = null, paint = 'orange', yaw = Math.PI / 4, pitch = 0.5, spinning = true, dragging = null, distance = 46;
  const cache = new Map();

  let size = '';
  function resize() {
    const w = Math.min(stage.clientWidth, 2000), h = Math.min(stage.clientHeight, 1200);
    if (!w || !h || `${w}x${h}` === size) return;
    size = `${w}x${h}`;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  }

  function frame() {
    const d = distance;
    camera.position.set(Math.sin(yaw) * Math.cos(pitch) * d, Math.sin(pitch) * d, Math.cos(yaw) * Math.cos(pitch) * d);
    camera.lookAt(0, 0, 0);
    renderer.render(scene, camera);
  }

  async function show(index) {
    const block = blocks[index];
    blockBar.querySelectorAll('button').forEach((b, i) => b.setAttribute('aria-selected', i === index));
    if (!cache.has(block.model)) {
      cache.set(block.model, fetch(root + block.model).then(r => r.json()));
    }
    const model = await cache.get(block.model);
    if (current) pivot.remove(current);
    current = buildBlock(model, root, block.paints.includes(paint) ? paint : 'orange');
    // Centre the block and zoom so it fills the view whatever its size (addons are smaller than a full block).
    const box = new THREE.Box3().setFromObject(current);
    current.position.sub(box.getCenter(new THREE.Vector3()));
    const radius = box.getBoundingSphere(new THREE.Sphere()).radius;
    distance = Math.max(radius, 6) / Math.sin(THREE.MathUtils.degToRad(camera.fov / 2)) * 0.92;
    pivot.add(current);
    paintBar.querySelectorAll('button').forEach(b => {
      b.disabled = b.dataset.paint !== 'orange' && !block.paints.includes(b.dataset.paint);
    });
  }

  blocks.forEach((block, i) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.setAttribute('role', 'tab');
    btn.title = block.name;
    btn.innerHTML = (block.icon ? `<img src="${root}${block.icon}" alt="">` : '') + `<span>${block.name}</span>`;
    btn.addEventListener('click', () => show(i));
    blockBar.append(btn);
  });
  for (const [name, colour] of Object.entries(swatches)) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.dataset.paint = name;
    btn.setAttribute('role', 'radio');
    btn.setAttribute('aria-checked', name === paint);
    btn.title = names[name] || name;
    btn.innerHTML = `<span class="pv-dot" style="background:${colour}"></span><span class="pv-label">${btn.title}</span>`;
    btn.addEventListener('click', () => {
      paint = name;
      paintBar.querySelectorAll('button').forEach(b => b.setAttribute('aria-checked', b === btn));
      if (current) repaint(current, root, paint);
    });
    paintBar.append(btn);
  }

  stage.addEventListener('pointerdown', e => {
    dragging = { x: e.clientX, y: e.clientY, yaw, pitch };
    spinning = false;
    stage.setPointerCapture(e.pointerId);
    container.classList.add('pv-touched');
  });
  stage.addEventListener('pointermove', e => {
    if (!dragging) return;
    yaw = dragging.yaw - (e.clientX - dragging.x) * 0.01;
    pitch = Math.max(-1.2, Math.min(1.2, dragging.pitch + (e.clientY - dragging.y) * 0.01));
  });
  const stop = () => { dragging = null; };
  stage.addEventListener('pointerup', stop);
  stage.addEventListener('pointercancel', stop);
  stage.addEventListener('dblclick', () => { spinning = true; });

  let visible = false;
  new IntersectionObserver(entries => { visible = entries[0].isIntersecting; }).observe(stage);
  new ResizeObserver(resize).observe(stage);
  resize();
  const tick = () => {
    if (!container.isConnected) return; // page changed (instant navigation)
    if (visible) {
      if (spinning) yaw += 0.006;
      frame();
    }
    requestAnimationFrame(tick);
  };
  show(0).then(() => requestAnimationFrame(tick));
}
