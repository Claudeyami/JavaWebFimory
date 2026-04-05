import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { getUserPreferences, saveUserPreference, type ReaderChapterImage } from "../../../lib/api";

export type ReaderMode = "auto" | "flip" | "scroll";
export type ActiveReaderMode = Exclude<ReaderMode, "auto">;

const isReaderMode = (value: string | null): value is ReaderMode =>
  value === "auto" || value === "flip" || value === "scroll";

const getAutoMode = (images: ReaderChapterImage[]): ActiveReaderMode => {
  const ratios = images
    .map((image) => {
      const width = Number(image.Width || 0);
      const height = Number(image.Height || 0);
      return width > 0 && height > 0 ? height / width : null;
    })
    .filter((ratio): ratio is number => ratio !== null);

  if (ratios.length === 0) return "flip";

  const median = ratios.sort((a, b) => a - b)[Math.floor(ratios.length / 2)];
  return median > 1.8 ? "scroll" : "flip";
};

export function useReaderMode(params: {
  email?: string;
  seriesId?: number;
  images: ReaderChapterImage[];
}) {
  const { email, seriesId, images } = params;
  const [searchParams, setSearchParams] = useSearchParams();
  const [storedMode, setStoredMode] = useState<ReaderMode | null>(null);
  const autoMode = useMemo(() => getAutoMode(images), [images]);

  useEffect(() => {
    let cancelled = false;
    const urlMode = searchParams.get("mode");
    if (isReaderMode(urlMode)) {
      setStoredMode(urlMode);
      return;
    }

    const localKeys = seriesId
      ? [`reader:mode:series:${seriesId}`, "reader:mode:default"]
      : ["reader:mode:default"];

    for (const key of localKeys) {
      const localValue = window.localStorage.getItem(key);
      if (isReaderMode(localValue)) {
        setStoredMode(localValue);
        return;
      }
    }

    if (!email) {
      setStoredMode(null);
      return;
    }

    (async () => {
      try {
        const prefs = await getUserPreferences(email);
        if (cancelled) return;
        const seriesMode = seriesId ? prefs[`reader_mode_series_${seriesId}`] : null;
        const defaultMode = prefs["reader_mode_default"];
        const nextMode = isReaderMode(seriesMode) ? seriesMode : isReaderMode(defaultMode) ? defaultMode : null;
        setStoredMode(nextMode);
      } catch {
        if (!cancelled) setStoredMode(null);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [email, searchParams, seriesId]);

  const selectedMode = isReaderMode(searchParams.get("mode"))
    ? (searchParams.get("mode") as ReaderMode)
    : storedMode || "auto";

  const activeMode: ActiveReaderMode = selectedMode === "auto" ? autoMode : selectedMode;

  const setMode = (nextMode: ReaderMode) => {
    setStoredMode(nextMode);

    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("mode", nextMode);
    setSearchParams(nextParams, { replace: true });

    if (seriesId) {
      window.localStorage.setItem(`reader:mode:series:${seriesId}`, nextMode);
    }
    window.localStorage.setItem("reader:mode:default", nextMode);

    if (email) {
      const key = seriesId ? `reader_mode_series_${seriesId}` : "reader_mode_default";
      saveUserPreference(email, key, nextMode).catch(() => {});
    }
  };

  return {
    selectedMode,
    activeMode,
    autoMode,
    setMode,
  };
}
