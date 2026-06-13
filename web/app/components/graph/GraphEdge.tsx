"use client";

import { useMemo } from "react";
import * as THREE from "three";
import { PositionedEdge } from "@/app/lib/graph-layout";

interface Props {
  edge: PositionedEdge;
}

function edgeColor(valueNorm: number): string {
  if (valueNorm > 0.7) return "#f59e0b";
  if (valueNorm > 0.4) return "#22d3ee";
  return "#6366f1";
}

export default function GraphEdge({ edge }: Props) {
  const color   = edgeColor(edge.valueNorm);
  const opacity = 0.15 + edge.valueNorm * 0.55;

  const { lineObject, arrowPosition, arrowQuaternion } = useMemo(() => {
    const from = new THREE.Vector3(...edge.fromPos);
    const to   = new THREE.Vector3(...edge.toPos);

    // Line
    const geo = new THREE.BufferGeometry().setFromPoints([from, to]);
    const mat = new THREE.LineBasicMaterial({ color, transparent: true, opacity });
    const lineObject = new THREE.Line(geo, mat);

    // Arrow at 85%
    const dir = new THREE.Vector3().subVectors(to, from);
    const arrowPosition = from.clone().addScaledVector(dir, 0.85);
    const arrowQuaternion = new THREE.Quaternion().setFromUnitVectors(
      new THREE.Vector3(0, 1, 0),
      dir.normalize()
    );

    return { lineObject, arrowPosition, arrowQuaternion };
  }, [edge.fromPos, edge.toPos, color, opacity]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <group>
      <primitive object={lineObject} />
      <mesh position={arrowPosition} quaternion={arrowQuaternion}>
        <coneGeometry args={[0.035, 0.12, 6]} />
        <meshBasicMaterial color={color} transparent opacity={opacity + 0.1} />
      </mesh>
    </group>
  );
}
