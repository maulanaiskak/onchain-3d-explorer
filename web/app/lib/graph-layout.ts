import { NodeDTO, EdgeDTO } from "./mock-data";

export interface PositionedNode extends NodeDTO {
  position: [number, number, number];
}

export interface PositionedEdge extends EdgeDTO {
  fromPos: [number, number, number];
  toPos: [number, number, number];
}

// Simple force-directed layout using a few iterations of repulsion + attraction
export function computeLayout(
  nodes: NodeDTO[],
  edges: EdgeDTO[]
): { nodes: PositionedNode[]; edges: PositionedEdge[] } {
  const SPREAD = 6;
  const pos = new Map<string, [number, number, number]>();

  // Initialise positions on a sphere
  nodes.forEach((n, i) => {
    const phi = Math.acos(1 - (2 * (i + 0.5)) / nodes.length);
    const theta = Math.PI * (1 + Math.sqrt(5)) * i;
    pos.set(n.id, [
      SPREAD * Math.sin(phi) * Math.cos(theta),
      SPREAD * Math.sin(phi) * Math.sin(theta),
      SPREAD * Math.cos(phi),
    ]);
  });

  // 20 iterations — enough for readable layout without blocking the main thread
  for (let iter = 0; iter < 20; iter++) {
    const forces = new Map<string, [number, number, number]>(
      nodes.map((n) => [n.id, [0, 0, 0]])
    );

    // Repulsion between all pairs
    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const a = pos.get(nodes[i].id)!;
        const b = pos.get(nodes[j].id)!;
        const dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        const dist = Math.sqrt(dx * dx + dy * dy + dz * dz) || 0.01;
        const force = 5 / (dist * dist);
        const fa = forces.get(nodes[i].id)!;
        const fb = forces.get(nodes[j].id)!;
        fa[0] += (dx / dist) * force; fa[1] += (dy / dist) * force; fa[2] += (dz / dist) * force;
        fb[0] -= (dx / dist) * force; fb[1] -= (dy / dist) * force; fb[2] -= (dz / dist) * force;
      }
    }

    // Attraction along edges
    for (const e of edges) {
      const a = pos.get(e.from);
      const b = pos.get(e.to);
      if (!a || !b) continue;
      const dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
      const dist = Math.sqrt(dx * dx + dy * dy + dz * dz) || 0.01;
      const force = dist * 0.05;
      const fa = forces.get(e.from)!;
      const fb = forces.get(e.to)!;
      fa[0] += (dx / dist) * force; fa[1] += (dy / dist) * force; fa[2] += (dz / dist) * force;
      fb[0] -= (dx / dist) * force; fb[1] -= (dy / dist) * force; fb[2] -= (dz / dist) * force;
    }

    // Apply with damping
    for (const n of nodes) {
      const p = pos.get(n.id)!;
      const f = forces.get(n.id)!;
      pos.set(n.id, [p[0] + f[0] * 0.1, p[1] + f[1] * 0.1, p[2] + f[2] * 0.1]);
    }
  }

  const positionedNodes: PositionedNode[] = nodes.map((n) => ({
    ...n,
    position: pos.get(n.id)!,
  }));

  const positionedEdges: PositionedEdge[] = edges
    .map((e) => {
      const fromPos = pos.get(e.from);
      const toPos = pos.get(e.to);
      if (!fromPos || !toPos) return null;
      return { ...e, fromPos, toPos };
    })
    .filter(Boolean) as PositionedEdge[];

  return { nodes: positionedNodes, edges: positionedEdges };
}
