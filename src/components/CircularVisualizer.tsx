import { memo } from "react";

interface CircularVisualizerProps {
  size: number;
  isActive: boolean;
  isHovered?: boolean;
  glowColor?: string;
  imageUrl: string;
  imageAlt: string;
}

export const CircularVisualizer = memo(({ size, isActive, imageUrl, imageAlt }: CircularVisualizerProps) => {
  return (
    <div
      className="rounded-full overflow-hidden"
      style={{
        width: size,
        height: size,
        opacity: isActive ? 1 : 0.6,
        transition: "opacity 0.3s ease",
      }}
    >
      <img
        src={imageUrl}
        alt={imageAlt}
        className="h-full w-full object-cover"
      />
    </div>
  );
});

CircularVisualizer.displayName = "CircularVisualizer";
