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

function shortAddr(id: string) {
  return id.length > 10 ? id.slice(0, 4) + "…" + id.slice(-3) : id;
}

export default function GraphNode({ node, isSelected, onSelect }: Props) {
  const meshRef = useRef<Mesh>(null);
  const [hovered, setHovered] = useState(false);
  const { overlay } = useGraphStore();

  const highlighted = overlay.highlightedIds.has(node.id);
  const annotation  = overlay.annotations.get(node.id);
  const isFocused   = overlay.focusedId === node.id;

  const radius  = 0.12 + node.weight * 0.28;
  const color   = nodeColor(node.recency, node.isWhale, highlighted);
  const emissive = isSelected || isFocused ? 1.0 : highlighted ? 0.7 : hovered ? 0.45 : 0.18;
  const scale    = isSelected || isFocused ? 1.4 : hovered ? 1.2 : 1;

  const showLabel = isSelected || isFocused || hovered || !!annotation
    || node.isWhale || node.weight > 0.65;

  return (
    <group position={node.position}>
      <mesh
        ref={meshRef}
        scale={scale}
        onPointerOver={(e) => { e.stopPropagation(); setHovered(true); }}
        onPointerOut={() => setHovered(false)}
        onClick={(e) => { e.stopPropagation(); onSelect(node.id); }}
      >
        <sphereGeometry args={[radius, 14, 14]} />
        <meshStandardMaterial
          color={color}
          emissive={color}
          emissiveIntensity={emissive}
          roughness={0.3}
          metalness={0.6}
          transparent
          opacity={0.45 + node.recency * 0.55}
        />
      </mesh>

      {(isSelected || isFocused) && (
        <mesh rotation={[Math.PI / 2, 0, 0]}>
          <ringGeometry args={[radius * 1.5, radius * 1.75, 28]} />
          <meshBasicMaterial color={color} transparent opacity={0.6} />
        </mesh>
      )}

      {highlighted && !isSelected && (
        <mesh rotation={[Math.PI / 2, 0, 0]}>
          <ringGeometry args={[radius * 1.3, radius * 1.5, 28]} />
          <meshBasicMaterial color="#a855f7" transparent opacity={0.4} />
        </mesh>
      )}

      {showLabel && (
        <Html distanceFactor={12} center>
          <div className="px-1.5 py-0.5 bg-black/80 text-cyan-300 text-[10px] rounded whitespace-nowrap pointer-events-none font-mono border border-cyan-500/30 leading-none">
            {annotation ?? shortAddr(node.id)}
            {node.isWhale && " 🐋"}
          </div>
        </Html>
      )}
    </group>
  );
}
