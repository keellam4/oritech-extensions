// Animated hangar door for the docs site (loaded by paint-viewer.js on pages with a .door-viewer).
// Plays Oritech's own GeckoLib model + animations (assets/viewer/door/, written by tools/wiki/door.py) and moves
// this mod's extensions exactly like HangarDoorExtensionRenderer does in game.
import * as THREE from './vendor/three.module.min.js';

/** Inline sizing so the canvas can never grow its container, whatever the stylesheet says (see paint-viewer-core.js). */
function lockLayout(stage, canvas, height) {
  Object.assign(stage.style, { position: 'relative', overflow: 'hidden', height: `${height}px` });
  Object.assign(canvas.style, { position: 'absolute', inset: '0', width: '100%', height: '100%', display: 'block' });
}

// ------------------------------------------------------------------ GeckoLib model

// Corners per face, top-left / top-right / bottom-right / bottom-left as seen from outside (Java orientation).
const FACE_CORNERS = {
  north: (a, b) => [[b[0], b[1], a[2]], [a[0], b[1], a[2]], [a[0], a[1], a[2]], [b[0], a[1], a[2]]],
  south: (a, b) => [[a[0], b[1], b[2]], [b[0], b[1], b[2]], [b[0], a[1], b[2]], [a[0], a[1], b[2]]],
  west: (a, b) => [[a[0], b[1], a[2]], [a[0], b[1], b[2]], [a[0], a[1], b[2]], [a[0], a[1], a[2]]],
  east: (a, b) => [[b[0], b[1], b[2]], [b[0], b[1], a[2]], [b[0], a[1], a[2]], [b[0], a[1], b[2]]],
  up: (a, b) => [[a[0], b[1], a[2]], [b[0], b[1], a[2]], [b[0], b[1], b[2]], [a[0], b[1], b[2]]],
  down: (a, b) => [[a[0], a[1], b[2]], [b[0], a[1], b[2]], [b[0], a[1], a[2]], [a[0], a[1], a[2]]],
};

function shadeFor(n) {
  const y = n.y > 0 ? n.y * n.y : n.y * n.y * 0.5;
  return n.x * n.x * 0.6 + n.z * n.z * 0.8 + y;
}

// Bedrock geometry is mirrored on X compared to Java; GeckoLib (and Blockbench) negate X, which we do too.
function cubeMesh(cube, material, texW, texH) {
  const inflate = cube.inflate || 0;
  const [ox, oy, oz] = cube.origin, [sx, sy, sz] = cube.size;
  const a = [-(ox + sx) - inflate, oy - inflate, oz - inflate];
  const b = [-ox + inflate, oy + sy + inflate, oz + sz + inflate];
  const positions = [], uvs = [], colors = [];
  for (const [face, f] of Object.entries(cube.uv || {})) {
    if (!FACE_CORNERS[face] || !f.uv_size) continue;
    const [u, v] = f.uv, [w, h] = f.uv_size;
    // Blockbench stores up/down per-face UVs reversed (from the far corner).
    const r = face === 'up' || face === 'down' ? [u + w, v + h, u, v] : [u, v, u + w, v + h];
    const tex = [[r[0], r[1]], [r[2], r[1]], [r[2], r[3]], [r[0], r[3]]];
    const corners = FACE_CORNERS[face](a, b).map(c => new THREE.Vector3(...c));
    const n = new THREE.Vector3().subVectors(corners[3], corners[0])
      .cross(new THREE.Vector3().subVectors(corners[1], corners[0])).normalize();
    const light = shadeFor(n);
    for (const i of [0, 3, 2, 0, 2, 1]) {
      positions.push(...corners[i].toArray());
      uvs.push(tex[i][0] / texW, tex[i][1] / texH);
      colors.push(light, light, light);
    }
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  geometry.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2));
  geometry.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3));
  const mesh = new THREE.Mesh(geometry, material);
  if (!cube.rotation) return mesh;
  // Cube rotation around its pivot; GeckoLib applies Z, then Y, then X (with X and Y negated for the mirror).
  const pivot = new THREE.Vector3(-cube.pivot[0], cube.pivot[1], cube.pivot[2]);
  const holder = new THREE.Group();
  holder.position.copy(pivot);
  holder.rotation.set(...[-cube.rotation[0], -cube.rotation[1], cube.rotation[2]].map(THREE.MathUtils.degToRad), 'ZYX');
  mesh.position.copy(pivot).negate();
  holder.add(mesh);
  return holder;
}

/** Builds bones as nested groups: outer group sits at the pivot (animated), inner group undoes the pivot. */
function buildGeo(json, material) {
  const geo = json['minecraft:geometry'][0];
  const texW = geo.description.texture_width, texH = geo.description.texture_height;
  const root = new THREE.Group();
  const bones = {};
  for (const bone of geo.bones) {
    const pivot = new THREE.Vector3(-(bone.pivot?.[0] || 0), bone.pivot?.[1] || 0, bone.pivot?.[2] || 0);
    const outer = new THREE.Group();
    const inner = new THREE.Group();
    inner.position.copy(pivot).negate();
    outer.add(inner);
    if (bone.rotation) {
      outer.rotation.set(...[-bone.rotation[0], -bone.rotation[1], bone.rotation[2]].map(THREE.MathUtils.degToRad), 'ZYX');
    }
    for (const cube of bone.cubes || []) inner.add(cubeMesh(cube, material, texW, texH));
    bones[bone.name] = { outer, inner, pivot, parent: bone.parent };
  }
  for (const [name, bone] of Object.entries(bones)) {
    const parent = bone.parent && bones[bone.parent];
    // A child's pivot is absolute; inside the parent's inner group (which undoes the parent pivot) that's correct.
    bone.outer.position.copy(bone.pivot);
    (parent ? parent.inner : root).add(bone.outer);
  }
  return { root, bones };
}

// ------------------------------------------------------------------ GeckoLib animation

const EASINGS = {
  linear: t => t,
  easeInOutSine: t => -(Math.cos(Math.PI * t) - 1) / 2,
  easeInOutQuad: t => (t < 0.5 ? 2 * t * t : 1 - (-2 * t + 2) ** 2 / 2),
  easeOutQuart: t => 1 - (1 - t) ** 4,
  easeInOutCubic: t => (t < 0.5 ? 4 * t ** 3 : 1 - (-2 * t + 2) ** 3 / 2),
  easeInOutExpo: t => (t === 0 ? 0 : t === 1 ? 1 : t < 0.5 ? 2 ** (20 * t - 10) / 2 : (2 - 2 ** (-20 * t + 10)) / 2),
};

/** Value of one channel (position/scale) at time t. Like GeckoLib: a keyframe's easing shapes the move into it. */
function sample(channel, t, fallback) {
  if (!channel) return fallback;
  const keys = Object.entries(channel).map(([time, k]) => [parseFloat(time), Array.isArray(k) ? { vector: k } : k])
    .sort((x, y) => x[0] - y[0]);
  if (t <= keys[0][0]) return keys[0][1].vector;
  for (let i = 1; i < keys.length; i++) {
    const [t1, k1] = keys[i];
    if (t <= t1) {
      const [t0, k0] = keys[i - 1];
      const p = (EASINGS[k1.easing] || EASINGS.linear)((t - t0) / (t1 - t0));
      return k0.vector.map((v, j) => v + (k1.vector[j] - v) * p);
    }
  }
  return keys[keys.length - 1][1].vector;
}

function pose(bones, animation, t) {
  for (const [name, bone] of Object.entries(bones)) {
    const ch = animation?.bones?.[name] || {};
    const [px, py, pz] = sample(ch.position, t, [0, 0, 0]);
    const [sx, sy, sz] = sample(ch.scale, t, [1, 1, 1]);
    bone.outer.position.set(bone.pivot.x - px, bone.pivot.y + py, bone.pivot.z + pz); // X mirrored
    bone.outer.scale.set(sx, sy, sz);
  }
}

// ------------------------------------------------------------------ extensions (HangarDoorExtensionRenderer)

const OPEN_DURATION = 1.6667, CLOSE_DELAY = 0.875, CLOSE_DURATION = 2.5833 - CLOSE_DELAY;
/** 0 = extended (door closed), 1 = tucked into the housing (door open). */
function retracted(open, t) {
  if (open) return EASINGS.easeInOutSine(Math.min(Math.max(t / OPEN_DURATION, 0), 1));
  if (t < CLOSE_DELAY) return 1;
  return 1 - EASINGS.easeOutQuart(Math.min((t - CLOSE_DELAY) / CLOSE_DURATION, 1));
}

// ------------------------------------------------------------------ viewer

// Fallback names; the page passes Oritech's own names in data-names (read from its lang file at build time).
const PAINT_NAMES = {
  orange: 'Unpainted', camo: 'Camo', diamond: 'Adamant', fluxite: 'Fluxite', white: 'White',
  industrial: 'Industrial', netherite: 'Netherite', redstone: 'Redstone', sculk: 'Sculk',
};

function texture(url) {
  const tex = new THREE.TextureLoader().load(url);
  tex.magFilter = tex.minFilter = THREE.NearestFilter;
  tex.generateMipmaps = false;
  tex.flipY = false;
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

export async function setup(container) {
  if (container.dataset.ready) return;
  container.dataset.ready = '1';
  const root = container.dataset.root || '';
  const base = `${root}assets/viewer/door/`;
  const paints = JSON.parse(container.dataset.paints || '[]');
  const swatches = JSON.parse(container.dataset.swatches || '{}');
  const names = { ...PAINT_NAMES, ...JSON.parse(container.dataset.names || '{}') };
  const [doorGeo, anims, extGeo] = await Promise.all(
    ['hangar_door.geo.json', 'hangar_door.animation.json', 'extension.geo.json'].map(f => fetch(base + f).then(r => r.json())));

  container.innerHTML = `
    <div class="pv-stage"><span class="pv-hint">Drag to rotate</span></div>
    <div class="pv-controls">
      <div class="pv-blocks">
        <button type="button" class="dv-toggle"></button>
        <label class="dv-count">Extensions <input type="range" min="0" max="8" value="3"><output>3</output></label>
      </div>
      <div class="pv-paints" role="radiogroup" aria-label="Paint"></div>
    </div>`;
  const stage = container.querySelector('.pv-stage');
  const toggle = container.querySelector('.dv-toggle');
  const slider = container.querySelector('input[type=range]');
  const countOut = container.querySelector('output');
  const paintBar = container.querySelector('.pv-paints');

  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  stage.prepend(renderer.domElement);
  lockLayout(stage, renderer.domElement, 380);
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(30, 1, 1, 500);

  const material = new THREE.MeshBasicMaterial({
    map: texture(base + 'hangar_door.png'), vertexColors: true, transparent: true, alphaTest: 0.05, side: THREE.DoubleSide,
  });
  const door = buildGeo(doorGeo, material);
  const model = new THREE.Group();
  model.add(door.root);
  scene.add(model);

  // Extensions: segment N rests at model Y 48 + 16(N-1) and slides 16N + 32 px down into the housing.
  const extensions = [];
  function setCount(n) {
    while (extensions.length < n) {
      const ext = buildGeo(extGeo, material).root;
      extensions.push(ext);
      model.add(ext);
    }
    while (extensions.length > n) model.remove(extensions.pop());
    countOut.textContent = n;
    model.position.y = -(48 + 16 * n) / 2;
    distance = (48 + 16 * n) * 1.95 + 40;
  }

  let open = false, changed = -Infinity, autoplay = true, nextAuto = performance.now() + 1200;
  let yaw = Math.PI / 5, pitch = 0.25, dragging = null, distance = 120;
  const clipLength = name => anims.animations[name]?.animation_length || 0;

  function setOpen(value) {
    open = value;
    changed = performance.now();
    toggle.textContent = open ? 'Close door' : 'Open door';
  }
  toggle.textContent = 'Open door';
  toggle.addEventListener('click', () => { autoplay = false; setOpen(!open); });
  slider.addEventListener('input', () => { autoplay = false; setCount(+slider.value); });
  setCount(+slider.value);

  for (const name of ['orange', ...paints]) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.setAttribute('role', 'radio');
    btn.setAttribute('aria-checked', name === 'orange');
    btn.title = names[name] || name;
    btn.innerHTML = `<span class="pv-dot" style="background:${swatches[name] || '#888'}"></span><span class="pv-label">${btn.title}</span>`;
    btn.addEventListener('click', () => {
      material.map = texture(base + (name === 'orange' ? 'hangar_door.png' : `hangar_door_${name}.png`));
      material.needsUpdate = true;
      paintBar.querySelectorAll('button').forEach(b => b.setAttribute('aria-checked', b === btn));
    });
    paintBar.append(btn);
  }

  stage.addEventListener('pointerdown', e => {
    dragging = { x: e.clientX, y: e.clientY, yaw, pitch };
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

  let size = '';
  function resize() {
    const w = Math.min(stage.clientWidth, 2000), h = Math.min(stage.clientHeight, 1200);
    if (!w || !h || `${w}x${h}` === size) return;
    size = `${w}x${h}`;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  }
  new ResizeObserver(resize).observe(stage);
  resize();
  let visible = false;
  new IntersectionObserver(e => { visible = e[0].isIntersecting; }).observe(stage);

  function draw(now) {
    const t = (now - changed) / 1000;
    // Oritech: "retract" opens, "deploy" closes; both hold their last frame.
    pose(door.bones, anims.animations[open ? 'retract' : 'deploy'], t);
    const f = retracted(open, t);
    extensions.forEach((ext, i) => {
      const index = i + 1;
      ext.position.y = 48 + 16 * (index - 1) - (16 * index + 32) * f;
      ext.visible = !(open && t >= OPEN_DURATION); // tucked away once fully open, like in game
    });
    camera.position.set(Math.sin(yaw) * Math.cos(pitch) * distance, Math.sin(pitch) * distance,
      Math.cos(yaw) * Math.cos(pitch) * distance);
    camera.lookAt(0, 0, 0);
    renderer.render(scene, camera);
    if (autoplay && now > nextAuto) {
      setOpen(!open);
      nextAuto = now + Math.max(clipLength('retract'), clipLength('deploy')) * 1000 + 1500;
    }
  }
  pose(door.bones, null, 0);
  const tick = now => {
    if (!container.isConnected) return;
    if (visible) draw(now);
    requestAnimationFrame(tick);
  };
  requestAnimationFrame(tick);
}
