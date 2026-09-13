import { useEffect, useRef, useState, type CSSProperties } from "react";
import {
  AnimatePresence,
  motion,
  useReducedMotion,
  useScroll,
  useSpring,
  useTransform,
} from "framer-motion";
import { ArrowDownRight, ArrowUpRight, Menu, X } from "lucide-react";
import { SERVICES } from "@/data/services";

const WHATSAPP_URL = "https://wa.me/5585986067012";
const EASE: [number, number, number, number] = [0.22, 1, 0.36, 1];

const BrandSymbol = ({ className = "" }: { className?: string }) => (
  <svg
    viewBox="0 0 120 120"
    aria-hidden="true"
    className={className}
    fill="none"
  >
    <path
      d="M61 9 103 83H82L60 44 39 83H17L61 9Z"
      fill="currentColor"
    />
    <path d="M29 92 48 59 59 78 51 92H29Z" fill="currentColor" opacity=".52" />
    <path
      d="M19 78a47 47 0 0 1 5-47M96 31a47 47 0 0 1 5 47"
      stroke="currentColor"
      strokeWidth="2.2"
      strokeLinecap="round"
    />
  </svg>
);

const BrandName = ({ compact = false }: { compact?: boolean }) => (
  <span className={`brand-name ${compact ? "brand-name--compact" : ""}`}>
    GL<span className="brand-a">A</span>SS MAIND
  </span>
);

function Intro() {
  const reduceMotion = useReducedMotion();
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    const timer = window.setTimeout(() => setVisible(false), reduceMotion ? 120 : 980);
    return () => window.clearTimeout(timer);
  }, [reduceMotion]);

  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          className="intro"
          initial={{ opacity: 1 }}
          exit={{ y: "-100%" }}
          transition={{ duration: reduceMotion ? 0.15 : 0.8, ease: EASE }}
        >
          <motion.div
            className="intro__mark"
            initial={reduceMotion ? false : { opacity: 0, scale: 0.86, rotate: -4 }}
            animate={{ opacity: 1, scale: 1, rotate: 0 }}
            transition={{ duration: 0.62, ease: EASE }}
          >
            <BrandSymbol />
          </motion.div>
          <motion.div
            className="intro__line"
            initial={{ scaleX: 0 }}
            animate={{ scaleX: 1 }}
            transition={{ duration: 0.65, delay: 0.2, ease: EASE }}
          />
          <motion.span
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.3, duration: 0.45 }}
          >
            Glass Maind · Creative Office
          </motion.span>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function Header() {
  const [open, setOpen] = useState(false);
  const items = [
    ["Início", "#inicio"],
    ["Sobre", "#sobre"],
    ["Serviços", "#servicos"],
    ["Método", "#metodo"],
    ["Contato", "#contato"],
  ];

  useEffect(() => {
    document.body.style.overflow = open ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [open]);

  return (
    <>
      <header className="site-header">
        <a href="#inicio" className="site-header__brand" aria-label="Glass Maind — início">
          <BrandSymbol className="site-header__symbol" />
          <BrandName compact />
        </a>
        <div className="site-header__right">
          <a className="header-contact" href={WHATSAPP_URL} target="_blank" rel="noreferrer">
            iniciar projeto <ArrowUpRight size={14} />
          </a>
          <button
            className="menu-button"
            type="button"
            onClick={() => setOpen(true)}
            aria-label="Abrir menu"
          >
            <Menu size={19} />
          </button>
        </div>
      </header>

      <AnimatePresence>
        {open && (
          <motion.div
            className="menu-overlay"
            initial={{ clipPath: "inset(0 0 100% 0)" }}
            animate={{ clipPath: "inset(0 0 0% 0)" }}
            exit={{ clipPath: "inset(0 0 100% 0)" }}
            transition={{ duration: 0.72, ease: EASE }}
          >
            <div className="menu-overlay__top">
              <div className="menu-overlay__brand">
                <BrandSymbol />
                <BrandName compact />
              </div>
              <button type="button" onClick={() => setOpen(false)} aria-label="Fechar menu">
                <X size={22} />
              </button>
            </div>

            <nav className="menu-overlay__nav" aria-label="Navegação principal">
              {items.map(([label, href], index) => (
                <motion.a
                  key={href}
                  href={href}
                  onClick={() => setOpen(false)}
                  initial={{ y: 70, opacity: 0 }}
                  animate={{ y: 0, opacity: 1 }}
                  transition={{ delay: 0.12 + index * 0.07, duration: 0.58, ease: EASE }}
                >
                  <span>0{index + 1}</span>
                  {label}
                  <ArrowDownRight />
                </motion.a>
              ))}
            </nav>

            <div className="menu-overlay__footer">
              <span>Brasil</span>
              <a href={WHATSAPP_URL} target="_blank" rel="noreferrer">
                WhatsApp ↗
              </a>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}

function Hero() {
  const sectionRef = useRef<HTMLElement>(null);
  const reduceMotion = useReducedMotion();
  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ["start start", "end start"],
  });
  const imageY = useTransform(scrollYProgress, [0, 1], ["0%", reduceMotion ? "0%" : "20%"]);
  const imageScale = useTransform(scrollYProgress, [0, 1], [1.06, reduceMotion ? 1.06 : 1.16]);
  const textY = useTransform(scrollYProgress, [0, 1], [0, reduceMotion ? 0 : -90]);
  const textOpacity = useTransform(scrollYProgress, [0, 0.75], [1, 0.08]);

  return (
    <section ref={sectionRef} id="inicio" className="hero">
      <motion.div className="hero__media" style={{ y: imageY, scale: imageScale }}>
        <img src={SERVICES[0].bgImage} alt="" aria-hidden="true" />
        <div className="hero__scrim" />
      </motion.div>

      <div className="hero__grid" aria-hidden="true" />

      <motion.div className="hero__content" style={{ y: textY, opacity: textOpacity }}>
        <motion.div
          className="hero__eyebrow"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 1.03, duration: 0.62, ease: EASE }}
        >
          <span>Creative agency</span>
          <span>Brand · Digital · Experience</span>
        </motion.div>

        <div className="hero__title-wrap">
          <motion.h1
            initial={{ y: "110%" }}
            animate={{ y: 0 }}
            transition={{ delay: 1.02, duration: 0.9, ease: EASE }}
          >
            GLASS
          </motion.h1>
        </div>
        <div className="hero__title-wrap hero__title-wrap--right">
          <motion.h1
            initial={{ y: "110%" }}
            animate={{ y: 0 }}
            transition={{ delay: 1.1, duration: 0.9, ease: EASE }}
          >
            MAIND
          </motion.h1>
        </div>

        <motion.div
          className="hero__bottom"
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 1.3, duration: 0.65, ease: EASE }}
        >
          <p>
            Criamos identidades e experiências digitais para marcas que querem ser reconhecidas antes mesmo de serem explicadas.
          </p>
          <a href="#sobre" className="scroll-link">
            explore <ArrowDownRight size={18} />
          </a>
        </motion.div>
      </motion.div>

      <motion.div
        className="hero__orbit"
        initial={{ opacity: 0, scale: 0.72, rotate: -18 }}
        animate={{ opacity: 0.85, scale: 1, rotate: 0 }}
        transition={{ delay: 1.35, duration: 1.2, ease: EASE }}
      >
        <BrandSymbol />
      </motion.div>
    </section>
  );
}

function Manifesto() {
  const ref = useRef<HTMLElement>(null);
  const reduceMotion = useReducedMotion();
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start end", "end start"] });
  const xLeft = useTransform(scrollYProgress, [0, 1], [reduceMotion ? 0 : -90, reduceMotion ? 0 : 80]);
  const xRight = useTransform(scrollYProgress, [0, 1], [reduceMotion ? 0 : 100, reduceMotion ? 0 : -70]);

  return (
    <section ref={ref} id="sobre" className="manifesto section-light">
      <div className="section-kicker">
        <span>01</span>
        <span>Sobre a Glass Maind</span>
      </div>
      <div className="manifesto__headline">
        <motion.div style={{ x: xLeft }}>ESTRATÉGIA</motion.div>
        <motion.div style={{ x: xRight }} className="outline-word">
          COM FORMA
        </motion.div>
      </div>
      <div className="manifesto__copy-grid">
        <div className="manifesto__mark"><BrandSymbol /></div>
        <motion.p
          initial={{ opacity: 0, y: 40 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.3 }}
          transition={{ duration: 0.8, ease: EASE }}
        >
          Design não é decoração. É percepção, posicionamento e direção. A Glass Maind conecta estratégia, identidade e experiência para construir marcas com presença real.
        </motion.p>
      </div>
    </section>
  );
}

function ServicesStory() {
  const sectionRef = useRef<HTMLElement>(null);
  const reduceMotion = useReducedMotion();
  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ["start start", "end end"],
  });
  const smoothProgress = useSpring(scrollYProgress, { stiffness: 90, damping: 24, mass: 0.35 });
  const x = useTransform(
    smoothProgress,
    [0, 1],
    ["0vw", reduceMotion ? "0vw" : `-${Math.max(0, SERVICES.length - 1) * 65}vw`]
  );

  return (
    <section
      ref={sectionRef}
      id="servicos"
      className="services-story"
      style={{ "--service-count": SERVICES.length } as CSSProperties}
    >
      <div className="services-story__sticky">
        <div className="services-story__head">
          <div className="section-kicker section-kicker--dark">
            <span>02</span>
            <span>O que fazemos</span>
          </div>
          <p>Uma estrutura criativa completa para transformar posicionamento em imagem, interface e presença.</p>
        </div>

        <motion.div className="services-track" style={{ x }}>
          {SERVICES.map((service, index) => (
            <article className="service-panel" key={service.title}>
              <div className="service-panel__media">
                <img src={service.bgImage} alt="" loading={index > 1 ? "lazy" : "eager"} />
                <div className="service-panel__veil" />
              </div>
              <div className="service-panel__index">0{index + 1}</div>
              <div className="service-panel__content">
                <span>{service.thumbSub}</span>
                <h2>{service.title}</h2>
                <p>{service.description}</p>
                <a href={service.whatsappUrl} target="_blank" rel="noreferrer">
                  conversar sobre este serviço <ArrowUpRight size={16} />
                </a>
              </div>
            </article>
          ))}
        </motion.div>

        <div className="services-story__progress" aria-hidden="true">
          <motion.div style={{ scaleX: smoothProgress }} />
        </div>
      </div>
    </section>
  );
}

function Method() {
  const steps = [
    ["01", "Entender", "Contexto, objetivo, público e o espaço que a marca precisa ocupar."],
    ["02", "Direcionar", "Transformamos diagnóstico em conceito, linguagem e sistema visual."],
    ["03", "Construir", "Design, interfaces e peças ganham forma com consistência e intenção."],
    ["04", "Ativar", "A identidade encontra o mundo em pontos de contato digitais e físicos."],
  ];

  return (
    <section id="metodo" className="method section-light">
      <div className="section-kicker">
        <span>03</span>
        <span>Nosso método</span>
      </div>
      <div className="method__title">
        <motion.h2
          initial={{ y: 70, opacity: 0 }}
          whileInView={{ y: 0, opacity: 1 }}
          viewport={{ once: true, amount: 0.5 }}
          transition={{ duration: 0.75, ease: EASE }}
        >
          CLAREZA ANTES
          <br />
          DO IMPACTO.
        </motion.h2>
      </div>

      <div className="method__list">
        {steps.map(([number, title, copy], index) => (
          <motion.article
            key={number}
            initial={{ opacity: 0, y: 34 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.35 }}
            transition={{ delay: index * 0.06, duration: 0.62, ease: EASE }}
          >
            <span>{number}</span>
            <h3>{title}</h3>
            <p>{copy}</p>
            <ArrowDownRight />
          </motion.article>
        ))}
      </div>
    </section>
  );
}

function Statement() {
  const ref = useRef<HTMLElement>(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start end", "end start"] });
  const rotate = useTransform(scrollYProgress, [0, 1], [-12, 18]);
  const y = useTransform(scrollYProgress, [0, 1], [80, -80]);

  return (
    <section ref={ref} className="statement">
      <motion.div className="statement__symbol" style={{ rotate, y }}>
        <BrandSymbol />
      </motion.div>
      <p>Marcas memoráveis não pedem atenção.</p>
      <h2>ELAS CRIAM<br />GRAVIDADE.</h2>
      <div className="statement__meta">
        <span>Branding</span><span>Digital</span><span>Experience</span><span>Strategy</span>
      </div>
    </section>
  );
}

function Contact() {
  return (
    <section id="contato" className="contact section-light">
      <div className="section-kicker">
        <span>04</span>
        <span>Próximo projeto</span>
      </div>
      <div className="contact__main">
        <p>Se a sua marca mudou, cresceu ou precisa finalmente parecer do tamanho que é, a conversa começa aqui.</p>
        <a href={WHATSAPP_URL} target="_blank" rel="noreferrer" className="contact__cta">
          <span>VAMOS CRIAR</span>
          <ArrowUpRight />
        </a>
      </div>
      <footer className="footer">
        <div className="footer__brand">
          <BrandSymbol />
          <BrandName />
        </div>
        <div className="footer__info">
          <span>Brasil</span>
          <a href={WHATSAPP_URL} target="_blank" rel="noreferrer">WhatsApp ↗</a>
          <span>© {new Date().getFullYear()} Glass Maind</span>
        </div>
      </footer>
    </section>
  );
}

const Index = () => {
  return (
    <main className="gm-site">
      <Intro />
      <Header />
      <Hero />
      <Manifesto />
      <ServicesStory />
      <Method />
      <Statement />
      <Contact />
    </main>
  );
};

export default Index;
