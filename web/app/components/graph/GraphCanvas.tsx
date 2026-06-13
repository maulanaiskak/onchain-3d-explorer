"use client";

import { Canvas } from "@react-three/fiber";
import { OrbitControls, Stars } from "@react-three/drei";
import { Suspense, useEffect, useRef, useState } from "react";
import { computeLayout, PositionedNode, PositionedEdge } from "@/app/lib/graph-layout";
import { useGraphStore } from "@/app/store/graph-store";
import GraphNode from "./GraphNode";
import GraphEdge from "./GraphEdge";

interface Layout {
  nodes: PositionedNode[];
  edges: PositionedEdge[];
}

export default function GraphCanvas() {
  const nodes     = useGraphStore((s) => s.nodes);
  const edges     = useGraphStore((s) => s.edges);
  const selectedId = useGraphStore((s) => s.selectedId);
  const setSelected = useGraphStore((s) => s.setSelected);

  const [layout, setLayout] = useState<Layout>({ nodes: [], edges: [] });
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const nodeKeyRef  = useRef<string>("");

  // Recompute layout only when node set changes, debounced so rapid upserts don't spam it
  useEffect(() => {
    const key = nodes.map((n) => n.id).join(",");
    if (key === nodeKeyRef.current) return;

    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      nodeKeyRef.current = key;
      setLayout(computeLayout(nodes, edges));
    }, 800);

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [nodes]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <Canvas
      camera={{ position: [0, 0, 18], fov: 60 }}
      className="w-full h-full"
      gl={{ antialias: true }}
    >
      <color attach="background" args={["#050914"]} />
      <ambientLight intensity={0.3} />
      <pointLight position={[10, 10, 10]} intensity={1} />
      <pointLight position={[-10, -10, -10]} intensity={0.5} color="#6366f1" />

      <Stars radius={80} depth={50} count={3000} factor={3} fade />

      <Suspense fallback={null}>
        {layout.edges.map((e) => (
          <GraphEdge key={e.id} edge={e} />
        ))}
        {layout.nodes.map((n) => (
          <GraphNode
            key={n.id}
            node={n}
            isSelected={selectedId === n.id}
            onSelect={setSelected}
          />
        ))}
      </Suspense>

      <OrbitControls
        enableDamping
        dampingFactor={0.05}
        minDistance={2}
        maxDistance={120}
        zoomSpeed={1.5}
      />
    </Canvas>
  );
}
