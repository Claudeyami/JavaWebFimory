import React from "react";

const BridgePage: React.FC<{ label?: string }> = ({ label = "Đang mở chương..." }) => {
  return (
    <div className="flex min-h-[40vh] items-center justify-center rounded-2xl border border-dashed border-gray-300 bg-white/80 p-10 text-center text-gray-600 dark:border-gray-700 dark:bg-gray-900/70 dark:text-gray-300">
      <div>
        <div className="mx-auto mb-4 h-10 w-10 animate-spin rounded-full border-2 border-blue-200 border-t-blue-600" />
        <p className="text-sm">{label}</p>
      </div>
    </div>
  );
};

export default BridgePage;
