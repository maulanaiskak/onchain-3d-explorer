"use client";

import { Canvas } from "@react-three/fiber";
import { OrbitControls, Stars } from "@react-three/drei";
import { Suspense, useMemo } from "react";
import { computeLayout } from "@/app/lib/graph-layout";
import { useGraphStore } from "@/app/store/graph-store";
import GraphNode from "./GraphNode";
import GraphEdge from "./GraphEdge";

export default function GraphCanvas() {
  const { nodes, edges, selectedId, setSelected } = useGraphStore();
  const layout = useMemo(() => computeLayout(nodes, edges), [nodes, edges]);

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
        minDistance={5}
        maxDistance={40}
      />
    </Canvas>
  );
}
