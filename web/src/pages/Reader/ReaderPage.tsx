import React, { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ArrowUp, ChevronLeft, ChevronRight, Home } from "lucide-react";
import ReaderHeader from "./components/ReaderHeader";
import ComicReader from "./components/ComicReader";
import TextReader from "./components/TextReader";
import PdfReader from "./components/PdfReader";
import BridgePage from "./components/BridgePage";
import { useAuth } from "../../hooks/useAuth";
import { fetchReaderChapterDetail, fetchReaderManifest, type ReaderChapterDetail, type ReaderManifestResponse } from "../../lib/api";
import { useReaderMode } from "./hooks/useReaderMode";
import { useReaderProgress } from "./hooks/useReaderProgress";
import { Button } from "../../components/ui/Button";

const isPdfContent = (content?: string | null) => {
  if (!content) return false;
  const normalized = content.toLowerCase();
  return normalized.startsWith("/storage/") && normalized.includes(".pdf");
};

const ReaderPage: React.FC = () => {
  const { slug } = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { user } = useAuth();
  const [manifest, setManifest] = useState<ReaderManifestResponse | null>(null);
  const [chapter, setChapter] = useState<ReaderChapterDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [showReaderQuickNav, setShowReaderQuickNav] = useState(false);

  const chapterNumber = Math.max(1, Number(searchParams.get("chapter") || 1));
  const email = (user?.email as string | undefined) || undefined;

  useEffect(() => {
    if (!slug) return;
    let cancelled = false;
    (async () => {
      try {
        const data = await fetchReaderManifest(slug);
        if (!cancelled) setManifest(data);
      } catch {
        if (!cancelled) setManifest(null);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [slug]);

  useEffect(() => {
    if (!slug) return;
    let cancelled = false;
    setLoading(true);
    (async () => {
      try {
        const data = await fetchReaderChapterDetail(slug, chapterNumber);
        if (!cancelled) setChapter(data);
      } catch {
        if (!cancelled) setChapter(null);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [chapterNumber, slug]);

  const images = chapter?.Images || [];
  const { selectedMode, activeMode, setMode } = useReaderMode({
    email,
    seriesId: chapter?.SeriesID,
    images,
  });

  const maxPage = useMemo(() => {
    if (chapter?.StoryType === "Comic") {
      return Math.max(1, images.length || Number(chapter?.ImageCount || 1));
    }
    return 1;
  }, [chapter?.ImageCount, chapter?.StoryType, images.length]);

  const { currentPage, setCurrentPage, ready } = useReaderProgress({
    email,
    seriesId: chapter?.SeriesID,
    chapterId: chapter?.ChapterID,
    maxPage,
  });

  const openChapter = (nextChapterNumber: number) => {
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("chapter", String(nextChapterNumber));
    nextParams.set("page", "1");
    setSearchParams(nextParams);
  };

  useEffect(() => {
    const handleScrollVisibility = () => {
      setShowReaderQuickNav(window.scrollY > 500);
    };

    handleScrollVisibility();
    window.addEventListener("scroll", handleScrollVisibility);
    return () => {
      window.removeEventListener("scroll", handleScrollVisibility);
    };
  }, []);

  const body = () => {
    if (!chapter) {
      return (
        <div className="rounded-3xl border border-dashed border-gray-300 bg-white p-10 text-center text-gray-500 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-400">
          Không thể tải chương này.
        </div>
      );
    }

    if (chapter.StoryType === "Comic") {
      return (
        <ComicReader
          key={`${chapter.ChapterID}-${activeMode}`}
          images={images}
          mode={activeMode}
          currentPage={currentPage}
          onPageChange={setCurrentPage}
        />
      );
    }

    if (isPdfContent(chapter.Content)) {
      return <PdfReader title={`${chapter.SeriesTitle} - Chapter ${chapter.ChapterNumber}`} content={chapter.Content} />;
    }

    return <TextReader content={chapter.Content} />;
  };

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-[#0f172a]">
      <ReaderHeader
        title={chapter?.SeriesTitle || manifest?.story?.Title || "Reader"}
        chapterNumber={chapter?.ChapterNumber || chapterNumber}
        chapterTitle={chapter?.Title}
        chapters={manifest?.chapters || []}
        selectedMode={selectedMode}
        activeMode={activeMode}
        isComic={chapter?.StoryType === "Comic"}
        onSelectChapter={openChapter}
        onSelectMode={setMode}
      />

      <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6">
        <div className="mb-5 rounded-3xl border border-gray-200 bg-white/90 p-4 shadow-sm dark:border-gray-800 dark:bg-slate-900/90">
          <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-gray-600 dark:text-gray-300">
            <button type="button" className="font-medium hover:text-blue-600" onClick={() => navigate(`/stories/${slug}`)}>
              Ve trang truyen
            </button>
            <div>
              {chapter?.StoryType === "Comic" ? `Trang ${currentPage}/${maxPage}` : "Che do doc toi uu"}
            </div>
          </div>
        </div>

        {loading || !ready ? <BridgePage label="Dang mo reader..." /> : body()}
      </div>

      {showReaderQuickNav && (
        <div className="fixed bottom-4 left-1/2 z-50 -translate-x-1/2">
          <div className="flex items-center gap-2 rounded-full border border-gray-200 bg-white/95 px-2 py-2 shadow-lg backdrop-blur dark:border-gray-700 dark:bg-gray-900/95">
            <Button
              size="sm"
              variant="secondary"
              onClick={() => navigate("/")}
              className="rounded-full px-3"
              title="Ve trang chu"
            >
              <Home className="h-4 w-4" />
            </Button>
            {chapter?.PrevChapterNumber && (
              <Button
                size="sm"
                variant="secondary"
                onClick={() => openChapter(chapter.PrevChapterNumber!)}
                className="rounded-full px-3"
                title="Prev chapter"
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
            )}
            {chapter?.NextChapterNumber && (
              <Button
                size="sm"
                variant="secondary"
                onClick={() => openChapter(chapter.NextChapterNumber!)}
                className="rounded-full px-3"
                title="Next chapter"
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            )}
            <Button
              size="sm"
              variant="primary"
              onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}
              className="rounded-full px-3"
              title="Len dau trang"
            >
              <ArrowUp className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
};

export default ReaderPage;
