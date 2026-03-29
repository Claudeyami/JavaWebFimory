import React, { useEffect, useMemo, useState } from 'react';
import { HeroSection } from '../components/home/HeroSection';
import { ContentSection } from '../components/home/ContentSection';
import type { Movie, Series } from '../types';
import { Film, BookOpen, TrendingUp, Star, Clock, Eye } from 'lucide-react';
import { buildMediaUrl } from '../lib/config';
import { useAuth } from '../hooks/useAuth';
import { getUserPreferences } from '../lib/api';

type DisplayMode = 'all' | 'movies' | 'series';

export const HomePage: React.FC = () => {
  const { user } = useAuth();
  const email = useMemo(() => (user?.email as string | undefined) || '', [user]);
  const [displayMode, setDisplayMode] = useState<DisplayMode>('all');

  const [movies, setMovies] = useState<Movie[]>([]);
  const [series, setSeries] = useState<Series[]>([]);
  const [featuredMovies, setFeaturedMovies] = useState<Movie[]>([]);
  const [trendingMovies, setTrendingMovies] = useState<Movie[]>([]);
  const [newMovies, setNewMovies] = useState<Movie[]>([]);
  const [topRatedMovies, setTopRatedMovies] = useState<Movie[]>([]);
  const [mostViewedMovies, setMostViewedMovies] = useState<Movie[]>([]);

  const mapMovie = (m: any): Movie => ({
    id: String(m.MovieID),
    title: m.Title,
    slug: m.Slug,
    description: m.Description || '',
    posterUrl: buildMediaUrl(m.PosterURL) || '',
    trailerUrl: buildMediaUrl(m.TrailerURL) || '',
    releaseYear: m.ReleaseYear,
    season: undefined,
    duration: m.Duration,
    country: m.Country || 'VN',
    director: m.Director || '',
    cast: m.Cast || '',
    status: m.Status === 'Ongoing' ? 'Ongoing' : 'Completed',
    isFree: m.IsFree ?? true,
    viewCount: m.ViewCount ?? 0,
    rating: m.Rating ?? 0,
    totalRatings: m.TotalRatings ?? 0,
    language: 'vi',
    uploaderId: String(m.UploaderID || '0'),
    isApproved: m.Status === 'Approved',
    createdAt: m.CreatedAt || new Date().toISOString(),
    updatedAt: m.UpdatedAt || new Date().toISOString(),
    categories: [],
    tags: [],
    episodes: [],
    uploaderName: m.UploaderName || '',
    uploaderRole: m.UploaderRole || '',
  });

  const mapSeries = (s: any): Series => ({
    id: String(s.SeriesID),
    title: s.Title,
    slug: s.Slug,
    description: s.Description || '',
    coverUrl: buildMediaUrl(s.CoverURL) || '',
    author: s.Author || '',
    status: s.Status === 'Ongoing' ? 'Ongoing' : 'Completed',
    isFree: s.IsFree ?? true,
    viewCount: s.ViewCount ?? 0,
    rating: s.Rating ?? 0,
    totalRatings: s.TotalRatings ?? ((s.Rating ?? 0) > 0 ? 1 : 0),
    commentCount: s.CommentCount ?? 0,
    latestChapterNumber: s.LatestChapterNumber ?? 0,
    language: s.Language || 'vi',
    uploaderId: String(s.UploaderID || '0'),
    isApproved: s.IsApproved ?? true,
    createdAt: s.CreatedAt || new Date().toISOString(),
    updatedAt: s.UpdatedAt || new Date().toISOString(),
    categories: [],
    tags: [],
    chapters: [],
    uploaderName: s.UploaderName || '',
    uploaderRole: s.UploaderRole || '',
  });

  useEffect(() => {
    const loadDisplayMode = async () => {
      try {
        if (email) {
          const prefs = await getUserPreferences(email);
          const mode = (prefs['display_mode'] as DisplayMode | undefined) || 'all';
          setDisplayMode(mode);
        } else {
          const local = window.localStorage.getItem('fimory-display-mode');
          setDisplayMode((local ? JSON.parse(local) : 'all') as DisplayMode);
        }
      } catch {
        setDisplayMode('all');
      }
    };

    loadDisplayMode();
  }, [email]);

  useEffect(() => {
    const loadContent = async () => {
      try {
        console.log('[HomePage] Loading content for homepage...');

        const moviesResponse = await fetch('/api/movies');
        if (!moviesResponse.ok) {
          throw new Error(`Movies HTTP error! status: ${moviesResponse.status}`);
        }

        const moviesPayload = await moviesResponse.json();
        const moviesList = Array.isArray(moviesPayload) ? moviesPayload : (moviesPayload?.data ?? []);
        console.log('[HomePage] Fetched movies:', moviesList.length, 'items');

        const mappedMovies: Movie[] = await Promise.all(
          moviesList
            .filter((m: any) => m.Status === 'Approved')
            .map(async (m: any) => {
              const movie = mapMovie(m);

              try {
                const categoriesResponse = await fetch(`/api/movies/${m.MovieID}/categories`);
                if (categoriesResponse.ok) {
                  const categoriesData = await categoriesResponse.json();
                  movie.categories = categoriesData.map((cat: any) => ({
                    id: String(cat.CategoryID),
                    name: cat.CategoryName,
                    slug: cat.Slug,
                  }));
                }
              } catch (error) {
                console.error(`Error loading categories for movie ${m.MovieID}:`, error);
              }

              try {
                const episodesResponse = await fetch(`/api/movies/${m.Slug}/episodes`);
                if (episodesResponse.ok) {
                  const episodesData = await episodesResponse.json();
                  movie.episodes = episodesData.map((ep: any) => ({
                    id: String(ep.EpisodeID),
                    movieId: String(m.MovieID),
                    episodeNumber: ep.EpisodeNumber,
                    title: ep.Title,
                    videoUrl: ep.VideoURL,
                    duration: ep.Duration,
                    isFree: ep.IsFree === 1 || ep.IsFree === true,
                    viewCount: ep.ViewCount || 0,
                    createdAt: ep.CreatedAt || new Date().toISOString(),
                  }));
                }
              } catch (error) {
                console.error(`Error loading episodes for movie ${m.MovieID}:`, error);
              }

              try {
                const tagsResponse = await fetch(`/api/movies/${m.MovieID}/tags`);
                if (tagsResponse.ok) {
                  const tagsData = await tagsResponse.json();
                  movie.tags = tagsData.map((tag: any) => ({
                    id: String(tag.TagID || tag.id),
                    name: tag.TagName || tag.name,
                    slug: tag.Slug || tag.slug,
                  }));
                }
              } catch {
                // ignore missing tags endpoint
              }

              return movie;
            })
        );

        const sortedByView = [...mappedMovies].sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0));
        const sortedByRating = [...mappedMovies].sort((a, b) => (b.rating || 0) - (a.rating || 0));
        const sortedByDate = [...mappedMovies].sort(
          (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        );

        setMovies(mappedMovies);
        setFeaturedMovies(sortedByView.slice(0, 5));
        setTrendingMovies(sortedByView.slice(0, 12));
        setNewMovies(sortedByDate.slice(0, 12));
        setTopRatedMovies(sortedByRating.slice(0, 12));
        setMostViewedMovies(sortedByView.slice(0, 12));

        try {
          const seriesResponse = await fetch('/api/stories');
          if (seriesResponse.ok) {
            const seriesPayload = await seriesResponse.json();
            const seriesList = Array.isArray(seriesPayload) ? seriesPayload : (seriesPayload?.data ?? []);

            const mappedSeries: Series[] = await Promise.all(
              seriesList
                .filter((s: any) => s.IsApproved !== false)
                .map(async (s: any) => {
                  const story = mapSeries(s);

                  try {
                    const categoriesResponse = await fetch(`/api/stories/${s.SeriesID}/categories`);
                    if (categoriesResponse.ok) {
                      const categoriesData = await categoriesResponse.json();
                      const rows = Array.isArray(categoriesData) ? categoriesData : (categoriesData?.data ?? []);
                      story.categories = rows.map((cat: any) => ({
                        id: String(cat.CategoryID),
                        name: cat.CategoryName,
                        slug: cat.Slug,
                      }));
                    }
                  } catch (error) {
                    console.error(`Error loading categories for series ${s.SeriesID}:`, error);
                  }

                  try {
                    const chaptersResponse = await fetch(`/api/stories/${s.SeriesID}/chapters`);
                    if (chaptersResponse.ok) {
                      const chaptersPayload = await chaptersResponse.json();
                      const chaptersRows = Array.isArray(chaptersPayload) ? chaptersPayload : (chaptersPayload?.data ?? []);
                      story.chapters = chaptersRows.map((ch: any) => ({
                        id: String(ch.ChapterID),
                        seriesId: String(s.SeriesID),
                        chapterNumber: ch.ChapterNumber,
                        title: ch.Title,
                        content: ch.Content || '',
                        isFree: ch.IsFree === 1 || ch.IsFree === true,
                        viewCount: ch.ViewCount || 0,
                        createdAt: ch.CreatedAt || new Date().toISOString(),
                      }));

                      if (story.chapters.length > 0) {
                        story.latestChapterNumber = Math.max(...story.chapters.map((ch) => ch.chapterNumber));
                      }
                    }
                  } catch (error) {
                    console.error(`Error loading chapters for series ${s.SeriesID}:`, error);
                  }

                  try {
                    const tagsResponse = await fetch(`/api/stories/${s.SeriesID}/tags`);
                    if (tagsResponse.ok) {
                      const tagsPayload = await tagsResponse.json();
                      const tagsRows = Array.isArray(tagsPayload) ? tagsPayload : (tagsPayload?.data ?? []);
                      story.tags = tagsRows.map((tag: any) => ({
                        id: String(tag.TagID || tag.id),
                        name: tag.TagName || tag.Name || tag.name,
                        slug: tag.Slug || tag.slug,
                      }));
                    }
                  } catch {
                    // ignore missing tags endpoint
                  }

                  return story;
                })
            );

            setSeries(mappedSeries);
          }
        } catch (e) {
          console.error('Error loading series:', e);
        }
      } catch (error) {
        console.error('Error loading content for homepage:', error);
        setMovies([]);
        setSeries([]);
      }
    };

    loadContent();
  }, []);

  const shouldShowMovies = displayMode !== 'series';
  const shouldShowSeries = displayMode !== 'movies';

  return (
    <div className="min-h-screen bg-gradient-to-b from-gray-900 via-gray-800 to-gray-900">
      {shouldShowMovies && <HeroSection featuredMovies={featuredMovies} />}

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-10">
        {((shouldShowMovies ? movies.length === 0 : true) && (shouldShowSeries ? series.length === 0 : true)) && (
          <div className="text-center py-16 bg-white/5 dark:bg-gray-800/50 backdrop-blur-sm rounded-2xl border border-white/10">
            <Film className="w-16 h-16 mx-auto mb-4 text-gray-400" />
            <div className="text-gray-300 text-lg mb-4 font-semibold">Chưa có nội dung nào được duyệt</div>
            <p className="text-gray-400 mb-6 max-w-md mx-auto">Các phim và truyện sẽ xuất hiện ở đây sau khi được admin duyệt.</p>
            <button
              onClick={async () => {
                try {
                  const response = await fetch('/api/movies/auto-approve', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                  });
                  const data = await response.json();
                  if (response.ok) {
                    alert(`Đã duyệt ${data.approvedCount} phim. Trang sẽ tự tải lại.`);
                    window.location.reload();
                  } else {
                    alert(`Lỗi: ${data.error}`);
                  }
                } catch (error) {
                  alert(`Lỗi: ${error}`);
                }
              }}
              className="px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium shadow-lg shadow-blue-500/50"
            >
              Duyệt tất cả phim pending
            </button>
          </div>
        )}

        {shouldShowMovies && trendingMovies.length > 0 && (
          <ContentSection
            title="Phim Hot"
            items={trendingMovies}
            type="movie"
            viewAllLink="/movies"
            icon={<TrendingUp className="w-6 h-6" />}
          />
        )}

        {shouldShowMovies && newMovies.length > 0 && (
          <ContentSection
            title="Phim Mới Nhất"
            items={newMovies}
            type="movie"
            viewAllLink="/movies?sort=newest"
            icon={<Clock className="w-6 h-6" />}
          />
        )}

        {shouldShowMovies && mostViewedMovies.length > 0 && (
          <ContentSection
            title="Xem Nhiều Nhất"
            items={mostViewedMovies}
            type="movie"
            viewAllLink="/movies?sort=views"
            icon={<Eye className="w-6 h-6" />}
          />
        )}

        {shouldShowMovies && topRatedMovies.length > 0 && (
          <ContentSection
            title="Đánh Giá Cao"
            items={topRatedMovies}
            type="movie"
            viewAllLink="/movies?sort=rating"
            icon={<Star className="w-6 h-6" />}
          />
        )}

        {shouldShowSeries && series.length > 0 && (
          <ContentSection
            title="Truyện Nổi Bật"
            items={series}
            type="series"
            viewAllLink="/stories"
            icon={<BookOpen className="w-6 h-6" />}
          />
        )}
      </div>
    </div>
  );
};
