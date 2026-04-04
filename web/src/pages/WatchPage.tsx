import React, { useEffect, useMemo, useRef, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import {
  fetchMovieDetail,
  fetchMovieEpisodes,
  saveMovieHistory,
  addFavorite,
  removeFavorite,
  fetchMovieRatings,
  submitMovieRating,
  awardMovieWatchExp,
  fetchCurrentRole,
} from "../lib/api";
import { buildMediaUrl, buildStorageUrl } from "../lib/config";
import { useAuth } from "../hooks/useAuth";
import { Button } from "../components/ui/Button";
import CommentSection from "../components/content/CommentSection";
import { Heart, Play, Clock, Eye, Share2 } from "lucide-react";

type Episode = {
  EpisodeID: number;
  EpisodeNumber: number;
  Title?: string;
  VideoURL: string;
  Duration?: number;
  ViewCount?: number;
};

export const WatchPage: React.FC = () => {
  const { slug = "" } = useParams<{ slug: string }>();
  const [searchParams] = useSearchParams();
  const { user } = useAuth();
  const [movie, setMovie] = useState<any | null>(null);
  const [episodes, setEpisodes] = useState<Episode[]>([]);
  const [current, setCurrent] = useState<Episode | null>(null);
  const [loading, setLoading] = useState(true);
  const [ratings, setRatings] = useState<{ averageRating: number; totalRatings: number } | null>(null);
  const [myRating, setMyRating] = useState<number>(0);
  const [isFavorite, setIsFavorite] = useState(false);
  const [watchedSeconds, setWatchedSeconds] = useState<number>(0);
  const [hasAwardedExp, setHasAwardedExp] = useState<boolean>(false);
  const [isAdmin, setIsAdmin] = useState<boolean>(false);
  const awardedEpisodeIdsRef = useRef<Set<number>>(new Set());

  const email = useMemo(
    () => (user?.email as string | undefined) || "",
    [user]
  );

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      setLoading(true);
      try {
        console.log(`📺 Loading movie and episodes for slug: ${slug}`);
        const [m, eps] = await Promise.all([
          fetchMovieDetail(slug),
          fetchMovieEpisodes(slug),
        ]);
        if (!cancelled) {
          console.log(`✅ Loaded movie:`, m);
          console.log(`✅ Loaded ${eps.length} episodes:`, eps);
          setMovie(m);
          setEpisodes(eps);

          const episodeParam = searchParams.get("episode");
          if (episodeParam && eps.length > 0) {
            const episodeNumber = parseInt(episodeParam, 10);
            const targetEpisode = eps.find(
              (ep: Episode) => ep.EpisodeNumber === episodeNumber
            );
            if (targetEpisode) {
              setCurrent(targetEpisode);
            } else {
              setCurrent(eps[0] ?? null);
            }
          } else {
            setCurrent(eps[0] ?? null);
          }
        }
      } catch (error) {
        console.error("❌ Error loading movie/episodes:", error);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    load();
    return () => {
      cancelled = true;
    };
  }, [slug, searchParams]);

  useEffect(() => {
    if (!loading && movie && window.location.hash === "#comments") {
      const scrollToComments = () => {
        const commentsSection = document.getElementById("comments");
        if (commentsSection) {
          const headerOffset = 80;
          const elementPosition = commentsSection.getBoundingClientRect().top;
          const offsetPosition = elementPosition + window.pageYOffset - headerOffset;

          window.scrollTo({
            top: offsetPosition,
            behavior: "smooth",
          });
          return true;
        }
        return false;
      };

      if (scrollToComments()) {
        return;
      }

      const timeouts: NodeJS.Timeout[] = [];
      const attempts = [100, 300, 500];

      attempts.forEach((delay) => {
        const timeout = setTimeout(() => {
          if (scrollToComments()) {
            timeouts.forEach((t) => clearTimeout(t));
          }
        }, delay);
        timeouts.push(timeout);
      });

      return () => {
        timeouts.forEach((t) => clearTimeout(t));
      };
    }
  }, [loading, movie]);

  useEffect(() => {
    if (!email || !movie || !current) return;
    saveMovieHistory(email, Number(movie.MovieID), current.EpisodeID).catch(
      () => {}
    );
    setWatchedSeconds(0);
    setHasAwardedExp(awardedEpisodeIdsRef.current.has(current.EpisodeID));
  }, [email, movie, current]);

  useEffect(() => {
    if (!email || !movie || !current || hasAwardedExp || isAdmin) return;
    const durationSeconds = current.Duration ? current.Duration * 60 : 0;
    if (durationSeconds <= 0) return;

    const watchThreshold = Math.floor(durationSeconds * 0.8);
    if (watchedSeconds < watchThreshold) return;

    awardMovieWatchExp(
      email,
      Number(movie.MovieID),
      watchedSeconds,
      current.EpisodeID
    )
      .then((response) => {
        if (response.ok) {
          awardedEpisodeIdsRef.current.add(current.EpisodeID);
          setHasAwardedExp(true);
          if (response.expGained > 0) {
            console.log(`✅ Nhận được ${response.expGained} EXP!`);
          }
        }
      })
      .catch((error) => {
        console.error("Error awarding movie watch EXP:", error);
      });
  }, [email, movie, current, watchedSeconds, hasAwardedExp, isAdmin]);

  useEffect(() => {
    if (!movie) return;
    const movieId = Number(movie.MovieID);
    fetchMovieRatings(movieId, email || undefined)
      .then((res) => {
        setRatings({ averageRating: res.averageRating, totalRatings: res.totalRatings });
        setMyRating(Number(res.myRating ?? 0) || 0);
      })
      .catch(() => {
        setRatings({ averageRating: 0, totalRatings: 0 });
        setMyRating(0);
      });
  }, [movie, email]);

  useEffect(() => {
    if (!movie || !email) return;
    const movieId = Number(movie.MovieID);
    fetch(`/api/movies/${movieId}/favorite-status`, {
      headers: { "x-user-email": email }
    })
      .then(res => res.json())
      .then(payload => {
        const data = payload?.data ?? payload ?? {};
        setIsFavorite(Boolean(data.isFavorite));
      })
      .catch(() => setIsFavorite(false));
  }, [movie, email]);

  useEffect(() => {
    if (!email) return;
    fetchCurrentRole(email)
      .then(({ role }) => setIsAdmin(role === "Admin"))
      .catch(() => setIsAdmin(false));
  }, [email]);

  const handleToggleFavorite = async () => {
    if (!email || !movie) return;
    try {
      const movieId = Number(movie.MovieID);
      if (isFavorite) {
        await removeFavorite(email, movieId);
      } else {
        await addFavorite(email, movieId);
      }

      const response = await fetch(`/api/movies/${movieId}/favorite-status`, {
        headers: { "x-user-email": email }
      });
      if (response.ok) {
        const data = await response.json();
        setIsFavorite(data.isFavorite || false);
      }
    } catch (error) {
      console.error("Error toggling favorite:", error);
    }
  };

  const shareMovie = async (platform: "facebook" | "zalo") => {
    if (!movie) return;
    const shareUrl = window.location.href;
    const shareTitle = `${movie.Title} - Fimory`;
    if (navigator.share) {
      try {
        await navigator.share({
          title: shareTitle,
          text: `Xem ${movie.Title} trên Fimory`,
          url: shareUrl,
        });
        return;
      } catch {
      }
    }

    const encodedUrl = encodeURIComponent(shareUrl);
    const encodedText = encodeURIComponent(`Xem ${movie.Title} trên Fimory`);
    const target = platform === "facebook"
      ? `https://www.facebook.com/sharer/sharer.php?u=${encodedUrl}&quote=${encodedText}`
      : `https://zalo.me/share?url=${encodedUrl}`;
    window.open(target, "_blank", "noopener,noreferrer");
  };

  const handleRate = async (value: number) => {
    if (!email || !movie) return;
    setMyRating(value);
    await submitMovieRating(email, Number(movie.MovieID), value).catch(() => {});
    const r = await fetchMovieRatings(Number(movie.MovieID), email).catch(() => ({ averageRating: 0, totalRatings: 0, myRating: value } as any));
    if (r && typeof (r as any).averageRating === "number") {
      setRatings({ averageRating: (r as any).averageRating, totalRatings: (r as any).totalRatings });
      setMyRating(Number((r as any).myRating ?? value) || value);
    }
  };

  const getVideoSrc = (url: string) => buildStorageUrl(url);

  if (loading)
    return (
      <div className="max-w-6xl mx-auto px-4 py-10 text-gray-600 dark:text-gray-300">
        Đang tải...
      </div>
    );

  if (!movie)
    return (
      <div className="max-w-6xl mx-auto px-4 py-10 text-gray-600 dark:text-gray-300">
        Không tìm thấy phim.
      </div>
    );

  const formatEpisodeTitle = (ep: Episode) => {
    const defaultTitle = `Tập ${ep.EpisodeNumber}`;
    if (!ep.Title) return defaultTitle;

    if (ep.Title.trim() === defaultTitle || ep.Title.trim() === `Tập ${ep.EpisodeNumber}`) {
      return defaultTitle;
    }

    return `${defaultTitle}: ${ep.Title}`;
  };

  const formatDuration = (minutes?: number) => {
    if (!minutes) return "";
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    if (hours > 0) {
      return `${hours}h ${mins}m`;
    }
    return `${mins}m`;
  };

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-6">
            <div className="aspect-video bg-black rounded-xl overflow-hidden shadow-2xl">
              {current ? (
                <video
                  key={current.EpisodeID}
                  src={getVideoSrc(current.VideoURL)}
                  controls
                  className="w-full h-full"
                  preload="metadata"
                  poster={buildMediaUrl(movie.PosterURL) || "/default-poster.jpg"}
                  onTimeUpdate={(e) => {
                    const video = e.currentTarget;
                    if (video && !isNaN(video.currentTime) && !isNaN(video.duration) && video.duration > 0) {
                      const seconds = Math.floor(video.currentTime);
                      setWatchedSeconds(seconds);
                    }
                  }}
                >
                  Trình duyệt của bạn không hỗ trợ video.
                </video>
              ) : (
                <div className="w-full h-full flex items-center justify-center text-gray-400 bg-gray-900">
                  <div className="text-center">
                    <Play className="w-16 h-16 mx-auto mb-4 opacity-50" />
                    <p className="text-lg">Chưa có video</p>
                  </div>
                </div>
              )}
            </div>

            <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-6">
              <div className="flex items-start justify-between mb-4">
                <div className="flex-1">
                  <h1 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
                    {movie.Title}
                  </h1>
                  {current && (
                    <div className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400 flex-wrap">
                      <span className="font-medium">{formatEpisodeTitle(current)}</span>
                      {current.Duration && (
                        <>
                          <span>•</span>
                          <div className="flex items-center gap-1">
                            <Clock className="w-4 h-4" />
                            <span>{formatDuration(current.Duration)}</span>
                          </div>
                        </>
                      )}
                      {current.ViewCount !== undefined && current.ViewCount !== null && (
                        <>
                          <span>•</span>
                          <div className="flex items-center gap-1">
                            <Eye className="w-4 h-4" />
                            <span>{current.ViewCount.toLocaleString()} lượt xem</span>
                          </div>
                        </>
                      )}
                    </div>
                  )}
                </div>
                {email && (
                  <Button
                    size="sm"
                    variant={isFavorite ? "primary" : "secondary"}
                    onClick={handleToggleFavorite}
                    className="ml-4"
                  >
                    <Heart className={`w-4 h-4 mr-2 ${isFavorite ? "fill-current" : ""}`} />
                    {isFavorite ? "Đã yêu thích" : "Yêu thích"}
                  </Button>
                )}
              </div>

              {movie.Description && (
                <p className="text-gray-600 dark:text-gray-300 text-sm mb-4 leading-relaxed">
                  {movie.Description}
                </p>
              )}

              <div className="flex items-center gap-4 pt-4 border-t border-gray-200 dark:border-gray-700 flex-wrap">
                <div className="flex items-center gap-2">
                  <span className="text-2xl">⭐</span>
                  <div>
                    <div className="font-semibold text-gray-900 dark:text-white">
                      {ratings?.averageRating?.toFixed ? ratings.averageRating.toFixed(1) : (ratings?.averageRating || 0)}
                    </div>
                    <div className="text-xs text-gray-500 dark:text-gray-400">
                      {ratings?.totalRatings || 0} đánh giá
                    </div>
                  </div>
                </div>
                {movie.ViewCount !== undefined && movie.ViewCount !== null && (
                  <div className="flex items-center gap-2">
                    <Eye className="w-5 h-5 text-gray-600 dark:text-gray-400" />
                    <div>
                      <div className="font-semibold text-gray-900 dark:text-white">
                        {movie.ViewCount.toLocaleString()}
                      </div>
                      <div className="text-xs text-gray-500 dark:text-gray-400">
                        lượt xem
                      </div>
                    </div>
                  </div>
                )}
                {email && (
                  <div className="flex items-center gap-1">
                    <span className="text-sm text-gray-600 dark:text-gray-400 mr-2">Đánh giá:</span>
                    {[1, 2, 3, 4, 5].map(n => (
                      <button
                        key={n}
                        onClick={() => handleRate(n)}
                        className={`text-2xl transition-transform hover:scale-110 ${
                          n <= myRating ? "text-yellow-400" : "text-gray-300 dark:text-gray-600"
                        }`}
                        aria-label={`rate-${n}`}
                      >
                        ★
                      </button>
                    ))}
                  </div>
                )}
                <div className="flex items-center gap-2 ml-auto">
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => shareMovie("facebook")}
                  >
                    <Share2 className="w-4 h-4 mr-2" />
                    Share Facebook
                  </Button>
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => shareMovie("zalo")}
                  >
                    <Share2 className="w-4 h-4 mr-2" />
                    Share Zalo
                  </Button>
                </div>
              </div>
            </div>

            <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-6">
              <CommentSection
                contentType="movie"
                contentId={Number(movie.MovieID)}
                contentTitle={movie.Title}
              />
            </div>
          </div>

          <div className="lg:col-span-1">
            <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
              <div className="px-4 py-3 bg-gray-50 dark:bg-gray-900 border-b border-gray-200 dark:border-gray-700">
                <h2 className="font-bold text-lg text-gray-900 dark:text-white flex items-center gap-2">
                  <Play className="w-5 h-5" />
                  Danh sách tập
                </h2>
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                  {episodes.length} tập
                </p>
              </div>
              <div className="max-h-[calc(100vh-200px)] overflow-y-auto p-3">
                {episodes.length === 0 ? (
                  <div className="text-center py-8 text-gray-500 dark:text-gray-400 text-sm">
                    Chưa có tập phim.
                  </div>
                ) : (
                  <div className="space-y-2">
                    {episodes.map((ep) => {
                      const isActive = current?.EpisodeID === ep.EpisodeID;
                      return (
                        <button
                          key={ep.EpisodeID}
                          onClick={() => setCurrent(ep)}
                          className={`w-full text-left p-3 rounded-lg border transition-all ${
                            isActive
                              ? "border-blue-500 bg-blue-50 dark:bg-blue-950/30 shadow-md"
                              : "border-gray-200 dark:border-gray-700 hover:border-blue-300 dark:hover:border-blue-700 hover:bg-gray-50 dark:hover:bg-gray-700/50"
                          }`}
                        >
                          <div className="flex items-start justify-between gap-2">
                            <div className="flex-1 min-w-0">
                              <div className={`font-semibold text-sm ${
                                isActive
                                  ? "text-blue-700 dark:text-blue-300"
                                  : "text-gray-900 dark:text-white"
                              }`}>
                                {formatEpisodeTitle(ep)}
                              </div>
                              <div className="flex items-center gap-2 mt-1 text-xs text-gray-500 dark:text-gray-400 flex-wrap">
                                {ep.Duration && (
                                  <div className="flex items-center gap-1">
                                    <Clock className="w-3 h-3" />
                                    <span>{formatDuration(ep.Duration)}</span>
                                  </div>
                                )}
                                {ep.ViewCount !== undefined && ep.ViewCount !== null && (
                                  <>
                                    {ep.Duration && <span>•</span>}
                                    <div className="flex items-center gap-1">
                                      <Eye className="w-3 h-3" />
                                      <span>{ep.ViewCount.toLocaleString()} lượt xem</span>
                                    </div>
                                  </>
                                )}
                              </div>
                            </div>
                            {isActive && (
                              <div className="flex-shrink-0">
                                <div className="w-2 h-2 bg-blue-500 rounded-full"></div>
                              </div>
                            )}
                          </div>
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default WatchPage;
