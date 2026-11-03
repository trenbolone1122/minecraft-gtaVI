// Bake the Leonida road network into data/leonida/road/roads.bin.gz for the road module.
// Runs our pipeline's own road engineering (profiles, render preparation, highway vertical
// engineering) over community-world-v1 with the real terrain provider, then writes the
// segments and junction nodes in a compact big-endian binary the Java side reads.
//   node tools/roadbake/bake.mjs [pipelineSrc] [upstreamDir]
import fs from 'node:fs'; import path from 'node:path'; import zlib from 'node:zlib';
const pipe = process.argv[2] || '/home/user/workspace/upstream/pipeline/src';
const up = process.argv[3] || '/home/user/workspace/upstream';
const { readPipelineInput } = await import(pipe + '/lib/read-input.js');
const { buildRoadProfiles } = await import(pipe + '/explorer/src/procedural-road-profiles.js');
const { prepareRenderableRoadProfiles } = await import(pipe + '/explorer/src/procedural-roads.js');
const { engineerHighwayProfiles } = await import(pipe + '/explorer/src/procedural-highway-profiles.js');
const env = path.join(up, 'cw-input-road.json');
fs.writeFileSync(env, JSON.stringify({ version: 1, seed: 1031, terrain: { kind: 'community-manifest', manifest: 'community-world-v1/manifest.json' } }));
const input = readPipelineInput(env); const t = input.terrain;
const p0 = buildRoadProfiles(input.roads, { terrainHeight: t.heightAt, isWater: t.isWater, waterLevelAt: t.waterLevelAt });
const p = engineerHighwayProfiles(prepareRenderableRoadProfiles(p0));
const nodeIdx = new Map(), nodes = [];
for (const n of p.nodes) { nodeIdx.set(String(n.id), nodes.length); nodes.push(n); }
let size = 12 + nodes.length * 12 + 4;
for (const s of p.segments) size += 1 + 4 + 8 + 4 + s.samples.length * 17;
const buf = new DataView(new ArrayBuffer(size)); let o = 0;
const i32 = v => { buf.setInt32(o, v); o += 4; }, f32 = v => { buf.setFloat32(o, v); o += 4; }, u8 = v => { buf.setUint8(o, v); o += 1; };
i32(0x4c524431); i32(1); i32(nodes.length);
for (const n of nodes) { f32(n.x); f32(n.y); f32(n.z); }
i32(p.segments.length);
let wet = 0;
for (const s of p.segments) {
  u8(s.kind === 'highway' ? 1 : 0); f32(s.width);
  i32(nodeIdx.has(String(s.startNode)) ? nodeIdx.get(String(s.startNode)) : -1);
  i32(nodeIdx.has(String(s.endNode)) ? nodeIdx.get(String(s.endNode)) : -1);
  i32(s.samples.length);
  for (const q of s.samples) {
    const water = t.isWater(q.x, q.y) ? 1 : 0; wet += water;
    f32(q.x); f32(q.y); f32(q.z); f32(q.ground); u8((q.bridge ? 1 : 0) | (water << 1));
  }
}
const out = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../src/main/resources/data/leonida/road/roads.bin.gz');
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, zlib.gzipSync(Buffer.from(buf.buffer), { level: 9 }));
console.log(`segments ${p.segments.length} nodes ${nodes.length} samples ${p.stats.samples} wet ${wet} -> ${out} ${fs.statSync(out).size} bytes`);
