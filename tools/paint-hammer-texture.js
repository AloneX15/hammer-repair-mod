// Repinta la textura del modelo 3D del martillo (128x128) a partir de los UV del modelo exportado de Blockbench.
// Mantiene el diseño y la paleta originales (paneles de metal oscuro con borde claro, mango y tiras de madera) y
// solo afina el pixel art al estilo de Minecraft: rampas de pocos tonos, bisel y ruido agrupado en vez de degradados.
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

// Rampas sacadas de la textura original (art/repair_hammer_original.png), de oscuro a claro
const METAL = [[36, 31, 37], [44, 39, 45], [49, 43, 50], [55, 48, 56], [62, 56, 63], [70, 65, 70], [89, 82, 89], [119, 109, 118]];
const WOOD = [[44, 31, 17], [54, 39, 24], [61, 44, 26], [66, 46, 24], [71, 48, 24], [82, 57, 32]];
const STRAP = [[58, 41, 23], [66, 47, 27], [74, 53, 32], [82, 57, 32], [94, 66, 38]];

const px = Buffer.alloc(SIZE * SIZE * 4);

function hash(x, y, seed) {
    let h = (x * 374761393 + y * 668265263 + seed * 1442695041) | 0;
    h = Math.imul(h ^ (h >>> 13), 1274126177);
    return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}

/** Ruido de valor en celdas de 2x2 para que las manchas se agrupen como en las texturas de Minecraft. */
function clustered(x, y, seed) {
    const cx = x >> 1, cy = y >> 1;
    return (hash(cx, cy, seed) * 2 + hash(x, y, seed + 1)) / 3;
}

const pick = (ramp, i) => ramp[Math.max(0, Math.min(ramp.length - 1, i))];

function region(uv) {
    const [u0, v0, u1, v1] = uv.map((v) => Math.round(v * PX_PER_UV));
    return { x: Math.min(u0, u1), y: Math.min(v0, v1), w: Math.abs(u1 - u0), h: Math.abs(v1 - v0) };
}

function paint(r, fn) {
    for (let j = 0; j < r.h; j++) {
        for (let i = 0; i < r.w; i++) {
            const c = fn(i, j, r.x + i, r.y + j);
            const o = ((r.y + j) * SIZE + r.x + i) * 4;
            px[o] = c[0]; px[o + 1] = c[1]; px[o + 2] = c[2]; px[o + 3] = 255;
        }
    }
}

/**
 * Panel de metal del diseño original: borde en relieve (claro arriba, más oscuro abajo), una línea de sombra bajo el
 * borde superior y un interior oscuro que se apaga hacia abajo con manchas agrupadas y algún arañazo claro.
 * {@code lift} aclara el interior (caras de golpe y casquillo, que en el original eran más claras).
 */
function panel(r, lift = 0) {
    const thin = r.w <= 2 || r.h <= 2;
    return (i, j, x, y) => {
        const t = r.h > 1 ? j / (r.h - 1) : 0;
        if (thin) {
            // Cantos de 1 unidad: solo la rampa del borde, de claro a oscuro
            const base = r.h <= 2 ? (j === 0 ? 7 : 5) : 7 - Math.round(t * 2);
            return pick(METAL, base - (hash(x, y, 3) < 0.15 ? 1 : 0));
        }
        // Borde de 2 píxeles como el original (1 en caras pequeñas): luz arriba e izquierda, sombra abajo y derecha
        const rim = Math.min(r.w, r.h) >= 8 ? 2 : 1;
        const top = j, left = i, bottom = r.h - 1 - j, right = r.w - 1 - i;
        if (top < rim && top <= right && top <= bottom) return pick(METAL, top === 0 ? (hash(x, y, 5) < 0.15 ? 6 : 7) : 6);
        if (bottom < rim && bottom <= left) return pick(METAL, bottom === 0 ? 3 : 4);
        if (left < rim) return pick(METAL, (left === 0 ? 6 : 5) - Math.round(t));
        if (right < rim) return pick(METAL, (right === 0 ? 4 : 5) - Math.round(t));
        // Sombra que proyecta el borde superior y el izquierdo sobre el interior hundido
        if (top === rim || left === rim) return pick(METAL, 1 + lift);
        let level = 3 + lift - Math.round(t * 1.3);
        const n = clustered(x, y, 7);
        if (n < 0.18) level -= 1;
        else if (n > 0.9) level += 1;
        // Algún arañazo claro suelto, sobre todo junto al borde inferior y derecho
        if (hash(x, y, 9) < (right === rim || bottom === rim ? 0.08 : 0.012)) level = 5 + lift;
        return pick(METAL, level);
    };
}

/** Mango: veta vertical con canto claro a la izquierda, oscuro a la derecha y algún nudo. */
function handle(r) {
    return (i, j, x, y) => {
        if (i === 0) return pick(WOOD, hash(x, y, 11) < 0.25 ? 4 : 5);
        if (i === r.w - 1) return pick(WOOD, hash(x, y, 13) < 0.3 ? 0 : 1);
        // Cada columna tiene su propia veta, que se oscurece en tramos cortos
        const streak = hash(i, Math.floor((j + hash(i, 0, 17) * 7) / 4), 19);
        let level = streak < 0.3 ? 2 : streak > 0.8 ? 4 : 3;
        if (hash(x, y, 23) < 0.06) level -= 2;
        return pick(WOOD, level);
    };
}

function endGrain(r) {
    return (i, j, x, y) => {
        const edge = i === 0 || j === 0 || i === r.w - 1 || j === r.h - 1;
        return pick(WOOD, edge ? 1 : hash(x, y, 29) < 0.5 ? 3 : 2);
    };
}

/** Tira enrollada: arriba clara, abajo oscura, con los extremos en sombra para darle volumen. */
function strap(r) {
    return (i, j, x, y) => {
        let level = j === 0 ? 3 : 1;
        if (i === 0) level += 1;
        if (i === r.w - 1) level -= 1;
        if (hash(x, y, 31) < 0.15) level += j === 0 ? 1 : -1;
        return pick(STRAP, level);
    };
}

function materialFor(name, face, r) {
    switch (name) {
        case "cubo_main":
        case "cubo_core":
        case "bloque_top":
            return panel(r);
        case "bloque_right": return panel(r, face === "east" ? 2 : 0);
        case "bloque_left": return panel(r, face === "west" ? 2 : 0);
        case "cubo_bottom": return panel(r, 2);
        case "base": return face === "up" || face === "down" ? endGrain(r) : handle(r);
        case "": return strap(r);
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
