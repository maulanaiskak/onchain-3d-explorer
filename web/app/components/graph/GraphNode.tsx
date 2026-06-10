"use client";

import { useRef, useState } from "react";
import { Mesh } from "three";
import { Html } from "@react-three/drei";
import { PositionedNode } from "@/app/lib/graph-layout";
import { useGraphStore } from "@/app/store/graph-store";

interface Props {
  node: PositionedNode;
  isSelected: boolean;
  onSelect: (id: string) => void;
}

function nodeColor(recency: number, isWhale: boolean, highlighted: boolean): string {
  if (highlighted) return "#a855f7";
  if (isWhale) return "#f59e0b";
  if (recency > 0.7) return "#22d3ee";
  if (recency > 0.4) return "#6366f1";
  return "#374151";
}

export default function GraphNode({ node, isSelected, onSelect }: Props) {
  const meshRef = useRef<Mesh>(null);
  const [hovered, setHovered] = useState(false);
  const { overlay } = useGraphStore();

  const highlighted = overlay.highlightedIds.has(node.id);
  const annotation  = overlay.annotations.get(node.id);
  const isFocused   = overlay.focusedId === node.id;

  const radius = 0.12 + node.weight * 0.28;
  const color  = nodeColor(node.recency, node.isWhale, highlighted);
  const active = isSelected || isFocused || hovered || highlighted;

  return (
    <group position={node.position}>
      <mesh
        ref={meshRef}
        onPointerOver={() => setHovered(true)}
        onPointerOut={() => setHovered(false)}
        onClick={() => onSelect(node.id)}
        scale={isSelected || isFocused ? 1.4 : hovered ? 1.2 : 1}
      >
        <sphereGeometry args={[radius, 16, 16]} />
        <meshStandardMaterial
          color={color}
          emissive={color}
          emissiveIntensity={isSelected || isFocused ? 1.0 : highlighted ? 0.7 : hovered ? 0.4 : 0.15}
          roughness={0.3}
          metalness={0.6}
          transparent
          opacity={0.4 + node.recency * 0.6}
        />
      </mesh>

      {/* Selection / focus ring */}
      {(isSelected || isFocused) && (
        <mesh rotation={[Math.PI / 2, 0, 0]}>
          <ringGeometry args={[radius * 1.5, radius * 1.8, 32]} />
          <meshBasicMaterial color={color} transparent opacity={0.6} />
        </mesh>
      )}

      {/* Highlight pulse ring */}
      {highlighted && !isSelected && (
        <mesh rotation={[Math.PI / 2, 0, 0]}>
          <ringGeometry args={[radius * 1.3, radius * 1.5, 32]} />
          <meshBasicMaterial color="#a855f7" transparent opacity={0.4} />
        </mesh>
      )}

      {/* Label tooltip or annotation */}
      {(active || annotation) && (
        <Html distanceFactor={10} center>
          <div className="px-2 py-1 bg-black/80 text-cyan-300 text-xs rounded whitespace-nowrap pointer-events-none font-mono border border-cyan-500/40">
            {annotation ?? (node.label ?? node.id.slice(0, 10) + "…")}
            {node.isWhale && " 🐋"}
          </div>
        </Html>
      )}
    </group>
  );
}
