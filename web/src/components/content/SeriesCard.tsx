import React, { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { BookOpen, Star, Eye, Heart, MessageCircle } from 'lucide-react';
import { Card } from '../ui/Card';
import { Series } from '../../types';
import { useAuth } from '../../hooks/useAuth';

interface SeriesCardProps {
  series: Series;
  showStats?: boolean;
}

export const SeriesCard: React.FC<SeriesCardProps> = ({ series, showStats = true }) => {
  const { user } = useAuth();
  const email = useMemo(() => (user?.email as string | undefined) || '', [user]);
  const [isFavorite, setIsFavorite] = useState(false);
  const [loading, setLoading] = useState(false);

  const latestChapterNumber = useMemo(() => {
    if (series.latestChapterNumber && series.latestChapterNumber > 0) {
      return series.latestChapterNumber;
    }
    if (series.chapters && series.chapters.length > 0) {
      return Math.max(...series.chapters.map((c) => Number(c.chapterNumber || 0)));
    }
    return 0;
  }, [series.latestChapterNumber, series.chapters]);

  const totalRatings = useMemo(() => {
    const base = Number(series.totalRatings || 0);
    if (base > 0) return base;
    return Number(series.rating || 0) > 0 ? 1 : 0;
  }, [series.totalRatings, series.rating]);

  useEffect(() => {
    if (!email || !series.id) return;

    const checkFavorite = async () => {
      try {
        const seriesId = Number(series.id);
        const response = await fetch(`/api/stories/${seriesId}/favorite-status`, {
          headers: { 'x-user-email': email },
        });
        if (response.ok) {
          const payload = await response.json();
          const data = payload?.data ?? payload ?? {};
          setIsFavorite(Boolean(data.isFavorite));
        }
      } catch (error) {
        console.error('Error checking favorite status:', error);
      }
    };

    checkFavorite();
  }, [email, series.id]);

  const handleFavorite = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();

    if (!email) {
      window.location.href = '/login';
      return;
    }

    if (loading) return;

    setLoading(true);
    const previousState = isFavorite;
    try {
      const seriesId = Number(series.id);
      if (isFavorite) {
        await fetch(`/api/stories/${seriesId}/favorite`, {
          method: 'DELETE',
          headers: { 'x-user-email': email },
        });
      } else {
        await fetch(`/api/stories/${seriesId}/favorite`, {
          method: 'POST',
          headers: { 'x-user-email': email },
        });
      }

      const response = await fetch(`/api/stories/${seriesId}/favorite-status`, {
        headers: { 'x-user-email': email },
      });
      if (response.ok) {
        const payload = await response.json();
        const data = payload?.data ?? payload ?? {};
        setIsFavorite(Boolean(data.isFavorite));
      } else {
        setIsFavorite(previousState);
      }
    } catch (error) {
      console.error('Error toggling favorite:', error);
      setIsFavorite(previousState);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card hover className="group overflow-hidden">
      <Link to={`/stories/${series.slug}`}>
        <div className="relative aspect-[2/3] overflow-hidden">
          <img
            src={series.coverUrl || 'https://images.pexels.com/photos/1261728/pexels-photo-1261728.jpeg'}
            alt={series.title}
            className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-110"
          />

          <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300">
            <div className="absolute bottom-0 left-0 right-0 p-4">
              <div className="flex items-center justify-between mb-2">
                <motion.div whileHover={{ scale: 1.1 }} whileTap={{ scale: 0.95 }} className="bg-green-600 text-white p-3 rounded-full">
                  <BookOpen className="w-5 h-5" />
                </motion.div>

                <motion.button
                  whileHover={{ scale: 1.1 }}
                  whileTap={{ scale: 0.95 }}
                  onClick={handleFavorite}
                  disabled={loading}
                  className={`p-2 rounded-full transition-colors ${
                    isFavorite ? 'bg-red-500/90 hover:bg-red-600 text-white' : 'bg-black/50 hover:bg-black/70 text-white'
                  }`}
                >
                  <Heart className={`w-4 h-4 ${isFavorite ? 'fill-current' : ''}`} />
                </motion.button>
              </div>

              {showStats && (
                <div className="space-y-2">
                  <div className="flex items-center space-x-3 text-white text-xs">
                    <div className="flex items-center space-x-1">
                      <Star className="w-3 h-3 fill-yellow-400 text-yellow-400" />
                      <span>{Number(series.rating || 0).toFixed(1)}</span>
                    </div>
                    <div className="flex items-center space-x-1">
                      <Eye className="w-3 h-3" />
                      <span>{Number(series.viewCount || 0).toLocaleString()}</span>
                    </div>
                    <div className="flex items-center space-x-1">
                      <MessageCircle className="w-3 h-3" />
                      <span>{Number(series.commentCount || 0).toLocaleString()}</span>
                    </div>
                  </div>
                  {series.tags && series.tags.length > 0 && (
                    <div className="flex flex-wrap gap-1">
                      {series.tags.slice(0, 3).map((tag) => (
                        <span key={tag.id} className="text-xs bg-purple-500/80 text-white px-2 py-0.5 rounded-full font-medium">
                          {tag.name}
                        </span>
                      ))}
                      {series.tags.length > 3 && <span className="text-xs text-white/80">+{series.tags.length - 3}</span>}
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>

          <div className="absolute top-2 left-2 flex items-center gap-1">
            {!series.isFree && (
              <span className="bg-gradient-to-r from-yellow-400 to-orange-500 text-white text-xs font-medium px-2 py-1 rounded-full">VIP</span>
            )}
            {series.status === 'Ongoing' && (
              <span className="bg-green-500 text-white text-xs font-medium px-2 py-1 rounded-full">Ongoing</span>
            )}
          </div>
        </div>
      </Link>

      <div className="p-3">
        <Link to={`/stories/${series.slug}`}>
          <h3 className="font-semibold text-sm text-gray-900 dark:text-gray-100 line-clamp-2 mb-1.5 group-hover:text-green-600 dark:group-hover:text-green-400 transition-colors min-h-[2.5rem]">
            {series.title}
          </h3>
        </Link>

        <div className="flex items-center justify-between text-xs">
          <span className="text-green-600 dark:text-green-400 font-semibold">{totalRatings} đánh giá</span>
          {series.chapters && series.chapters.length > 0 && (
            <span className="text-blue-600 dark:text-blue-400 font-semibold">{series.chapters.length} chương</span>
          )}
        </div>

        {latestChapterNumber > 0 && (
          <Link
            to={`/stories/${series.slug}?chapter=${latestChapterNumber}`}
            className="mt-2 inline-flex items-center text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline"
          >
            Chương mới nhất: {latestChapterNumber}
          </Link>
        )}
      </div>
    </Card>
  );
};
