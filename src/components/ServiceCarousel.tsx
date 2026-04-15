import { useState } from "react";
import { SERVICES } from "@/data/services";
import { motion } from "framer-motion";
import { CircularVisualizer } from "./CircularVisualizer";

interface ServiceCarouselProps {
  activeIndex: number;
  onSelect: (index: number) => void;
}

export const ServiceCarousel = ({
  activeIndex,
  onSelect,
}: ServiceCarouselProps) => {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  return (
    <div className="flex items-center gap-5 max-md:gap-2 max-md:overflow-x-auto max-md:pb-1 max-md:scrollbar-none">
      {SERVICES.map((service, i) => {
        const isActive = i === activeIndex;
        const isHovered = i === hoveredIndex;

        return (
          <motion.button
            key={i}
            onClick={() => onSelect(i)}
            onMouseEnter={() => setHoveredIndex(i)}
            onMouseLeave={() => setHoveredIndex(null)}
            className="group flex items-center gap-3"
            animate={{ opacity: isActive ? 1 : 0.4 }}
            whileHover={{ opacity: isActive ? 1 : 0.75 }}
            transition={{ duration: 0.4 }}
          >
            <div className="flex-shrink-0 hidden md:block">
              <CircularVisualizer
                size={72}
                isActive={isActive}
                isHovered={isHovered}
                glowColor={service.glowColor}
                imageUrl={service.thumbImage}
                imageAlt={service.thumbLabel}
              />
            </div>
            <div className="flex-shrink-0 md:hidden">
              <CircularVisualizer
                size={56}
                isActive={isActive}
                isHovered={isHovered}
                glowColor={service.glowColor}
                imageUrl={service.thumbImage}
                imageAlt={service.thumbLabel}
              />
            </div>

            <div className="text-left hidden sm:block min-w-0">
              <motion.p
                className="font-body text-xs font-medium text-foreground leading-tight whitespace-nowrap"
                animate={{ opacity: isActive ? 1 : 0.7 }}
                transition={{ duration: 0.3 }}
              >
                {service.thumbLabel}
              </motion.p>
              <motion.p
                className="font-body text-[10px] text-muted-foreground leading-tight mt-0.5 whitespace-nowrap"
                animate={{ opacity: isActive ? 1 : 0.5 }}
                transition={{ duration: 0.35, delay: 0.05 }}
              >
                {service.thumbSub}
              </motion.p>
            </div>
          </motion.button>
        );
      })}
    </div>
  );
};
