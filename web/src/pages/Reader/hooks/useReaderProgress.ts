import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { fetchSeriesReaderProgress, saveSeriesReaderProgress } from "../../../lib/api";

type Params = {
  email?: string;
  seriesId?: number;
  chapterId?: number;
  maxPage: number;
};

const clampPage = (page: number, maxPage: number) => Math.max(1, Math.min(maxPage || 1, page || 1));

export function useReaderProgress({ email, seriesId, chapterId, maxPage }: Params) {
  const [searchParams, setSearchParams] = useSearchParams();
  const urlPage = Number(searchParams.get("page") || 0) || 0;
  const [currentPage, setCurrentPage] = useState<number>(urlPage > 0 ? clampPage(urlPage, maxPage) : 1);
  const [ready, setReady] = useState<boolean>(urlPage > 0 || !email || !chapterId);
  const timerRef = useRef<number | null>(null);

  useEffect(() => {
    setCurrentPage((prev) => clampPage(urlPage > 0 ? urlPage : prev, maxPage));
  }, [maxPage, urlPage]);

  useEffect(() => {
    let cancelled = false;
    if (!chapterId) {
      setReady(true);
      setCurrentPage(1);
      return;
    }

    if (urlPage > 0) {
      setCurrentPage(clampPage(urlPage, maxPage));
      setReady(true);
      return;
    }

    if (!email) {
      setCurrentPage(1);
      setReady(true);
      return;
    }

    setReady(false);
    (async () => {
      try {
        const progress = await fetchSeriesReaderProgress(email, chapterId);
        if (cancelled) return;
        const nextPage = clampPage(Number(progress.currentPosition || 1), maxPage);
        setCurrentPage(nextPage);
        if (searchParams.get("page") !== String(nextPage)) {
          const nextParams = new URLSearchParams(searchParams);
          nextParams.set("page", String(nextPage));
          setSearchParams(nextParams, { replace: true });
        }
      } catch {
        if (!cancelled) setCurrentPage(1);
      } finally {
        if (!cancelled) setReady(true);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [chapterId, email, maxPage]);

  useEffect(() => {
    if (!ready || !chapterId) return;

    if (searchParams.get("page") !== String(currentPage)) {
      const nextParams = new URLSearchParams(searchParams);
      nextParams.set("page", String(currentPage));
      setSearchParams(nextParams, { replace: true });
    }

    if (!email || !seriesId) {
      return;
    }

    if (timerRef.current) {
      window.clearTimeout(timerRef.current);
    }

    timerRef.current = window.setTimeout(() => {
      saveSeriesReaderProgress({
        email,
        seriesId,
        chapterId,
        currentPosition: currentPage,
        isCompleted: currentPage >= maxPage,
      }).catch(() => {});
    }, 1200);

    return () => {
      if (timerRef.current) {
        window.clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [chapterId, currentPage, email, maxPage, ready, seriesId, searchParams, setSearchParams]);

  return {
    currentPage,
    setCurrentPage: (page: number) => setCurrentPage(clampPage(page, maxPage)),
    ready,
  };
}
