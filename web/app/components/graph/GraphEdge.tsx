"use client";

import { useMemo } from "react";
import * as THREE from "three";
import { PositionedEdge } from "@/app/lib/graph-layout";

interface Props {
  edge: PositionedEdge;
}

export default function GraphEdge({ edge }: Props) {
  const lineObject = useMemo(() => {
    const points = [
      new THREE.Vector3(...edge.fromPos),
      new THREE.Vector3(...edge.toPos),
    ];
    const geo = new THREE.BufferGeometry().setFromPoints(points);
    const mat = new THREE.LineBasicMaterial({
      color: "#22d3ee",
      transparent: true,
      opacity: 0.2 + edge.valueNorm * 0.5,
    });
    return new THREE.Line(geo, mat);
  }, [edge.fromPos, edge.toPos, edge.valueNorm]);

  return <primitive object={lineObject} />;
}
