#!/usr/bin/env python3 -I
"""Packs the Flow background's five morph meshes into app/src/main/res/raw/flow_meshes.bin.

Usage: python3 -I tools/gen_flow_meshes.py <SubdividedMeshes.json>

The input is the reverse-engineered mesh data (5 meshes, each 1681 vertices = a 41x41 subdivided 6x6 control mesh, 1600 quads, one shared
topology). It is not kept in the repo; pass a local copy. Layout, little endian:
  int32 magic 'FLOW', int32 meshCount, int32 vertexCount, int32 indexCount
  float32 uv[vertexCount * 2]            (the texture coordinate of each vertex; also its un-morphed grid position)
  uint16  indices[indexCount]            (triangles: per quad [f0,f2,f3, f0,f1,f2])
  per mesh: float32 from[vertexCount * 2], float32 to[vertexCount * 2]   (clip-space positions at the two ends of the morph)
"""
import json
import struct
import sys
from pathlib import Path

src, out = Path(sys.argv[1]), Path(__file__).resolve().parent.parent / "app/src/main/res/raw/flow_meshes.bin"
meshes = sorted(json.loads(src.read_text()), key=lambda m: m["index"])
first = meshes[0]["initial"]
n = len(first["vertices"])
faces = first["faces"]
for m in meshes:
    assert len(m["initial"]["vertices"]) == n and m["initial"]["faces"] == faces == m["morphed"]["faces"]
    assert all(abs(a[0] - b[0]) < 1e-6 and abs(a[1] - b[1]) < 1e-6 for a, b in zip(m["initial"]["vertices"], first["vertices"]))

indices = [i for f in faces for i in (f[0], f[2], f[3], f[0], f[1], f[2])]
assert max(indices) < 65536
buf = bytearray(struct.pack("<4i", 0x574F4C46, len(meshes), n, len(indices)))
buf += struct.pack(f"<{n * 2}f", *[c for v in first["vertices"] for c in (v[0], v[1])])
buf += struct.pack(f"<{len(indices)}H", *indices)
for m in meshes:
    for key in ("initial", "morphed"):
        buf += struct.pack(f"<{n * 2}f", *[c for v in m[key]["vertices"] for c in (v[2], v[3])])
out.parent.mkdir(parents=True, exist_ok=True)
out.write_bytes(buf)
print(f"wrote {out} ({len(buf)} bytes): {len(meshes)} meshes, {n} vertices, {len(indices)} indices")
