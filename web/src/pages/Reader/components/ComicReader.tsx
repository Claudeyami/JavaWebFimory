import React, { useEffect, useMemo, useRef, useState } from "react";
import { useInView } from "react-intersection-observer";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { AnimatePresence, motion } from "framer-motion";
import type { ReaderChapterImage } from "../../../lib/api";
import { buildMediaUrl } from "../../../lib/config";
import { Button } from "../../../components/ui/Button";
import type { ActiveReaderMode } from "../hooks/useReaderMode";

const ObservedImage: React.FC<{
  image: ReaderChapterImage;
  pageNumber: number;
  activePage: number;
  onEnter: (page: number) => void;
  disabled?: boolean;
}> = ({ image, pageNumber, activePage, onEnter, disabled = false }) => {
  const { ref, inView } = useInView({ threshold: 0.7 });
  const aspectRatio =
    image.Width && image.Height && image.Width > 0 && image.Height > 0
      ? `${image.Width} / ${image.Height}`
      : undefined;

  useEffect(() => {
    if (!disabled && inView) {
      onEnter(pageNumber);
    }
  }, [disabled, inView, onEnter, pageNumber]);

  return (
    <div
      ref={ref}
      data-reader-page={pageNumber}
      className={`rounded-2xl p-2 transition ${activePage === pageNumber ? "bg-blue-50 dark:bg-blue-950/20" : ""}`}
    >
      <img
        data-reader-image={pageNumber}
        src={buildMediaUrl(image.ImageURL) || undefined}
        alt={`Trang ${pageNumber}`}
        className="mx-auto max-w-full rounded-xl shadow-sm"
        loading={Math.abs(activePage - pageNumber) <= 1 || pageNumber <= 2 ? "eager" : "lazy"}
        style={aspectRatio ? { aspectRatio } : undefined}
      />
    </div>
  );
};

type Props = {
  images: ReaderChapterImage[];
  mode: ActiveReaderMode;
  currentPage: number;
  onPageChange: (page: number) => void;
};

const ComicReader: React.FC<Props> = ({ images, mode, currentPage, onPageChange }) => {
  const stageRef = useRef<HTMLDivElement | null>(null);
  const scrollContainerRef = useRef<HTMLDivElement | null>(null);
  const isFirstMountRef = useRef(true);
  const previousPageRef = useRef(currentPage);
  const unlockTimerRef = useRef<number | null>(null);
  const scrollFrameRef = useRef<number | null>(null);
  const imageCount = images.length;
  const currentImage = useMemo(() => images[Math.max(0, currentPage - 1)], [currentPage, images]);
  const [direction, setDirection] = useState<1 | -1>(1);
  const [isSyncingScroll, setIsSyncingScroll] = useState(true);
  const [flipEdge, setFlipEdge] = useState<"left" | "right" | null>(null);

  useEffect(() => {
    if (currentPage === previousPageRef.current) return;
    setDirection(currentPage > previousPageRef.current ? 1 : -1);
    previousPageRef.current = currentPage;
  }, [currentPage]);

  useEffect(() => {
    if (!flipEdge) return;
    const timer = window.setTimeout(() => {
      setFlipEdge(null);
    }, 320);
    return () => window.clearTimeout(timer);
  }, [flipEdge]);

  const goToPage = (nextPage: number, nextDirection: 1 | -1) => {
    if (nextPage < 1 || nextPage > imageCount || nextPage === currentPage) return;
    setDirection(nextDirection);
    setFlipEdge(nextDirection > 0 ? "right" : "left");
    onPageChange(nextPage);
  };

  const centerFlipStage = (behavior: ScrollBehavior = "smooth") => {
    if (!stageRef.current) return;
    const rect = stageRef.current.getBoundingClientRect();
    const absoluteTop = window.scrollY + rect.top;
    const targetTop = absoluteTop - Math.max(0, (window.innerHeight - rect.height) / 2);
    window.scrollTo({
      top: Math.max(0, targetTop),
      behavior,
    });
  };

  const syncToTarget = (behavior: ScrollBehavior = "auto") => {
    const target = scrollContainerRef.current?.querySelector<HTMLElement>(`[data-reader-page="${currentPage}"]`);
    if (target) {
      target.scrollIntoView({ behavior, block: "center" });
    }
    if (unlockTimerRef.current) {
      window.clearTimeout(unlockTimerRef.current);
      unlockTimerRef.current = null;
    }
    unlockTimerRef.current = window.setTimeout(() => {
      setIsSyncingScroll(false);
    }, 150);
  };

  useEffect(() => {
    if (mode !== "scroll" || isSyncingScroll) return;

    const syncPageFromViewport = () => {
      const pages = Array.from(
        scrollContainerRef.current?.querySelectorAll<HTMLElement>("[data-reader-page]") || []
      );
      if (!pages.length) return;

      const viewportCenter = window.innerHeight / 2;
      let nextPage = currentPage;
      let bestDistance = Number.POSITIVE_INFINITY;

      for (const pageEl of pages) {
        const rect = pageEl.getBoundingClientRect();
        if (rect.bottom <= 0 || rect.top >= window.innerHeight) continue;

        const pageCenter = rect.top + rect.height / 2;
        const distance = Math.abs(pageCenter - viewportCenter);
        if (distance < bestDistance) {
          bestDistance = distance;
          nextPage = Number(pageEl.dataset.readerPage || currentPage);
        }
      }

      if (nextPage !== currentPage) {
        onPageChange(nextPage);
      }
    };

    const handleScroll = () => {
      if (scrollFrameRef.current) {
        window.cancelAnimationFrame(scrollFrameRef.current);
      }
      scrollFrameRef.current = window.requestAnimationFrame(syncPageFromViewport);
    };

    handleScroll();
    window.addEventListener("scroll", handleScroll, { passive: true });
    window.addEventListener("resize", handleScroll);

    return () => {
      window.removeEventListener("scroll", handleScroll);
      window.removeEventListener("resize", handleScroll);
      if (scrollFrameRef.current) {
        window.cancelAnimationFrame(scrollFrameRef.current);
        scrollFrameRef.current = null;
      }
    };
  }, [currentPage, isSyncingScroll, mode, onPageChange]);

  useEffect(() => {
    if (mode === "flip") {
      setIsSyncingScroll(false);
      const frame = window.requestAnimationFrame(() => {
        centerFlipStage(isFirstMountRef.current ? "auto" : "smooth");
        isFirstMountRef.current = false;
      });
      return () => window.cancelAnimationFrame(frame);
    }

    setIsSyncingScroll(true);

    const frame = window.requestAnimationFrame(() => {
      const image = scrollContainerRef.current?.querySelector<HTMLImageElement>(`img[data-reader-image="${currentPage}"]`);
      if (image && !image.complete) {
        const handleLoaded = () => {
          syncToTarget(isFirstMountRef.current ? "auto" : "smooth");
          isFirstMountRef.current = false;
        };
        image.addEventListener("load", handleLoaded, { once: true });
        unlockTimerRef.current = window.setTimeout(handleLoaded, 280);
        return;
      }
      syncToTarget(isFirstMountRef.current ? "auto" : "smooth");
      isFirstMountRef.current = false;
    });

    return () => {
      window.cancelAnimationFrame(frame);
      if (unlockTimerRef.current) {
        window.clearTimeout(unlockTimerRef.current);
        unlockTimerRef.current = null;
      }
    };
  }, [currentPage, mode]);

  if (!images.length) {
    return (
      <div className="rounded-3xl border border-dashed border-gray-300 bg-white p-10 text-center text-gray-500 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-400">
        Chuong nay chua co anh.
      </div>
    );
  }

  if (mode === "scroll") {
    return (
      <div className="rounded-[2rem] border border-gray-200 bg-white/90 p-3 shadow-sm dark:border-gray-800 dark:bg-slate-900/90 sm:p-5">
        <div ref={scrollContainerRef} className="mx-auto flex max-w-5xl flex-col gap-4">
          {images.map((image, index) => (
            <ObservedImage
              key={image.ImageID}
              image={image}
              pageNumber={index + 1}
              activePage={currentPage}
              onEnter={onPageChange}
              disabled={isSyncingScroll}
            />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl">
      <div className="mb-4 flex items-center justify-between">
        <Button size="sm" variant="outline" disabled={currentPage <= 1} onClick={() => goToPage(currentPage - 1, -1)}>
          <ChevronLeft className="mr-1 h-4 w-4" />
          Trang truoc
        </Button>
        <div className="text-sm text-gray-500 dark:text-gray-400">
          Trang {currentPage}/{imageCount}
        </div>
        <Button size="sm" variant="outline" disabled={currentPage >= imageCount} onClick={() => goToPage(currentPage + 1, 1)}>
          Trang sau
          <ChevronRight className="ml-1 h-4 w-4" />
        </Button>
      </div>

      <div ref={stageRef} className="rounded-[2rem] border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-800 dark:bg-gray-900">
        <div
          className="relative flex min-h-[calc(100vh-18rem)] items-center justify-center overflow-hidden rounded-2xl bg-[#0b1220]"
          style={{ perspective: "2000px" }}
        >
          <button
            type="button"
            aria-label="Trang truoc"
            disabled={currentPage <= 1}
            onClick={() => goToPage(currentPage - 1, -1)}
            className="group absolute inset-y-0 left-0 z-10 w-1/5 min-w-[72px] disabled:pointer-events-none"
          >
            <div className="absolute inset-y-0 left-0 w-full bg-gradient-to-r from-white/10 via-white/0 to-transparent opacity-0 transition group-hover:opacity-100" />
            <div className="absolute left-4 top-1/2 -translate-y-1/2 rounded-full bg-black/30 p-2 text-white/80 opacity-0 transition group-hover:opacity-100">
              <ChevronLeft className="h-5 w-5" />
            </div>
          </button>

          <button
            type="button"
            aria-label="Trang sau"
            disabled={currentPage >= imageCount}
            onClick={() => goToPage(currentPage + 1, 1)}
            className="group absolute inset-y-0 right-0 z-10 w-1/5 min-w-[72px] disabled:pointer-events-none"
          >
            <div className="absolute inset-y-0 right-0 w-full bg-gradient-to-l from-white/10 via-white/0 to-transparent opacity-0 transition group-hover:opacity-100" />
            <div className="absolute right-4 top-1/2 -translate-y-1/2 rounded-full bg-black/30 p-2 text-white/80 opacity-0 transition group-hover:opacity-100">
              <ChevronRight className="h-5 w-5" />
            </div>
          </button>

          <div className="pointer-events-none absolute inset-0 rounded-2xl ring-1 ring-inset ring-white/5" />
          <AnimatePresence>
            {flipEdge && (
              <motion.div
                key={flipEdge}
                initial={{ opacity: 0, x: flipEdge === "right" ? 50 : -50 }}
                animate={{ opacity: 0.95, x: 0 }}
                exit={{ opacity: 0, x: flipEdge === "right" ? -24 : 24 }}
                transition={{ duration: 0.28, ease: "easeOut" }}
                className={`pointer-events-none absolute inset-y-8 z-10 w-28 rounded-full blur-2xl ${
                  flipEdge === "right"
                    ? "right-10 bg-gradient-to-l from-white/35 via-white/12 to-transparent"
                    : "left-10 bg-gradient-to-r from-white/35 via-white/12 to-transparent"
                }`}
              />
            )}
          </AnimatePresence>

          <div className="flex h-full w-full items-center justify-center px-8 py-8 sm:px-14">
            <AnimatePresence mode="wait" custom={direction}>
              {currentImage && (
                <motion.div
                  key={currentImage.ImageID}
                  custom={direction}
                  initial={{
                    opacity: 0.2,
                    rotateY: direction > 0 ? -58 : 58,
                    rotateZ: direction > 0 ? 0.9 : -0.9,
                    x: direction > 0 ? 64 : -64,
                    scale: 0.985,
                  }}
                  animate={{
                    opacity: 1,
                    rotateY: 0,
                    rotateZ: 0,
                    x: 0,
                    scale: 1,
                  }}
                  exit={{
                    opacity: 0.12,
                    rotateY: direction > 0 ? 62 : -62,
                    rotateZ: direction > 0 ? -0.8 : 0.8,
                    x: direction > 0 ? -86 : 86,
                    scale: 0.99,
                  }}
                  transition={{ duration: 0.34, ease: [0.22, 1, 0.36, 1] }}
                  style={{
                    transformOrigin: direction > 0 ? "left center" : "right center",
                    transformStyle: "preserve-3d",
                  }}
                  className="relative"
                >
                  <motion.div
                    aria-hidden="true"
                    initial={{ opacity: 0.38 }}
                    animate={{ opacity: 0 }}
                    exit={{ opacity: 0.46 }}
                    transition={{ duration: 0.24, ease: "easeOut" }}
                    className={`pointer-events-none absolute inset-y-0 z-10 w-10 ${
                      direction > 0
                        ? "left-0 bg-gradient-to-r from-black/45 via-white/12 to-transparent"
                        : "right-0 bg-gradient-to-l from-black/45 via-white/12 to-transparent"
                    }`}
                  />
                  <motion.img
                    src={buildMediaUrl(currentImage.ImageURL) || undefined}
                    alt={`Trang ${currentPage}`}
                    className="mx-auto block max-h-[calc(100vh-21rem)] max-w-full rounded-2xl object-contain object-center shadow-[0_18px_60px_rgba(0,0,0,0.35)]"
                  />
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ComicReader;
