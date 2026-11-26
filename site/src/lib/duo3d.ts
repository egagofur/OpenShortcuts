// A foldable phone in the shape of iPhone Duo, built from simple geometry (no Apple assets).
// The fold, the front-view UI projection, and the blur/darken curves are adapted from
// chuspeeism/iphone-duo (MIT, © 2026 jadon7); see THIRD_PARTY_LICENSE_iphone-duo.txt.
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js';

// Model units. The inner screen is 15.8 × 11.1, the outer screen 7.74 × 11.25, each half 0.55 thick.
const HINGE_Z = 0.275;
const FRONT_Z = 0.244;
const BACK_Z = -0.3;
const SCREEN_Z = 0.2495;
const BODY_HALF_W = 8.15;
const BODY_H = 11.75;
const INNER = { w: 15.8, h: 11.1 };
const OUTER = { x0: 0.234, w: 7.74, h: 11.25 };
const SCREEN_R = 0.62;
const EYE = new THREE.Vector3(0, 0, 40);
const OUTER_Z = BACK_Z - 0.012;
const CLOSED_OUTER_Z = 2 * HINGE_Z - OUTER_Z;

const foldGlsl = /* glsl */ `
uniform float foldAngle;
vec2 rotateHinge(vec2 p) {
  float c = cos(foldAngle), s = sin(foldAngle);
  p.y -= ${HINGE_Z.toFixed(4)};
  return vec2(c * p.x + s * p.y, -s * p.x + c * p.y + ${HINGE_Z.toFixed(4)});
}
// The inner screen bends smoothly across a narrow strip at the hinge.
vec4 bendStrip(vec3 p) {
  float halfWidth = 0.35;
  if (p.x >= halfWidth) return vec4(p.x, p.z, 1.0, 0.0);
  if (p.x <= -halfWidth) return vec4(rotateHinge(p.xz), cos(foldAngle), -sin(foldAngle));
  float t = (p.x + halfWidth) / (2.0 * halfWidth);
  float t2 = t * t, t3 = t2 * t;
  vec2 a = rotateHinge(vec2(-halfWidth, p.z));
  vec2 b = vec2(halfWidth, p.z);
  vec2 ta = 2.0 * halfWidth * vec2(cos(foldAngle), -sin(foldAngle));
  vec2 tb = vec2(2.0 * halfWidth, 0.0);
  vec2 point = (2.0*t3-3.0*t2+1.0)*a + (t3-2.0*t2+t)*ta + (-2.0*t3+3.0*t2)*b + (t3-t2)*tb;
  vec2 tangent = normalize((6.0*t2-6.0*t)*a + (3.0*t2-4.0*t+1.0)*ta + (-6.0*t2+6.0*t)*b + (3.0*t2-2.0*t)*tb);
  return vec4(point, tangent);
}
`;

const screenGlsl = /* glsl */ `
uniform vec2 uiPixel;
uniform vec4 uiFrame;
uniform vec2 uiGradient;
uniform vec3 uiEye;
uniform vec4 uiMask;
varying vec3 vUIPosition;
varying vec2 vMaskUv;
float roundedMask(vec2 uv) {
  vec2 size = uiMask.xy;
  vec2 p = (uv - 0.5) * size;
  vec2 q = abs(p) - (size * 0.5 - vec2(uiMask.z));
  float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - uiMask.z;
  float aa = fwidth(d);
  float m = 1.0 - smoothstep(-aa, aa, d);
  #ifndef INNER_UI
    // Front camera hole near the hinge-side top corner.
    vec2 cam = vec2(size.x * 0.5 - 0.55, size.y * 0.5 - 0.55);
    float c = length(p - vec2(-cam.x, cam.y)) - 0.17;
    m *= smoothstep(-aa, aa, c);
  #endif
  return m;
}
vec3 screenColor() {
  // Intersect the fixed front-view ray with the unfolded inner-screen plane.
  float depth = (${SCREEN_Z.toFixed(4)} - uiEye.z) / (vUIPosition.z - uiEye.z);
  vec2 projected = uiEye.xy + (vUIPosition.xy - uiEye.xy) * depth;
  vec2 sourceUV = (projected - uiFrame.xy) / uiFrame.zw;
  #ifdef INNER_UI
    float progress = clamp(foldAngle / 1.570796327, 0.0, 1.0);
  #else
    // Anchor the image to the projected hinge-side edge of the outer screen.
    float c = cos(foldAngle), s = sin(foldAngle);
    vec2 hingeEdge = vec2(-${OUTER.x0.toFixed(4)}, ${OUTER_Z.toFixed(4)} - ${HINGE_Z.toFixed(4)});
    vec2 foldedEdge = vec2(c * hingeEdge.x + s * hingeEdge.y, -s * hingeEdge.x + c * hingeEdge.y + ${HINGE_Z.toFixed(4)});
    float edgeDepth = (${SCREEN_Z.toFixed(4)} - uiEye.z) / (foldedEdge.y - uiEye.z);
    float anchorX = uiEye.x + (foldedEdge.x - uiEye.x) * edgeDepth;
    sourceUV.x = uiGradient.x + (projected.x - anchorX) / uiFrame.z;
    float progress = clamp((3.141592654 - foldAngle) / 1.570796327, 0.0, 1.0);
  #endif
  float edge = (sourceUV.x - uiGradient.x) / (uiGradient.y - uiGradient.x);
  float motion = smoothstep(0.0, 1.0, progress);
  float blurGradient = clamp(edge, 0.0, 1.0);
  float darkenGradient = clamp((edge - 0.2) / 0.8, 0.0, 1.0);
  float effect = motion * pow(darkenGradient, 1.35);
  float radius = 72.0 * motion * pow(blurGradient, 1.35);
  vec2 dx = dFdx(sourceUV) / uiPixel;
  vec2 dy = dFdy(sourceUV) / uiPixel;
  float baseLod = log2(max(1.0, max(length(dx), length(dy))));
  vec2 uv = clamp(sourceUV, vec2(0.0), vec2(1.0));
  vec3 color = textureLod(map, uv, baseLod).rgb;
  if (radius > 0.5) {
    float lod = max(baseLod, log2(max(1.0, radius)));
    color = vec3(0.0);
    for (int y = -2; y <= 2; y++) {
      for (int x = -2; x <= 2; x++) {
        float wx = x == 0 ? 6.0 : (abs(x) == 1 ? 4.0 : 1.0);
        float wy = y == 0 ? 6.0 : (abs(y) == 1 ? 4.0 : 1.0);
        vec2 sampleUV = clamp(sourceUV + vec2(float(x), float(y)) * uiPixel * radius, vec2(0.0), vec2(1.0));
        color += textureLod(map, sampleUV, lod).rgb * wx * wy / 256.0;
      }
    }
  }
  return color * (1.0 - min(1.0, effect * 2.0));
}
`;

type ScreenKind = 'inner' | 'outer';

function roundedHalf(side: -1 | 1) {
  // Outer corners are round, the hinge side is square so the halves meet cleanly.
  const r = 1.0;
  const x0 = side < 0 ? -BODY_HALF_W : 0;
  const x1 = side < 0 ? 0 : BODY_HALF_W;
  const y0 = -BODY_H / 2;
  const y1 = BODY_H / 2;
  const s = new THREE.Shape();
  if (side < 0) {
    s.moveTo(x1, y0);
    s.lineTo(x1, y1);
    s.lineTo(x0 + r, y1);
    s.quadraticCurveTo(x0, y1, x0, y1 - r);
    s.lineTo(x0, y0 + r);
    s.quadraticCurveTo(x0, y0, x0 + r, y0);
    s.lineTo(x1, y0);
  } else {
    s.moveTo(x0, y0);
    s.lineTo(x1 - r, y0);
    s.quadraticCurveTo(x1, y0, x1, y0 + r);
    s.lineTo(x1, y1 - r);
    s.quadraticCurveTo(x1, y1, x1 - r, y1);
    s.lineTo(x0, y1);
    s.lineTo(x0, y0);
  }
  const bevel = 0.14;
  const depth = FRONT_Z - BACK_Z - 2 * bevel;
  const g = new THREE.ExtrudeGeometry(s, {
    depth,
    bevelEnabled: true,
    bevelThickness: bevel,
    bevelSize: 0.07,
    bevelSegments: 6,
    curveSegments: 24
  });
  g.translate(0, 0, BACK_Z + bevel);
  g.computeVertexNormals();
  return g;
}

export type Duo = {
  setFold(t: number): void;
  setTexture(kind: ScreenKind, source: HTMLCanvasElement): void;
  innerRect(): { x: number; y: number; w: number; h: number; r: number };
  atHome(): boolean;
  onFrame(cb: () => void): void;
  onOrbitStart(cb: () => void): void;
  goHome(): void;
  resize(): void;
  dispose(): void;
};

export function createDuo(canvas: HTMLCanvasElement): Duo {
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.18;
  renderer.outputColorSpace = THREE.SRGBColorSpace;

  const scene = new THREE.Scene();
  const pmrem = new THREE.PMREMGenerator(renderer);
  const room = new RoomEnvironment();
  scene.environment = pmrem.fromScene(room, 0.04).texture;
  scene.environmentIntensity = 1.35;
  room.dispose();
  pmrem.dispose();
  scene.add(new THREE.HemisphereLight(0xffffff, 0xb5baa8, 1.8));
  const key = new THREE.DirectionalLight(0xfffcf5, 2.6);
  key.position.set(-15, 25, 30);
  scene.add(key);
  const rim = new THREE.DirectionalLight(0xe8edf5, 2);
  rim.position.set(15, 5, -15);
  scene.add(rim);

  // A tight depth range keeps the screens from z-fighting with the glass right behind them.
  const camera = new THREE.PerspectiveCamera(32, 1, 10, 90);
  camera.position.copy(EYE);
  const controls = new OrbitControls(camera, canvas);
  controls.enableDamping = true;
  controls.enablePan = false;
  controls.enableZoom = false;
  controls.minAzimuthAngle = -0.9;
  controls.maxAzimuthAngle = 0.9;
  controls.minPolarAngle = Math.PI / 2 - 0.55;
  controls.maxPolarAngle = Math.PI / 2 + 0.55;
  controls.target.set(0, 0, HINGE_Z);
  controls.update();

  const titanium = new THREE.MeshPhysicalMaterial({ color: 0xd8d3ca, metalness: 1, roughness: 0.26, clearcoat: 0.4, clearcoatRoughness: 0.2 });
  const glass = new THREE.MeshPhysicalMaterial({ color: 0x050506, metalness: 0, roughness: 0.08, clearcoat: 1, clearcoatRoughness: 0.05 });

  const phone = new THREE.Group();
  scene.add(phone);

  const fixedBody = new THREE.Mesh(roundedHalf(1), [glass, titanium]);
  phone.add(fixedBody);

  const pivot = new THREE.Group();
  pivot.position.set(0, 0, HINGE_Z);
  phone.add(pivot);
  const moving = new THREE.Group();
  moving.position.set(0, 0, -HINGE_Z);
  pivot.add(moving);
  moving.add(new THREE.Mesh(roundedHalf(-1), [glass, titanium]));

  const hinge = new THREE.Mesh(new THREE.CylinderGeometry(0.27, 0.27, BODY_H - 0.6, 32), titanium);
  hinge.position.set(0, 0, -0.03);
  phone.add(hinge);

  const fold = { value: 0 };
  const screens = {} as Record<ScreenKind, { material: THREE.MeshBasicMaterial; pixel: { value: THREE.Vector2 } }>;

  const placeholder = document.createElement('canvas');
  placeholder.width = placeholder.height = 4;

  function screenMaterial(kind: ScreenKind, flexible: boolean) {
    const texture = new THREE.CanvasTexture(placeholder);
    texture.colorSpace = THREE.SRGBColorSpace;
    const material = new THREE.MeshBasicMaterial({
      map: texture,
      toneMapped: false,
      transparent: true,
      polygonOffset: true,
      polygonOffsetFactor: -1,
      polygonOffsetUnits: -4
    });
    const pixel = { value: new THREE.Vector2(1 / 4, 1 / 4) };
    const frame =
      kind === 'inner'
        ? new THREE.Vector4(-INNER.w / 2, -INNER.h / 2, INNER.w, INNER.h)
        : new THREE.Vector4(OUTER.x0, -OUTER.h / 2, OUTER.w, OUTER.h).multiplyScalar((EYE.z - SCREEN_Z) / (EYE.z - CLOSED_OUTER_Z));
    const gradient = kind === 'inner' ? new THREE.Vector2(0.5, 0) : new THREE.Vector2(0, 1);
    const mask = kind === 'inner' ? new THREE.Vector4(INNER.w, INNER.h, SCREEN_R, 0) : new THREE.Vector4(OUTER.w, OUTER.h, SCREEN_R, 0);
    material.onBeforeCompile = (shader) => {
      shader.uniforms.foldAngle = fold;
      shader.uniforms.uiFrame = { value: frame };
      shader.uniforms.uiGradient = { value: gradient };
      shader.uniforms.uiEye = { value: EYE };
      shader.uniforms.uiPixel = pixel;
      shader.uniforms.uiMask = { value: mask };
      shader.vertexShader = `${foldGlsl}\nvarying vec3 vUIPosition;\nvarying vec2 vMaskUv;\n${shader.vertexShader}`
        .replace(
          '#include <begin_vertex>',
          flexible
            ? `vec4 folded = bendStrip(position);\nvec3 transformed = vec3(folded.x, position.y, folded.y);`
            : `vec3 transformed = vec3(position);`
        )
        .replace(
          '#include <project_vertex>',
          `vUIPosition = (modelMatrix * vec4(transformed, 1.0)).xyz;\nvMaskUv = uv;\n#include <project_vertex>`
        );
      shader.fragmentShader = shader.fragmentShader
        .replace('#include <map_pars_fragment>', `#include <map_pars_fragment>\nuniform float foldAngle;\n${kind === 'inner' ? '#define INNER_UI' : ''}\n${screenGlsl}`)
        .replace('#include <map_fragment>', 'diffuseColor.rgb *= screenColor();\ndiffuseColor.a *= roundedMask(vMaskUv);');
    };
    material.customProgramCacheKey = () => `duo-${kind}`;
    screens[kind] = { material, pixel };
    return material;
  }

  // Inner screen: one bendable sheet across both halves.
  const innerGeo = new THREE.PlaneGeometry(INNER.w, INNER.h, 240, 1);
  innerGeo.translate(0, 0, SCREEN_Z);
  const innerMesh = new THREE.Mesh(innerGeo, screenMaterial('inner', true));
  innerMesh.frustumCulled = false;
  innerMesh.renderOrder = 1;
  phone.add(innerMesh);

  // Outer screen: on the back of the moving half, facing away until the phone closes.
  const outerGeo = new THREE.PlaneGeometry(OUTER.w, OUTER.h, 1, 1);
  outerGeo.rotateY(Math.PI);
  outerGeo.translate(-(OUTER.x0 + OUTER.w / 2), 0, OUTER_Z);
  const uv = outerGeo.attributes.uv;
  const pos = outerGeo.attributes.position;
  for (let i = 0; i < pos.count; i++) uv.setXY(i, (-OUTER.x0 - pos.getX(i)) / OUTER.w, (pos.getY(i) + OUTER.h / 2) / OUTER.h);
  const outerMesh = new THREE.Mesh(outerGeo, screenMaterial('outer', false));
  outerMesh.frustumCulled = false;
  moving.add(outerMesh);

  let frameCb: (() => void) | null = null;
  let t = 1;

  function setFold(next: number) {
    t = next;
    const angle = Math.PI * (1 - next);
    fold.value = angle;
    pivot.rotation.y = angle;
    // The outer screen is off while the phone is fully open.
    screens.outer.material.color.setScalar(next >= 1 ? 0 : 1);
  }

  function setTexture(kind: ScreenKind, source: HTMLCanvasElement) {
    const m = screens[kind].material;
    const old = m.map;
    const tex = new THREE.CanvasTexture(source);
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.anisotropy = renderer.capabilities.getMaxAnisotropy();
    tex.generateMipmaps = true;
    tex.minFilter = THREE.LinearMipmapLinearFilter;
    m.map = tex;
    screens[kind].pixel.value.set(1 / source.width, 1 / source.height);
    old?.dispose();
  }

  function resize() {
    const { width, height } = canvas.getBoundingClientRect();
    if (!width || !height) return;
    renderer.setSize(width, height, false);
    camera.aspect = width / height;
    const pxPerUnit = Math.min(width / 18.5, height / 13.6);
    camera.fov = THREE.MathUtils.radToDeg(2 * Math.atan(height / pxPerUnit / 2 / EYE.z));
    camera.updateProjectionMatrix();
  }

  const v = new THREE.Vector3();
  function project(x: number, y: number, z: number) {
    v.set(x, y, z).project(camera);
    const { width, height } = canvas.getBoundingClientRect();
    return { x: ((v.x + 1) / 2) * width, y: ((1 - v.y) / 2) * height };
  }
  function innerRect() {
    const a = project(-INNER.w / 2, INNER.h / 2, SCREEN_Z);
    const b = project(INNER.w / 2, -INNER.h / 2, SCREEN_Z);
    const w = b.x - a.x;
    return { x: a.x, y: a.y, w, h: b.y - a.y, r: (SCREEN_R / INNER.w) * w };
  }
  function atHome() {
    return camera.position.distanceTo(EYE) < 0.05;
  }
  let homing = false;
  function goHome() {
    homing = true;
  }
  let orbitCb: (() => void) | null = null;
  let homeTimer = 0;
  controls.addEventListener('start', () => {
    homing = false;
    clearTimeout(homeTimer);
    orbitCb?.();
  });
  // Turn back to face the viewer shortly after they let go, so the live UI can return.
  controls.addEventListener('end', () => {
    homeTimer = window.setTimeout(() => (homing = true), 1600);
  });

  let raf = 0;
  const loop = () => {
    raf = requestAnimationFrame(loop);
    if (homing) {
      camera.position.lerp(EYE, 0.12);
      if (camera.position.distanceTo(EYE) < 0.01) {
        camera.position.copy(EYE);
        homing = false;
      }
      camera.lookAt(controls.target);
    }
    controls.update();
    renderer.render(scene, camera);
    frameCb?.();
  };
  resize();
  setFold(t);
  loop();

  return {
    setFold,
    setTexture,
    innerRect,
    atHome,
    onFrame: (cb) => (frameCb = cb),
    onOrbitStart: (cb) => (orbitCb = cb),
    goHome,
    resize,
    dispose() {
      cancelAnimationFrame(raf);
      clearTimeout(homeTimer);
      controls.dispose();
      scene.traverse((o) => {
        const mesh = o as THREE.Mesh;
        mesh.geometry?.dispose();
        const mats = Array.isArray(mesh.material) ? mesh.material : mesh.material ? [mesh.material] : [];
        mats.forEach((mm) => {
          (mm as THREE.MeshBasicMaterial).map?.dispose();
          mm.dispose();
        });
      });
      renderer.dispose();
    }
  };
}
