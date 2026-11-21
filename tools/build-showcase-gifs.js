// Monta los GIF de docs/images a partir de los fotogramas del escaparate (HammerRepairShowcaseClientGameTest), sin
// dependencias: lee los PNG, los reduce, crea una paleta común por GIF y guarda en cada fotograma solo lo que cambia.
// Uso: node tools/build-showcase-gifs.js <carpeta de capturas> [carpeta de destino]
// Ver docs/capturas.md.
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

/** Escena (subcarpeta de <capturas>/gif) → archivo de docs/images. */
const SCENES = {
    progress: "repair-progress.gif",
    partner: "partner-notice.gif",
    action_only: "action-only.gif",
};
const WIDTH = 800;          // 1280×720 → 800×450: la barra de acción (escala 3) sigue legible
const FRAME_DELAY = 10;     // centésimas: 2 ticks del juego por fotograma, en tiempo real
const LAST_FRAME_DELAY = 150;
const TRANSPARENT = 255;    // índice reservado para "sin cambios respecto al fotograma anterior"

// ---------------------------------------------------------------------------------------------------------------
// PNG (8 bits, RGB o RGBA, sin entrelazar: lo que escribe Minecraft)
// ---------------------------------------------------------------------------------------------------------------

function readPng(file) {
    const buf = fs.readFileSync(file);
    let pos = 8, width = 0, height = 0, colorType = 0;
    const idat = [];
    while (pos < buf.length) {
        const len = buf.readUInt32BE(pos);
        const type = buf.toString("ascii", pos + 4, pos + 8);
        const data = buf.subarray(pos + 8, pos + 8 + len);
        if (type === "IHDR") {
            width = data.readUInt32BE(0);
            height = data.readUInt32BE(4);
            colorType = data[9];
            if (data[8] !== 8 || (colorType !== 2 && colorType !== 6) || data[12] !== 0) {
                throw new Error(`${file}: formato PNG no soportado`);
            }
        } else if (type === "IDAT") {
            idat.push(data);
        }
        pos += 12 + len;
    }
    const raw = zlib.inflateSync(Buffer.concat(idat));
    const bpp = colorType === 6 ? 4 : 3;
    const stride = width * bpp;
    const pixels = Buffer.alloc(width * height * 3);
    let prev = Buffer.alloc(stride);
    for (let y = 0; y < height; y++) {
        const filter = raw[y * (stride + 1)];
        const line = Buffer.from(raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)));
        for (let i = 0; i < stride; i++) {
            const a = i >= bpp ? line[i - bpp] : 0, b = prev[i], c = i >= bpp ? prev[i - bpp] : 0;
            let add = 0;
            if (filter === 1) add = a;
            else if (filter === 2) add = b;
            else if (filter === 3) add = (a + b) >> 1;
            else if (filter === 4) {
                const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
                add = pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
            }
            line[i] = (line[i] + add) & 0xff;
        }
        for (let x = 0; x < width; x++) {
            pixels.set(line.subarray(x * bpp, x * bpp + 3), (y * width + x) * 3);
        }
        prev = line;
    }
    return { width, height, pixels };
}

/** Reducción por media de área (sin bordes dentados en el texto de la interfaz). */
function downscale(img, width) {
    const height = Math.round(img.height * width / img.width);
    const sx = img.width / width, sy = img.height / height;
    const out = Buffer.alloc(width * height * 3);
    for (let y = 0; y < height; y++) {
        const y0 = Math.floor(y * sy), y1 = Math.max(y0 + 1, Math.floor((y + 1) * sy));
        for (let x = 0; x < width; x++) {
            const x0 = Math.floor(x * sx), x1 = Math.max(x0 + 1, Math.floor((x + 1) * sx));
            let r = 0, g = 0, b = 0, n = 0;
            for (let yy = y0; yy < y1; yy++) {
                for (let xx = x0; xx < x1; xx++) {
                    const i = (yy * img.width + xx) * 3;
                    r += img.pixels[i]; g += img.pixels[i + 1]; b += img.pixels[i + 2]; n++;
                }
            }
            const o = (y * width + x) * 3;
            out[o] = Math.round(r / n); out[o + 1] = Math.round(g / n); out[o + 2] = Math.round(b / n);
        }
    }
    return { width, height, pixels: out };
}

// ---------------------------------------------------------------------------------------------------------------
// Paleta común (corte por la mediana sobre un histograma de 6 bits por canal)
// ---------------------------------------------------------------------------------------------------------------

const key = (r, g, b) => ((r >> 2) << 12) | ((g >> 2) << 6) | (b >> 2);

function buildPalette(frames, size) {
    const counts = new Map();
    for (const f of frames) {
        for (let i = 0; i < f.pixels.length; i += 3) {
            const k = key(f.pixels[i], f.pixels[i + 1], f.pixels[i + 2]);
            counts.set(k, (counts.get(k) || 0) + 1);
        }
    }
    const colors = [...counts].map(([k, n]) => ({ c: [(k >> 12) << 2 | 2, ((k >> 6) & 63) << 2 | 2, (k & 63) << 2 | 2], n }));
    let boxes = [colors];
    while (boxes.length < size) {
        // Divide la caja con más píxeles × rango (las zonas grandes y variadas, como el cielo, reciben más colores)
        let best = -1, bestScore = 0, bestAxis = 0;
        boxes.forEach((box, i) => {
            if (box.length < 2) return;
            let total = 0;
            const lo = [255, 255, 255], hi = [0, 0, 0];
            for (const { c, n } of box) {
                total += n;
                for (let a = 0; a < 3; a++) { lo[a] = Math.min(lo[a], c[a]); hi[a] = Math.max(hi[a], c[a]); }
            }
            const ranges = hi.map((h, a) => h - lo[a]);
            const axis = ranges.indexOf(Math.max(...ranges));
            const score = total * ranges[axis];
            if (score > bestScore) { best = i; bestScore = score; bestAxis = axis; }
        });
        if (best < 0) break;
        const box = boxes[best].sort((p, q) => p.c[bestAxis] - q.c[bestAxis]);
        const half = box.reduce((s, { n }) => s + n, 0) / 2;
        let acc = 0, cut = 1;
        for (let i = 0; i < box.length - 1; i++) {
            acc += box[i].n;
            if (acc >= half) { cut = i + 1; break; }
        }
        boxes.splice(best, 1, box.slice(0, cut), box.slice(cut));
    }
    return boxes.map(box => {
        let r = 0, g = 0, b = 0, n = 0;
        for (const { c, n: w } of box) { r += c[0] * w; g += c[1] * w; b += c[2] * w; n += w; }
        return [Math.round(r / n), Math.round(g / n), Math.round(b / n)];
    });
}

function indexer(palette) {
    const cache = new Int16Array(1 << 18).fill(-1);
    return (r, g, b) => {
        const k = key(r, g, b);
        let idx = cache[k];
        if (idx < 0) {
            let best = Infinity;
            palette.forEach((p, i) => {
                const d = (p[0] - r) ** 2 * 3 + (p[1] - g) ** 2 * 4 + (p[2] - b) ** 2 * 2;
                if (d < best) { best = d; idx = i; }
            });
            cache[k] = idx;
        }
        return idx;
    };
}

// ---------------------------------------------------------------------------------------------------------------
// GIF89a con LZW
// ---------------------------------------------------------------------------------------------------------------

function lzw(indices, minCodeSize) {
    const clear = 1 << minCodeSize, eoi = clear + 1;
    const out = [];
    let cur = 0, bits = 0, codeSize = minCodeSize + 1, next = eoi + 1;
    let dict = new Map();
    const emit = code => {
        cur |= code << bits;
        bits += codeSize;
        while (bits >= 8) { out.push(cur & 0xff); cur >>>= 8; bits -= 8; }
    };
    emit(clear);
    let prefix = indices[0];
    for (let i = 1; i < indices.length; i++) {
        const k = indices[i];
        const entry = prefix * 256 + k;
        const found = dict.get(entry);
        if (found !== undefined) { prefix = found; continue; }
        emit(prefix);
        if (next < 4096) {
            dict.set(entry, next++);
            if (next > (1 << codeSize) && codeSize < 12) codeSize++;
        } else {
            emit(clear);
            dict = new Map();
            codeSize = minCodeSize + 1;
            next = eoi + 1;
        }
        prefix = k;
    }
    emit(prefix);
    emit(eoi);
    if (bits > 0) out.push(cur & 0xff);
    // Subbloques de 255 bytes como máximo
    const blocks = [minCodeSize];
    for (let i = 0; i < out.length; i += 255) {
        const chunk = out.slice(i, i + 255);
        blocks.push(chunk.length, ...chunk);
    }
    blocks.push(0);
    return Buffer.from(blocks);
}

function writeGif(file, frames) {
    const palette = buildPalette(frames, 255);
    while (palette.length < 256) palette.push([0, 0, 0]);
    const toIndex = indexer(palette.slice(0, 255));
    const { width, height } = frames[0];

    const parts = [];
    const header = Buffer.alloc(13);
    header.write("GIF89a", 0, "ascii");
    header.writeUInt16LE(width, 6);
    header.writeUInt16LE(height, 8);
    header[10] = 0xf7; // tabla global de 256 colores
    parts.push(header, Buffer.from(palette.flat()));
    parts.push(Buffer.from([0x21, 0xff, 0x0b, ...Buffer.from("NETSCAPE2.0"), 0x03, 0x01, 0x00, 0x00, 0x00])); // bucle infinito

    // Fotogramas: solo el rectángulo que cambia; lo que no cambia, transparente
    const encoded = [];
    let shown = null;
    for (const f of frames) {
        const idx = new Uint8Array(width * height);
        for (let i = 0, p = 0; i < idx.length; i++, p += 3) idx[i] = toIndex(f.pixels[p], f.pixels[p + 1], f.pixels[p + 2]);
        let x0 = 0, y0 = 0, x1 = width - 1, y1 = height - 1;
        if (shown) {
            x0 = width; y0 = height; x1 = -1; y1 = -1;
            for (let y = 0; y < height; y++) {
                for (let x = 0; x < width; x++) {
                    if (idx[y * width + x] !== shown[y * width + x]) {
                        if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
                    }
                }
            }
            if (x1 < 0) { encoded[encoded.length - 1].delay += FRAME_DELAY; continue; }
        }
        const w = x1 - x0 + 1, h = y1 - y0 + 1;
        const sub = new Uint8Array(w * h);
        for (let y = 0; y < h; y++) {
            for (let x = 0; x < w; x++) {
                const i = (y0 + y) * width + x0 + x;
                sub[y * w + x] = shown && idx[i] === shown[i] ? TRANSPARENT : idx[i];
            }
        }
        encoded.push({ x0, y0, w, h, data: lzw(sub, 8), delay: FRAME_DELAY });
        shown = idx;
    }
    encoded[encoded.length - 1].delay += LAST_FRAME_DELAY;

    for (const f of encoded) {
        const gce = Buffer.from([0x21, 0xf9, 0x04, 0x05, f.delay & 0xff, f.delay >> 8, TRANSPARENT, 0x00]); // no borrar + transparencia
        const desc = Buffer.alloc(10);
        desc[0] = 0x2c;
        desc.writeUInt16LE(f.x0, 1); desc.writeUInt16LE(f.y0, 3);
        desc.writeUInt16LE(f.w, 5); desc.writeUInt16LE(f.h, 7);
        parts.push(gce, desc, f.data);
    }
    parts.push(Buffer.from([0x3b]));
    fs.writeFileSync(file, Buffer.concat(parts));
    return encoded.length;
}

// ---------------------------------------------------------------------------------------------------------------

const [source, destination = path.join(__dirname, "..", "docs", "images")] = process.argv.slice(2);
if (!source) {
    console.error("Uso: node tools/build-showcase-gifs.js <carpeta de capturas> [carpeta de destino]");
    process.exit(1);
}
for (const [scene, name] of Object.entries(SCENES)) {
    const dir = path.join(source, "gif", scene);
    if (!fs.existsSync(dir)) {
        console.warn(`Falta ${dir}: se omite ${name}`);
        continue;
    }
    const files = fs.readdirSync(dir).filter(f => f.endsWith(".png")).sort();
    const frames = files.map(f => downscale(readPng(path.join(dir, f)), WIDTH));
    const out = path.join(destination, name);
    const count = writeGif(out, frames);
    console.log(`${name}: ${files.length} capturas, ${count} fotogramas, ${(fs.statSync(out).size / 1024).toFixed(0)} KB`);
}
