import { useState, useEffect, useCallback, useRef } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { SERVICES } from "@/data/services";
import { ServiceCarousel } from "./ServiceCarousel";
import { ArrowLeft, ArrowRight } from "lucide-react";

const SLIDE_DURATION = 12000;

const easeSmooth: [number, number, number, number] = [0.25, 0.46, 0.45, 0.94];
const easeSnap: [number, number, number, number] = [0.22, 1, 0.36, 1];
const easeAccel: [number, number, number, number] = [0.55, 0, 1, 0.45];

const bgVariants = {
  enter: (dir: number) => ({
    opacity: 0,
    scale: 1.08,
    x: dir > 0 ? 60 : -60,
  }),
  center: {
    opacity: 1,
    scale: 1,
    x: 0,
    transition: { duration: 1, ease: easeSmooth },
  },
  exit: (dir: number) => ({
    opacity: 0,
    scale: 0.97,
    x: dir > 0 ? -40 : 40,
    transition: { duration: 0.7, ease: easeSmooth },
  }),
};

const textContainerVariants = {
  enter: { transition: { staggerChildren: 0.1, delayChildren: 0.25 } },
  exit: { transition: { staggerChildren: 0.05, staggerDirection: -1 } },
};

const textLineVariants = {
  initial: (dir: number) => ({
    opacity: 0,
    y: dir > 0 ? 40 : -40,
    filter: "blur(8px)",
  }),
  enter: {
    opacity: 1,
    y: 0,
    filter: "blur(0px)",
    transition: { duration: 0.6, ease: easeSnap },
  },
  exit: (dir: number) => ({
    opacity: 0,
    y: dir > 0 ? -30 : 30,
    filter: "blur(6px)",
    transition: { duration: 0.35, ease: easeAccel },
  }),
};

export const HeroSlideshow = () => {
  const [activeIndex, setActiveIndex] = useState(0);
  const [direction, setDirection] = useState(1);
  const [progress, setProgress] = useState(0);
  const progressRef = useRef<number>(0);
  const rafRef = useRef<number>(0);
  const startTimeRef = useRef<number>(Date.now());

  const goTo = useCallback(
    (index: number) => {
      setDirection(index > activeIndex ? 1 : -1);
      setActiveIndex(index);
      setProgress(0);
      startTimeRef.current = Date.now();
    },
    [activeIndex]
  );

  const goPrev = useCallback(() => {
    setDirection(-1);
    setActiveIndex((prev) => (prev - 1 + SERVICES.length) % SERVICES.length);
    setProgress(0);
    startTimeRef.current = Date.now();
  }, []);

  const goNext = useCallback(() => {
    setDirection(1);
    setActiveIndex((prev) => (prev + 1) % SERVICES.length);
    setProgress(0);
    startTimeRef.current = Date.now();
  }, []);

  // Progress bar animation + auto-advance
  useEffect(() => {
    startTimeRef.current = Date.now();
    const tick = () => {
      const elapsed = Date.now() - startTimeRef.current;
      const p = Math.min(elapsed / SLIDE_DURATION, 1);
      progressRef.current = p;
      setProgress(p);
      if (p >= 1) {
        goNext();
        return;
      }
      rafRef.current = requestAnimationFrame(tick);
    };
    rafRef.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafRef.current);
  }, [activeIndex, goNext]);

  const service = SERVICES[activeIndex];

  return (
    <div className="relative h-screen w-full overflow-hidden bg-background">
      {/* Background images with Ken Burns + directional slide */}
      <AnimatePresence mode="sync" custom={direction}>
        <motion.div
          key={activeIndex}
          custom={direction}
          variants={bgVariants}
          initial="enter"
          animate="center"
          exit="exit"
          className="absolute inset-0 will-change-transform"
        >
          <motion.div
            className="absolute inset-0 bg-cover bg-center"
            style={{ backgroundImage: `url(${service.bgImage})` }}
            animate={{ scale: [1, 1.06] }}
            transition={{ duration: SLIDE_DURATION / 1000, ease: "linear" }}
          />
          {activeIndex !== 0 && (
            <div className="absolute inset-0 bg-black/30" />
          )}
          <div className="absolute inset-0 bg-gradient-to-r from-background/70 via-background/40 to-background/20" />
          <div className="absolute inset-0 bg-gradient-to-t from-background/80 via-transparent to-background/30" />
        </motion.div>
      </AnimatePresence>

      {/* Vertical dot navigation with progress indicator */}
      <div className="absolute left-8 top-1/2 -translate-y-1/2 z-20 flex-col gap-5 hidden md:flex">
        {SERVICES.map((_, i) => (
          <button
            key={i}
            onClick={() => goTo(i)}
            className="group relative flex items-center gap-3"
          >
            <div className="relative flex h-3.5 w-3.5 items-center justify-center">
              <motion.span
                className="block h-full w-full rounded-full border"
                animate={{
                  borderColor:
                    i === activeIndex
                      ? "hsl(0 0% 100%)"
                      : "hsl(0 0% 100% / 0.3)",
                  backgroundColor:
                    i === activeIndex
                      ? "hsl(0 0% 100%)"
                      : "hsl(0 0% 100% / 0)",
                  scale: i === activeIndex ? 1 : 0.7,
                }}
                whileHover={{ scale: 1, borderColor: "hsl(0 0% 100% / 0.7)" }}
                transition={{ duration: 0.4, ease: "easeOut" }}
              />
              {i === activeIndex && (
                <motion.span
                  className="absolute inset-[-5px] rounded-full border border-foreground/30"
                  initial={{ scale: 0.5, opacity: 0 }}
                  animate={{ scale: 1, opacity: 1 }}
                  exit={{ scale: 1.3, opacity: 0 }}
                  transition={{ duration: 0.4, ease: "easeOut" }}
                />
              )}
            </div>
            <motion.span
              className="font-body text-[10px] tracking-[0.3em] uppercase"
              animate={{
                opacity: i === activeIndex ? 1 : 0,
                x: i === activeIndex ? 0 : -5,
                color:
                  i === activeIndex
                    ? "hsl(0 0% 100%)"
                    : "hsl(0 0% 100% / 0.4)",
              }}
              whileHover={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.3 }}
            >
              0{i + 1}
            </motion.span>
          </button>
        ))}
        <div className="absolute left-[6px] top-0 bottom-0 w-[1px] -z-10">
          <div className="h-full w-full bg-foreground/10" />
          <motion.div
            className="absolute top-0 left-0 w-full bg-foreground/40 origin-top"
            style={{
              height: `${((activeIndex + progress) / SERVICES.length) * 100}%`,
            }}
            transition={{ duration: 0.1 }}
          />
        </div>
      </div>

      {/* Content with staggered text animations */}
      <div className="relative z-10 flex h-full flex-col justify-center px-20 pb-40 md:pl-24 max-md:px-5 max-md:pb-56 max-md:justify-end">
        <div className="max-w-2xl">
          <AnimatePresence mode="wait" custom={direction}>
            <motion.div
              key={activeIndex}
              custom={direction}
              variants={textContainerVariants}
              initial="initial"
              animate="enter"
              exit="exit"
            >
              <motion.span
                custom={direction}
                variants={textLineVariants}
                className="inline-block font-body text-[10px] tracking-[0.4em] uppercase text-muted-foreground mb-4 max-md:mb-2"
              >
                Serviço 0{activeIndex + 1}
              </motion.span>

              <motion.h2
                custom={direction}
                variants={textLineVariants}
                className="font-heading text-6xl font-extralight tracking-tight text-foreground mb-5 max-md:text-3xl max-md:mb-3 leading-[1.1]"
              >
                {service.title}
              </motion.h2>

              <motion.p
                custom={direction}
                variants={textLineVariants}
                className="font-body text-sm leading-relaxed text-foreground/60 max-w-lg mb-8 max-md:text-xs max-md:mb-5 max-md:max-w-xs"
              >
                {service.description}
              </motion.p>

              <motion.div custom={direction} variants={textLineVariants}>
                <a
                  href={service.whatsappUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="group inline-flex items-center gap-3 rounded-full border border-foreground/20 bg-foreground/5 px-7 py-3.5 font-body text-sm text-foreground backdrop-blur-md transition-all duration-500 hover:bg-foreground/15 hover:border-foreground/40 hover:tracking-wider hover:px-8 max-md:px-5 max-md:py-3 max-md:text-xs"
                >
                  <span>Entrar em contato</span>
                  <motion.span
                    className="inline-block"
                    animate={{ x: [0, 4, 0] }}
                    transition={{
                      duration: 1.5,
                      repeat: Infinity,
                      ease: "easeInOut",
                    }}
                  >
                    →
                  </motion.span>
                </a>
              </motion.div>
            </motion.div>
          </AnimatePresence>
        </div>
      </div>

      {/* Bottom section */}
      <div className="absolute bottom-0 left-0 right-0 z-20">
        {/* Progress bar */}
        <div className="mx-8 mb-6 h-[1px] bg-foreground/10 max-md:mx-4 max-md:mb-3">
          <motion.div
            className="h-full bg-foreground/50 origin-left"
            style={{ scaleX: progress }}
          />
        </div>

        <div className="flex items-end justify-between px-8 pb-8 max-md:flex-col max-md:gap-3 max-md:px-4 max-md:pb-4">
          {/* Arrow navigation */}
          <div className="flex gap-3 max-md:order-2 max-md:self-start">
            <motion.button
              onClick={goPrev}
              whileHover={{ scale: 1.1 }}
              whileTap={{ scale: 0.92 }}
              className="flex h-12 w-12 items-center justify-center rounded-full border border-foreground/20 text-foreground backdrop-blur-sm transition-colors hover:bg-foreground/10 hover:border-foreground/40 max-md:h-10 max-md:w-10"
            >
              <ArrowLeft className="h-4 w-4" />
            </motion.button>
            <motion.button
              onClick={goNext}
              whileHover={{ scale: 1.1 }}
              whileTap={{ scale: 0.92 }}
              className="flex h-12 w-12 items-center justify-center rounded-full border border-foreground/20 text-foreground backdrop-blur-sm transition-colors hover:bg-foreground/10 hover:border-foreground/40 max-md:h-10 max-md:w-10"
            >
              <ArrowRight className="h-4 w-4" />
            </motion.button>
          </div>

          {/* Service carousel */}
          <div className="max-md:order-1 max-md:w-full">
            <ServiceCarousel activeIndex={activeIndex} onSelect={goTo} />
          </div>
        </div>
      </div>
    </div>
  );
};
