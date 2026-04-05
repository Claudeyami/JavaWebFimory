import React from "react";

const TextReader: React.FC<{ content?: string | null }> = ({ content }) => {
  return (
    <div className="mx-auto max-w-4xl rounded-3xl border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-800 dark:bg-gray-900 sm:p-8">
      <div className="whitespace-pre-wrap text-base leading-8 text-gray-700 dark:text-gray-200">
        {content || "Chương này chưa có nội dung."}
      </div>
    </div>
  );
};

export default TextReader;
