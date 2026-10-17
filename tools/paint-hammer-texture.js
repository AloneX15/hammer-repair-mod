// Pinta la textura del modelo 3D del martillo (128x128) a partir de los UV del modelo exportado de Blockbench.
// Cada pieza recibe su material: cabeza de netherita, caras de golpe pulidas, núcleo y tapa de oro con gema,
// mango de madera con contera de hierro y tiras de cuero.
// Uso: node tools/paint-hammer-texture.js   (escribe la textura y la incrusta en art/repair_hammer.bbmodel)
const zlib = require("zlib");
const fs = require("fs");
const path = require("path");

const ROOT = path.join(__dirname, "..");
const ASSETS = path.join(ROOT, "src", "main", "resources", "assets", "hammer_repair");
const MODEL = path.join(ASSETS, "models", "item", "repair_hammer.json");
const TEXTURE = path.join(ASSETS, "textures", "item", "repair_hammer.png");
const BBMODEL = path.join(ROOT, "art", "repair_hammer.bbmodel");
const SIZE = 128;
const PX_PER_UV = SIZE / 16;

const STEEL = { hi: [118, 110, 124], base: [64, 58, 68], lo: [34, 30, 38], worn: [150, 142, 156] };
const POLISH = { hi: [214, 212, 224], base: [150, 146, 160], lo: [84, 80, 94] };
const GOLD = { hi: [252, 222, 132], base: [216, 162, 60], lo: [138, 88, 26], deep: [92, 56, 18] };
const GEM = { core: [210, 255, 255], light: [120, 236, 255], base: [40, 170, 220], deep: [18, 90, 140] };
const WOOD = { hi: [146, 98, 58], base: [112, 72, 40], lo: [74, 44, 22], deep: [54, 30, 14] };
const LEATHER = { hi: [138, 70, 44], base: [96, 46, 30], lo: [58, 26, 18], stitch: [196, 170, 128] };

const px = Buffer.alloc(SIZE * SIZE * 4);

function hash(x, y, seed) {
    let h = (x * 374761393 + y * 668265263 + seed * 2147483647) | 0;
    h = Math.imul(h ^ (h >>> 13), 1274126177);
    return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}
const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));
const shade = (c, k) => c.map((v) => v * k);
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const jitter = (c, x, y, seed, amount) => { const n = (hash(x, y, seed) - 0.5) * 2 * amount; return c.map((v) => v + n); };

/** Región de una cara en píxeles de la textura, con coordenadas locales (i hacia la derecha, j hacia abajo). */
function region(uv) {
    const [u0, v0, u1, v1] = uv.map((v) => Math.round(v * PX_PER_UV));
    const x = Math.min(u0, u1), y = Math.min(v0, v1);
    return { x, y, w: Math.abs(u1 - u0), h: Math.abs(v1 - v0) };
}

function paint(r, fn) {
    for (let j = 0; j < r.h; j++) {
        for (let i = 0; i < r.w; i++) {
            const c = fn(i, j, r.x + i, r.y + j);
            if (!c) continue;
            const o = ((r.y + j) * SIZE + r.x + i) * 4;
            px[o] = clamp(c[0]); px[o + 1] = clamp(c[1]); px[o + 2] = clamp(c[2]); px[o + 3] = 255;
        }
    }
}

/** Bisel de un píxel: luz arriba e izquierda, sombra abajo y derecha. */
function bevel(i, j, r, pal, c) {
    if (j === 0 || i === 0) return mix(c, pal.hi, 0.75);
    if (j === r.h - 1 || i === r.w - 1) return mix(c, pal.lo, 0.8);
    return c;
}

/** Netherita cepillada y desgastada en los bordes. */
function steel(r, pal = STEEL) {
    return (i, j, x, y) => {
        const t = r.h > 1 ? j / (r.h - 1) : 0.5;
        let c = shade(pal.base, 1.12 - 0.24 * t);
        c = jitter(c, 0, y, 11, 5);         // vetas horizontales del cepillado
        c = jitter(c, x, y, 7, 6);
        const edge = i === 0 || j === 0 || i === r.w - 1 || j === r.h - 1;
        if (edge && hash(x, y, 3) < 0.3 && pal.worn) return jitter(pal.worn, x, y, 5, 8);
        return bevel(i, j, r, pal, c);
    };
}

function rivet(i, j, ri, rj) {
    if (i === ri && j === rj) return [190, 184, 198];
    if (i === ri + 1 && j === rj + 1) return [24, 20, 26];
    if ((i === ri + 1 && j === rj) || (i === ri && j === rj + 1)) return [92, 86, 98];
    return null;
}

/** Cabeza: netherita con bandas de oro y remaches a cada lado del núcleo. */
function head(r) {
    const base = steel(r);
    const bands = [3, 4, r.w - 5, r.w - 4];
    return (i, j, x, y) => {
        if (r.w >= 20 && bands.includes(i)) {
            const g = i === bands[0] || i === bands[2] ? GOLD.hi : GOLD.base;
            if (j === r.h - 1) return GOLD.lo;
            return jitter(j === 0 ? GOLD.hi : g, x, y, 13, 6);
        }
        if (r.w >= 20 && r.h >= 8) {
            for (const ri of [1, r.w - 3]) for (const rj of [2, r.h - 4]) {
                const c = rivet(i, j, ri, rj);
                if (c) return c;
            }
        }
        return base(i, j, x, y);
    };
}

/** Cara de golpe: acero pulido con brillo central y chaflán oscuro. */
function strikeFace(r) {
    const cx = (r.w - 1) / 2, cy = (r.h - 1) / 2;
    return (i, j, x, y) => {
        if (i === 0 || j === 0) return POLISH.hi;
        if (i === r.w - 1 || j === r.h - 1) return POLISH.lo;
        const d = Math.hypot((i - cx) / cx, (j - cy) / cy);
        let c = mix(POLISH.hi, POLISH.base, Math.min(1, d * 1.1));
        if (d > 0.82) c = mix(c, POLISH.lo, 0.45);
        if (i === Math.floor(cx) - 1 && j === Math.floor(cy) - 1) c = [240, 240, 250];
        return jitter(c, x, y, 17, 5);
    };
}

function polishedEdge(r) {
    return (i, j, x, y) => jitter(bevel(i, j, r, POLISH, mix(POLISH.base, POLISH.lo, r.h > 1 ? j / (r.h - 1) * 0.5 : 0.2)), x, y, 19, 5);
}

/** Gema cian en rombo con engaste de oro, centrada en la región. */
function gem(i, j, r, size) {
    const d = Math.abs(i + 0.5 - r.w / 2) + Math.abs(j + 0.5 - r.h / 2);
    if (d > size + 1) return null;
    if (d > size) return GOLD.deep;
    if (d <= 1 && i < r.w / 2 && j < r.h / 2) return GEM.core;
    if (d <= 1) return GEM.light;
    if (i + j < r.w / 2 + r.h / 2 - 1) return GEM.light;
    return d > size - 1 ? GEM.deep : GEM.base;
}

/** Núcleo: marco de oro, interior hundido de netherita y gema en las caras laterales. */
function core(r, withGem) {
    return (i, j, x, y) => {
        const ring = Math.min(i, j, r.w - 1 - i, r.h - 1 - j);
        if (ring === 0) return bevel(i, j, r, GOLD, GOLD.base);
        if (ring === 1) {
            const engraved = (i + j) % 4 === 0;
            return jitter(engraved ? GOLD.lo : GOLD.base, x, y, 23, 6);
        }
        if (withGem) {
            const g = gem(i, j, r, 3);
            if (g) return g;
        }
        // Interior hundido: sombra arriba e izquierda, luz abajo y derecha
        let c = shade(STEEL.base, 0.85);
        if (ring === 2 && (j === 2 || i === 2)) c = STEEL.lo;
        else if (ring === 2) c = mix(c, STEEL.hi, 0.4);
        return jitter(c, x, y, 29, 5);
    };
}

function goldCap(r, withGem) {
    return (i, j, x, y) => {
        if (withGem) {
            const g = gem(i, j, r, 2);
            if (g) return g;
        }
        return jitter(bevel(i, j, r, GOLD, GOLD.base), x, y, 31, 6);
    };
}

/** Casquillo bajo el núcleo: aros de oro de claro a oscuro. */
function socket(r) {
    const rows = [GOLD.hi, GOLD.base, GOLD.lo];
    return (i, j, x, y) => jitter(r.h <= 3 ? rows[j] : bevel(i, j, r, GOLD, GOLD.lo), x, y, 37, 5);
}

/** Mango: madera con veta vertical, oscura en los cantos, y contera de hierro abajo. */
function handle(r) {
    return (i, j, x, y) => {
        if (j >= r.h - 2) {
            if (j === r.h - 1) return STEEL.lo;
            return jitter(i === 0 ? STEEL.worn : STEEL.hi, x, y, 41, 6);
        }
        if (j === r.h - 3) return WOOD.deep;
        let c = WOOD.base;
        if (i === 0) c = mix(WOOD.base, WOOD.hi, 0.5);
        if (i === r.w - 1) c = WOOD.lo;
        const grain = Math.sin(j * 0.9 + hash(i, 0, 43) * 6.28) * 0.5 + 0.5;
        if (grain > 0.82) c = mix(c, WOOD.lo, 0.6);
        if (hash(i, Math.floor(j / 5), 47) > 0.86) c = mix(c, WOOD.deep, 0.5);
        return jitter(c, x, y, 53, 6);
    };
}

function handleEnd(r, iron) {
    return (i, j, x, y) => {
        if (iron) return i === 1 && j === 1 ? STEEL.worn : bevel(i, j, r, STEEL, STEEL.base);
        const d = Math.hypot(i + 0.5 - r.w / 2, j + 0.5 - r.h / 2);
        return jitter(Math.floor(d) % 2 ? WOOD.base : WOOD.lo, x, y, 59, 4);
    };
}

/** Tira de cuero enrollada con costura diagonal. */
function leather(r) {
    return (i, j, x, y) => {
        let c = j === 0 ? LEATHER.hi : LEATHER.lo;
        if ((i + j) % 3 === 0) c = mix(c, LEATHER.stitch, 0.55);
        if (i === r.w - 1) c = shade(c, 0.75);
        return jitter(c, x, y, 61, 5);
    };
}

function materialFor(name, face, r) {
    switch (name) {
        case "cubo_main": return head(r);
        case "bloque_right": return face === "east" ? strikeFace(r) : polishedEdge(r);
        case "bloque_left": return face === "west" ? strikeFace(r) : polishedEdge(r);
        case "cubo_core": return core(r, !["up", "down"].includes(face));
        case "bloque_top": return goldCap(r, face === "up");
        case "cubo_bottom": return socket(r);
        case "base":
            if (face === "down") return handleEnd(r, true);
            if (face === "up") return handleEnd(r, false);
            return handle(r);
        case "": return leather(r);
        default: throw new Error(`pieza sin material: "${name}"`);
    }
}

function png(w, h, data) {
    const raw = Buffer.alloc((w * 4 + 1) * h);
    for (let y = 0; y < h; y++) data.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
    const table = [];
    for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        table[n] = c >>> 0;
    }
    const crc = (buf) => { let r = 0xffffffff; for (const v of buf) r = table[(r ^ v) & 255] ^ (r >>> 8); return (r ^ 0xffffffff) >>> 0; };
    const chunk = (type, body) => {
        const len = Buffer.alloc(4); len.writeUInt32BE(body.length);
        const typed = Buffer.concat([Buffer.from(type), body]);
        const sum = Buffer.alloc(4); sum.writeUInt32BE(crc(typed));
        return Buffer.concat([len, typed, sum]);
    };
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
    return Buffer.concat([
        Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
        chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0)),
    ]);
}

const model = JSON.parse(fs.readFileSync(MODEL, "utf8"));
for (const element of model.elements) {
    for (const [face, def] of Object.entries(element.faces)) {
        const r = region(def.uv);
        if (r.w === 0 || r.h === 0) continue;
        paint(r, materialFor(element.name ?? "", face, r));
    }
}

const file = png(SIZE, SIZE, px);
fs.writeFileSync(TEXTURE, file);
if (fs.existsSync(BBMODEL)) {
    const bb = JSON.parse(fs.readFileSync(BBMODEL, "utf8"));
    bb.textures[0].source = "data:image/png;base64," + file.toString("base64");
    fs.writeFileSync(BBMODEL, JSON.stringify(bb));
}
console.log("Textura del martillo pintada en", TEXTURE);
