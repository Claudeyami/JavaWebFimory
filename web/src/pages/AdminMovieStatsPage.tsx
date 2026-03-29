import React, { useEffect, useMemo, useState } from 'react';
import { useAuth } from '../hooks/useAuth';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { buildMediaUrl, API_BASE } from '../lib/config';
import { Eye, Heart, MessageCircle, Star, Trophy, Filter, Film, BookOpen } from 'lucide-react';

type ContentType = 'movie' | 'series';

type Row = {
  id: number;
  title: string;
  slug: string;
  posterUrl?: string;
  totalViews: number;
  totalLikes: number;
  totalComments: number;
  totalRatings: number;
};

type DetailPayload = {
  metric: string;
  title: string;
  items: Array<Record<string, unknown>>;
};

type SortMode = 'hot' | 'views' | 'comments' | 'likes' | 'ratings';

const metricLabels: Record<string, string> = {
  views: 'Lượt xem',
  likes: 'Lượt thích',
  comments: 'Bình luận',
  ratings: 'Đánh giá',
};

const iconMap: Record<string, JSX.Element> = {
  views: <Eye className="w-4 h-4" />,
  likes: <Heart className="w-4 h-4" />,
  comments: <MessageCircle className="w-4 h-4" />,
  ratings: <Star className="w-4 h-4" />,
};

const score = (r: Row) => r.totalViews * 1 + r.totalComments * 3 + r.totalLikes * 2 + r.totalRatings * 2;

const toNumber = (value: unknown) => {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? n : 0;
};

const normalizeRows = (payload: unknown): Row[] => {
  if (!Array.isArray(payload)) return [];
  return payload
    .map((raw) => {
      const item = raw as Record<string, unknown>;
      const id = toNumber(item.MovieID ?? item.SeriesID);
      const title = String(item.Title ?? '');
      const slug = String(item.Slug ?? '');
      if (!id || !title || !slug) return null;
      return {
        id,
        title,
        slug,
        posterUrl: (item.PosterURL as string | undefined) || (item.CoverURL as string | undefined),
        totalViews: toNumber(item.totalViews),
        totalLikes: toNumber(item.totalLikes),
        totalComments: toNumber(item.totalComments),
        totalRatings: toNumber(item.totalRatings),
      } as Row;
    })
    .filter((item): item is Row => item !== null);
};

const AdminMovieStatsPage: React.FC = () => {
  const { user } = useAuth();
  const email = useMemo(() => (user?.email as string | undefined) || '', [user]);

  const [contentType, setContentType] = useState<ContentType>('movie');
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<DetailPayload | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [sortBy, setSortBy] = useState<SortMode>('hot');
  const [onlyWithComments, setOnlyWithComments] = useState(false);

  const load = async () => {
    if (!email) return;
    setLoading(true);
    setDetail(null);
    try {
      const endpoint = contentType === 'movie'
        ? '/admin/stats/movies/engagement'
        : '/admin/stats/series/engagement';
      const url = new URL(`${API_BASE}${endpoint}`, window.location.origin);
      url.searchParams.set('limit', '200');
      const res = await fetch(url.toString(), { headers: { 'x-user-email': email } });
      if (!res.ok) throw new Error(`Failed to load ${contentType} engagement stats`);
      const payload = await res.json();
      setRows(normalizeRows(payload?.data ?? payload));
    } catch (error) {
      console.error(`Failed to load admin ${contentType} stats:`, error);
      setRows([]);
    } finally {
      setLoading(false);
    }
  };

  const openDetail = async (id: number, metric: 'views' | 'likes' | 'comments' | 'ratings') => {
    if (!email) return;
    setDetailLoading(true);
    try {
      const endpoint = contentType === 'movie'
        ? `/admin/stats/movies/${id}/details`
        : `/admin/stats/series/${id}/details`;
      const url = new URL(`${API_BASE}${endpoint}`, window.location.origin);
      url.searchParams.set('metric', metric);
      url.searchParams.set('limit', '30');
      const res = await fetch(url.toString(), { headers: { 'x-user-email': email } });
      if (!res.ok) throw new Error('Failed to load metric details');
      const payload = await res.json();
      const info = (payload?.data ?? payload) as Record<string, unknown>;
      setDetail({
        metric: String(info.metric ?? metric),
        title: String(info.title ?? ''),
        items: Array.isArray(info.items) ? (info.items as Array<Record<string, unknown>>) : [],
      });
    } catch (error) {
      console.error('Failed to load metric details:', error);
      setDetail(null);
    } finally {
      setDetailLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [email, contentType]);

  const rankedRows = useMemo(() => {
    let next = [...rows];
    if (onlyWithComments) {
      next = next.filter((r) => r.totalComments > 0);
    }

    next.sort((a, b) => {
      if (sortBy === 'views') return b.totalViews - a.totalViews || b.totalComments - a.totalComments;
      if (sortBy === 'comments') return b.totalComments - a.totalComments || b.totalViews - a.totalViews;
      if (sortBy === 'likes') return b.totalLikes - a.totalLikes || b.totalViews - a.totalViews;
      if (sortBy === 'ratings') return b.totalRatings - a.totalRatings || b.totalViews - a.totalViews;
      return score(b) - score(a) || b.totalViews - a.totalViews;
    });

    return next;
  }, [rows, sortBy, onlyWithComments]);

  const titleLabel = contentType === 'movie' ? 'Xếp hạng phim' : 'Xếp hạng truyện';

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10 space-y-6">
      <div className="rounded-2xl p-4 md:p-6 bg-gradient-to-b from-white to-zinc-100 dark:from-zinc-900 dark:to-zinc-950 border border-zinc-200 dark:border-zinc-800">
        <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
          <div>
            <h1 className="text-2xl md:text-3xl font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
              <Trophy className="w-7 h-7 text-amber-500" />
              {titleLabel}
            </h1>
            <p className="text-zinc-600 dark:text-zinc-300 mt-1">Xếp hạng theo độ hot: view + comment + like + rating.</p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <div className="flex items-center rounded-lg border border-zinc-300 dark:border-zinc-700 overflow-hidden">
              <button
                className={`px-3 py-2 text-sm flex items-center gap-1 ${contentType === 'movie' ? 'bg-blue-600 text-white' : 'bg-white dark:bg-zinc-800 text-zinc-700 dark:text-zinc-200'}`}
                onClick={() => setContentType('movie')}
              >
                <Film className="w-4 h-4" />
                Phim
              </button>
              <button
                className={`px-3 py-2 text-sm flex items-center gap-1 ${contentType === 'series' ? 'bg-blue-600 text-white' : 'bg-white dark:bg-zinc-800 text-zinc-700 dark:text-zinc-200'}`}
                onClick={() => setContentType('series')}
              >
                <BookOpen className="w-4 h-4" />
                Truyện
              </button>
            </div>

            <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-white dark:bg-zinc-800 border border-zinc-300 dark:border-zinc-700">
              <Filter className="w-4 h-4 text-zinc-500 dark:text-zinc-300" />
              <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value as SortMode)}
                className="min-w-[180px] rounded-md border border-zinc-300 dark:border-zinc-600 px-2 py-1 bg-white text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100 outline-none"
              >
                <option className="bg-white text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100" value="hot">Hot tổng hợp</option>
                <option className="bg-white text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100" value="views">Nhiều view nhất</option>
                <option className="bg-white text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100" value="comments">Nhiều bình luận nhất</option>
                <option className="bg-white text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100" value="likes">Nhiều like nhất</option>
                <option className="bg-white text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100" value="ratings">Nhiều đánh giá nhất</option>
              </select>
            </div>

            <label className="flex items-center gap-2 text-sm text-zinc-700 dark:text-zinc-200 px-3 py-2 rounded-lg bg-white dark:bg-zinc-800 border border-zinc-300 dark:border-zinc-700">
              <input type="checkbox" checked={onlyWithComments} onChange={(e) => setOnlyWithComments(e.target.checked)} />
              Chỉ nội dung có bình luận
            </label>

            <Button onClick={load} disabled={loading}>{loading ? 'Đang tải...' : 'Làm mới'}</Button>
          </div>
        </div>
      </div>

      {loading ? (
        <div className="text-center py-12 text-zinc-600 dark:text-zinc-300">Đang tải dữ liệu...</div>
      ) : rankedRows.length === 0 ? (
        <Card>
          <div className="p-12 text-center text-zinc-600 dark:text-zinc-300">Không có dữ liệu</div>
        </Card>
      ) : (
        <Card className="border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900/90">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[980px]">
              <thead>
                <tr className="border-b border-zinc-200 dark:border-zinc-700 text-left text-sm text-zinc-600 dark:text-zinc-300">
                  <th className="py-3 px-3">#</th>
                  <th className="py-3 px-3">{contentType === 'movie' ? 'Phim' : 'Truyện'}</th>
                  <th className="py-3 px-3">View</th>
                  <th className="py-3 px-3">Like</th>
                  <th className="py-3 px-3">Comment</th>
                  <th className="py-3 px-3">Rating</th>
                  <th className="py-3 px-3">Hot Score</th>
                </tr>
              </thead>
              <tbody>
                {rankedRows.map((row, index) => (
                  <tr key={`${contentType}-${row.id}`} className="border-b border-zinc-100 dark:border-zinc-800 align-top hover:bg-zinc-50 dark:hover:bg-zinc-800/40 transition-colors">
                    <td className="py-3 px-3 font-bold text-amber-500">{index + 1}</td>
                    <td className="py-3 px-3">
                      <div className="flex items-center gap-3 min-w-[300px]">
                        <div className="w-14 h-20 rounded-md overflow-hidden bg-zinc-200 dark:bg-zinc-700 shrink-0 border border-zinc-300 dark:border-zinc-600">
                          {row.posterUrl ? (
                            <img src={buildMediaUrl(row.posterUrl) || undefined} alt={row.title} className="w-full h-full object-cover" />
                          ) : null}
                        </div>
                        <div>
                          <div className="font-semibold text-zinc-900 dark:text-zinc-100 leading-snug">{row.title}</div>
                          <a href={contentType === 'movie' ? `/watch/${row.slug}` : `/stories/${row.slug}`} className="text-xs text-blue-600 dark:text-blue-400 hover:underline">
                            {contentType === 'movie' ? 'Xem trang phim' : 'Xem trang truyện'}
                          </a>
                        </div>
                      </div>
                    </td>

                    {(['views', 'likes', 'comments', 'ratings'] as const).map((metric) => {
                      const value = metric === 'views'
                        ? row.totalViews
                        : metric === 'likes'
                          ? row.totalLikes
                          : metric === 'comments'
                            ? row.totalComments
                            : row.totalRatings;

                      return (
                        <td key={metric} className="py-3 px-3">
                          <div className="text-base font-semibold text-zinc-900 dark:text-zinc-100 mb-2">{value}</div>
                          <Button size="sm" variant="secondary" onClick={() => openDetail(row.id, metric)}>
                            {metricLabels[metric]}
                          </Button>
                        </td>
                      );
                    })}

                    <td className="py-3 px-3 text-base font-bold text-cyan-600 dark:text-cyan-300">{score(row)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      <Card className="border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900/90">
        <div className="p-4 md:p-5">
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
              {detail ? iconMap[detail.metric] ?? null : null}
              {detail ? `${detail.title} - ${metricLabels[detail.metric] || detail.metric}` : 'Chi tiết'}
            </h2>
            {detailLoading ? <span className="text-sm text-zinc-500 dark:text-zinc-300">Đang tải...</span> : null}
          </div>

          {!detail ? (
            <div className="text-sm text-zinc-600 dark:text-zinc-300">Bấm nút ở cột tương ứng để mở chi tiết.</div>
          ) : detail.items.length === 0 ? (
            <div className="text-sm text-zinc-600 dark:text-zinc-300">Không có dữ liệu chi tiết.</div>
          ) : (
            <div className="max-h-[360px] overflow-auto border border-zinc-200 dark:border-zinc-700 rounded-md">
              <table className="w-full text-sm">
                <thead className="bg-zinc-100 dark:bg-zinc-800 sticky top-0">
                  <tr>
                    {Object.keys(detail.items[0]).map((key) => (
                      <th key={key} className="py-2 px-3 text-left whitespace-nowrap text-zinc-700 dark:text-zinc-200">{key}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {detail.items.map((item, idx) => (
                    <tr key={idx} className="border-t border-zinc-100 dark:border-zinc-800">
                      {Object.keys(detail.items[0]).map((key) => (
                        <td key={key} className="py-2 px-3 whitespace-nowrap text-zinc-700 dark:text-zinc-300">{String((item as Record<string, unknown>)[key] ?? '')}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
};

export default AdminMovieStatsPage;

