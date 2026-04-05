import React from "react";
import { BookOpen, ChevronLeft, ChevronRight, ScrollText } from "lucide-react";
import { Button } from "../../../components/ui/Button";
import type { ReaderManifestChapter } from "../../../lib/api";
import type { ActiveReaderMode, ReaderMode } from "../hooks/useReaderMode";

type Props = {
  title: string;
  chapterNumber: number;
  chapterTitle?: string;
  chapters: ReaderManifestChapter[];
  selectedMode: ReaderMode;
  activeMode: ActiveReaderMode;
  isComic: boolean;
  onSelectChapter: (chapterNumber: number) => void;
  onSelectMode: (mode: ReaderMode) => void;
};

const ReaderHeader: React.FC<Props> = ({
  title,
  chapterNumber,
  chapterTitle,
  chapters,
  selectedMode,
  activeMode,
  isComic,
  onSelectChapter,
  onSelectMode,
}) => {
  const currentIndex = chapters.findIndex((chapter) => chapter.ChapterNumber === chapterNumber);
  const prev = currentIndex > 0 ? chapters[currentIndex - 1] : null;
  const next = currentIndex >= 0 && currentIndex < chapters.length - 1 ? chapters[currentIndex + 1] : null;

  return (
    <div className="sticky top-0 z-30 bg-white/95 backdrop-blur dark:bg-gray-950/95">
      <div className="border-b border-gray-200 dark:border-gray-800">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-3 px-4 py-3">
          <div className="min-w-0 flex-1">
            <div className="truncate text-sm text-gray-500 dark:text-gray-400">{title}</div>
            <div className="truncate text-lg font-semibold text-gray-900 dark:text-white">
              Chuong {chapterNumber}{chapterTitle ? ` - ${chapterTitle}` : ""}
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Button size="sm" variant="outline" disabled={!prev} onClick={() => prev && onSelectChapter(prev.ChapterNumber)}>
              <ChevronLeft className="mr-1 h-4 w-4" />
              Prev
            </Button>
            <select
              className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-800 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-200"
              value={chapterNumber}
              onChange={(e) => onSelectChapter(Number(e.target.value))}
            >
              {chapters.map((chapter) => (
                <option key={chapter.ChapterID} value={chapter.ChapterNumber}>
                  Chapter {chapter.ChapterNumber}
                </option>
              ))}
            </select>
            <Button size="sm" variant="outline" disabled={!next} onClick={() => next && onSelectChapter(next.ChapterNumber)}>
              Next
              <ChevronRight className="ml-1 h-4 w-4" />
            </Button>
          </div>
        </div>
      </div>

      {isComic && (
        <div className="border-b border-gray-200/80 dark:border-gray-800/80">
          <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-3 px-4 py-2">
            <div className="text-xs font-medium uppercase tracking-[0.16em] text-gray-500 dark:text-gray-400">
              Che do doc
            </div>
            <div className="flex items-center gap-2 rounded-full border border-gray-200 bg-gray-50 p-1 dark:border-gray-700 dark:bg-gray-900">
              {(["auto", "flip", "scroll"] as ReaderMode[]).map((mode) => {
                const active = selectedMode === mode;
                return (
                  <button
                    key={mode}
                    type="button"
                    onClick={() => onSelectMode(mode)}
                    className={`inline-flex items-center gap-1 rounded-full px-3 py-1.5 text-xs font-medium transition ${
                      active
                        ? "bg-blue-600 text-white"
                        : "text-gray-600 hover:bg-white dark:text-gray-300 dark:hover:bg-gray-800"
                    }`}
                    title={mode === "auto" ? `Auto (${activeMode})` : mode}
                  >
                    {mode === "scroll" ? <ScrollText className="h-3.5 w-3.5" /> : <BookOpen className="h-3.5 w-3.5" />}
                    {mode === "auto" ? `Auto (${activeMode})` : mode}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ReaderHeader;
