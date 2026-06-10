"use client";

import { useRef, useState } from "react";
import { Mesh } from "three";
import { Html } from "@react-three/drei";
import { PositionedNode } from "@/app/lib/graph-layout";

interface Props {
  node: PositionedNode;
  isSelected: boolean;
  onSelect: (id: string) => void;
}

function nodeColor(recency: number, isWhale: boolean): string {
  if (isWhale) return "#a855f7";
  if (recency > 0.7) return "#22d3ee";
  if (recency > 0.4) return "#6366f1";
  return "#374151";
}

export default function GraphNode({ node, isSelected, onSelect }: Props) {
  const meshRef = useRef<Mesh>(null);
  const [hovered, setHovered] = useState(false);
  const radius = 0.12 + node.weight * 0.28;
  const color = nodeColor(node.recency, node.isWhale);

  return (
    <group position={node.position}>
      <mesh
        ref={meshRef}
        onPointerOver={() => setHovered(true)}
        onPointerOut={() => setHovered(false)}
        onClick={() => onSelect(node.id)}
        scale={isSelected || hovered ? 1.3 : 1}
      >
        <sphereGeometry args={[radius, 16, 16]} />
        <meshStandardMaterial
          color={color}
          emissive={color}
          emissiveIntensity={isSelected ? 0.9 : hovered ? 0.5 : 0.2}
          roughness={0.3}
          metalness={0.6}
          transparent
          opacity={0.4 + node.recency * 0.6}
        />
      </mesh>

      {isSelected && (
        <mesh>
          <ringGeometry args={[radius * 1.4, radius * 1.7, 32]} />
          <meshBasicMaterial color={color} transparent opacity={0.5} />
        </mesh>
      )}

      {(hovered || isSelected) && (
        <Html distanceFactor={10} center>
          <div className="px-2 py-1 bg-black/80 text-cyan-300 text-xs rounded whitespace-nowrap pointer-events-none font-mono border border-cyan-500/40">
            {node.label ?? node.id.slice(0, 10) + "…"}
            {node.isWhale && " 🐋"}
          </div>
        </Html>
      )}
    </group>
  );
}
