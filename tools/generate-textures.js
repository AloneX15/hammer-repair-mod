// Genera la textura del martillo (16x16) y el icono del mod (128x128) sin dependencias.
// Uso: node tools/generate-textures.js [--ascii]
const zlib = require("zlib");
const fs = require("fs");
const path = require("path");

const PALETTE = {
    ".": null,
    K: [24, 20, 26],      // contorno
    D: [52, 45, 52],      // netherita oscura
    N: [74, 64, 72],      // netherita
    L: [105, 93, 101],    // netherita clara
    W: [150, 138, 145],   // brillo
    G: [214, 160, 58],    // banda dorada
    Y: [247, 214, 120],   // brillo dorado
    H: [120, 64, 26],     // mango oscuro (vara de blaze)
    O: [204, 120, 40],    // mango
    P: [250, 190, 80],    // mango claro
    C: [100, 230, 255],   // destello del núcleo
};

// Mango de vara de blaze en diagonal y cabeza de netherita perpendicular con el núcleo pesado en el centro
const SPRITE = [
    ".......KWK......",
    "......KWLLK.....",
    ".....KWNNLLK....",
    "....KWDNNNLLK...",
    ".....KDDNCCLLK..",
    "......KDDCCNLLK.",
    ".......KDDNNNLNK",
    "......KKKDDNNNK.",
    ".....KGYKKDDDK..",
    "....KOPYK.KDK...",
    "...KOPPK...K....",
    "..KOPPK.........",
    ".KOOPK..........",
    "KHOPK...........",
    "KHHK............",
    ".KK.............",
];

function rgba(grid) {
    const h = grid.length, w = grid[0].length;
    const px = Buffer.alloc(w * h * 4);
    grid.forEach((row, y) => {
        if (row.length !== w) throw new Error(`fila ${y} con ${row.length} columnas`);
        [...row].forEach((ch, x) => {
            if (!(ch in PALETTE)) throw new Error(`color desconocido ${ch}`);
            const c = PALETTE[ch];
            const o = (y * w + x) * 4;
            if (c) { px[o] = c[0]; px[o + 1] = c[1]; px[o + 2] = c[2]; px[o + 3] = 255; }
        });
    });
    return { w, h, px };
}

function png({ w, h, px }) {
    const raw = Buffer.alloc((w * 4 + 1) * h);
    for (let y = 0; y < h; y++) {
        raw[y * (w * 4 + 1)] = 0;
        px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
    }
    const table = [];
    for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        table[n] = c >>> 0;
    }
    const crc = (buf) => { let r = 0xffffffff; for (const v of buf) r = table[(r ^ v) & 255] ^ (r >>> 8); return (r ^ 0xffffffff) >>> 0; };
    const chunk = (type, data) => {
        const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
        const body = Buffer.concat([Buffer.from(type), data]);
        const sum = Buffer.alloc(4); sum.writeUInt32BE(crc(body));
        return Buffer.concat([len, body, sum]);
    };
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6; // RGBA
    return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr),
        chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}

// Icono: fondo de piedra oscura con borde y el martillo ampliado x6 centrado
function icon(sprite) {
    const W = 128, scale = 6, off = Math.floor((W - 16 * scale) / 2);
    const px = Buffer.alloc(W * W * 4);
    for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
        const o = (y * W + x) * 4;
        const border = x < 6 || y < 6 || x >= W - 6 || y >= W - 6;
        const noise = ((x * 7 + y * 13) ^ (x * y)) % 9;
        const t = 1 - (y / W) * 0.35;
        const base = border ? [40, 28, 22] : [62 + noise, 54 + noise, 60 + noise];
        px[o] = Math.round(base[0] * (border ? 1 : t)); px[o + 1] = Math.round(base[1] * (border ? 1 : t));
        px[o + 2] = Math.round(base[2] * (border ? 1 : t)); px[o + 3] = 255;
        if (border && (x < 3 || y < 3 || x >= W - 3 || y >= W - 3)) { px[o] = 214; px[o + 1] = 160; px[o + 2] = 58; }
    }
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
        const s = (y * 16 + x) * 4;
        if (sprite.px[s + 3] === 0) continue;
        for (let dy = 0; dy < scale; dy++) for (let dx = 0; dx < scale; dx++) {
            const o = ((off + y * scale + dy) * W + off + x * scale + dx) * 4;
            sprite.px.copy(px, o, s, s + 4);
        }
    }
    return { w: W, h: W, px };
}

const root = path.join(__dirname, "..", "src", "main", "resources", "assets", "hammer_repair");
if (process.argv.includes("--ascii")) {
    console.log(SPRITE.join("\n"));
} else {
    const sprite = rgba(SPRITE);
    fs.writeFileSync(path.join(root, "textures", "item", "repair_hammer.png"), png(sprite));
    fs.writeFileSync(path.join(root, "icon.png"), png(icon(sprite)));
    console.log("Texturas generadas en", root);
}
