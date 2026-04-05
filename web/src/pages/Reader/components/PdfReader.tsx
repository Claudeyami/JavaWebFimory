import React from "react";
import { buildMediaUrl } from "../../../lib/config";

const PdfReader: React.FC<{ content?: string | null; title: string }> = ({ content, title }) => {
  const url = buildMediaUrl(content || "");

  return (
    <div className="mx-auto max-w-5xl overflow-hidden rounded-3xl border border-gray-200 bg-white shadow-sm dark:border-gray-800 dark:bg-gray-900">
      <div className="flex items-center justify-between border-b border-gray-200 px-4 py-3 dark:border-gray-800">
        <span className="text-sm text-gray-500 dark:text-gray-400">PDF Reader</span>
        <a href={url || undefined} target="_blank" rel="noreferrer" className="text-sm text-blue-600 hover:underline">
          Mở tab mới
        </a>
      </div>
      <iframe src={url || undefined} title={title} className="h-[80vh] w-full border-0" />
    </div>
  );
};

export default PdfReader;
